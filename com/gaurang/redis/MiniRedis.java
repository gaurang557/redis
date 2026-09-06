package com.gaurang.redis;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;

/** Binary-safe storage. One lock keeps each operation and its expiry check atomic. */
public class MiniRedis {
    private record Entry(byte[] value, Long expiresAtMillis) {}

    private final Map<ByteBuffer, Entry> data = new HashMap<>();
    private final Clock clock;

    public MiniRedis() {
        this(Clock.systemUTC());
    }

    MiniRedis(Clock clock) {
        this.clock = clock;
    }

    public synchronized void set(byte[] key, byte[] value) {
        // SET replaces both the value and any previous expiration.
        data.put(key(key), new Entry(value.clone(), null));
    }

    public synchronized byte[] get(byte[] key) {
        Entry entry = liveEntry(key(key));
        return entry == null ? null : entry.value().clone();
    }

    public synchronized long delete(byte[]... keys) {
        long removed = 0;
        for (byte[] bytes : keys) {
            ByteBuffer key = key(bytes);
            if (liveEntry(key) != null) {
                data.remove(key);
                removed++;
            }
        }
        return removed;
    }

    public synchronized boolean expire(byte[] bytes, long seconds) {
        ByteBuffer key = key(bytes);
        Entry entry = liveEntry(key);
        if (entry == null) return false;
        if (seconds <= 0) {
            data.remove(key);
        } else {
            long deadline = Math.addExact(clock.millis(), Math.multiplyExact(seconds, 1000));
            data.put(key, new Entry(entry.value(), deadline));
        }
        return true;
    }

    public synchronized long increment(byte[] bytes) {
        return incrementBy(bytes, 1);
    }

    public synchronized long incrementBy(byte[] bytes, long amount) {
        ByteBuffer key = key(bytes);
        Entry entry = liveEntry(key);
        long oldValue = entry == null ? 0 : parseInteger(entry.value());
        long next = Math.addExact(oldValue, amount);
        data.put(key, new Entry(Long.toString(next).getBytes(StandardCharsets.US_ASCII),
                entry == null ? null : entry.expiresAtMillis()));
        return next;
    }

    public synchronized long ttl(byte[] bytes) {
        Entry entry = liveEntry(key(bytes));
        if (entry == null) return -2;
        if (entry.expiresAtMillis() == null) return -1;
        long remaining = Math.max(0, entry.expiresAtMillis() - clock.millis());
        return remaining / 1000 + (remaining % 1000 >= 500 ? 1 : 0);
    }

    public static long parseInteger(byte[] bytes) {
        String value = new String(bytes, StandardCharsets.US_ASCII);
        if (!value.matches("0|-?[1-9][0-9]*")) {
            throw new NumberFormatException("not an integer");
        }
        return Long.parseLong(value);
    }

    private Entry liveEntry(ByteBuffer key) {
        Entry entry = data.get(key);
        if (entry != null && entry.expiresAtMillis() != null
                && entry.expiresAtMillis() <= clock.millis()) {
            data.remove(key);
            return null;
        }
        return entry;
    }

    private static ByteBuffer key(byte[] bytes) {
        // ByteBuffer supplies content-based equality; copies prevent caller mutations.
        return ByteBuffer.wrap(bytes.clone()).asReadOnlyBuffer();
    }
}
