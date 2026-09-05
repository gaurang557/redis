import java.util.Map;
import java.util.concurrent.*;

public class MiniRedis {

    private final Map<String, String> store =
            new ConcurrentHashMap<>();

    private final Map<String, Long> expiry =
            new ConcurrentHashMap<>();

    private final ScheduledExecutorService expiryCleaner =
            Executors.newSingleThreadScheduledExecutor();

    public MiniRedis() {

        expiryCleaner.scheduleAtFixedRate(
                this::removeExpiredKeys,
                1,
                1,
                TimeUnit.SECONDS
        );
    }

    public void set(String key, String value) {
        store.put(key, value);
        expiry.remove(key);
    }

    public String get(String key) {

        if (isExpired(key)) {
            delete(key);
            return null;
        }

        return store.get(key);
    }

    public boolean delete(String key) {

        expiry.remove(key);

        return store.remove(key) != null;
    }

    public boolean expire(String key, long seconds) {

        if (!store.containsKey(key)) {
            return false;
        }

        expiry.put(
                key,
                System.currentTimeMillis() + seconds * 1000
        );

        return true;
    }

    public long ttl(String key) {

        if (!store.containsKey(key)) {
            return -2;
        }

        Long expiryTime = expiry.get(key);

        if (expiryTime == null) {
            return -1;
        }

        long remaining =
                expiryTime - System.currentTimeMillis();

        if (remaining <= 0) {
            delete(key);
            return -2;
        }

        return remaining / 1000;
    }

    private boolean isExpired(String key) {

        Long expiryTime = expiry.get(key);

        return expiryTime != null &&
                System.currentTimeMillis() >= expiryTime;
    }

    private void removeExpiredKeys() {

        long now = System.currentTimeMillis();

        expiry.forEach((key, expiryTime) -> {

            if (now >= expiryTime) {

                store.remove(key);

                expiry.remove(key);
            }
        });
    }
}