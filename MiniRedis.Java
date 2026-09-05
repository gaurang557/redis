import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MiniRedis {

    private final Map<String, String> store = new ConcurrentHashMap<>();
    private final Map<String, Long> expiry = new ConcurrentHashMap<>();

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

    public boolean exists(String key) {

        if (isExpired(key)) {
            delete(key);
            return false;
        }

        return store.containsKey(key);
    }

    public boolean expire(String key, long seconds) {

        if (!store.containsKey(key)) {
            return false;
        }

        long expiryTime =
                System.currentTimeMillis() + seconds * 1000;

        expiry.put(key, expiryTime);

        return true;
    }

    private boolean isExpired(String key) {

        Long expiryTime = expiry.get(key);

        if (expiryTime == null) {
            return false;
        }

        return System.currentTimeMillis() >= expiryTime;
    }
}