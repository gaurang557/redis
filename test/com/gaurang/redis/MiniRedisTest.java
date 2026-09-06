package com.gaurang.redis;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MiniRedisTest {
    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void handlesMissingEmptyBinaryAndDefensiveCopies() {
        var store = new MiniRedis();
        byte[] key = {0, -1};
        byte[] value = {13, 10, -1};
        store.set(key, value);
        key[0] = 42;
        value[0] = 42;
        byte[] actual = store.get(new byte[] {0, -1});
        assertArrayEquals(new byte[] {13, 10, -1}, actual);
        actual[0] = 42;
        assertEquals(13, store.get(new byte[] {0, -1})[0]);
        assertNull(store.get(bytes("missing")));
        store.set(bytes("empty"), new byte[0]);
        assertArrayEquals(new byte[0], store.get(bytes("empty")));
    }

    @Test
    void expirationOverwriteAndIncrementHaveConsistentSemantics() {
        var clock = new MutableClock();
        var store = new MiniRedis(clock);
        byte[] key = bytes("key");
        assertEquals(-2, store.ttl(key));
        assertFalse(store.expire(key, 10));
        store.set(key, bytes("20"));
        assertEquals(-1, store.ttl(key));
        assertTrue(store.expire(key, 2));
        assertEquals(21, store.increment(key));
        assertEquals(2, store.ttl(key));
        clock.now += 2000;
        assertNull(store.get(key));
        assertEquals(-2, store.ttl(key));
        assertEquals(1, store.increment(key));
        assertTrue(store.expire(key, 10));
        store.set(key, bytes("replacement"));
        assertEquals(-1, store.ttl(key));
        assertTrue(store.expire(key, 0));
        assertNull(store.get(key));
    }

    @Test
    void numericErrorsDoNotChangeValues() {
        var store = new MiniRedis();
        byte[] key = bytes("n");
        store.set(key, bytes("9223372036854775807"));
        assertThrows(ArithmeticException.class, () -> store.increment(key));
        assertArrayEquals(bytes("9223372036854775807"), store.get(key));
        for (String value : new String[] {"hello", "01", "+1", "-0", ""}) {
            store.set(key, bytes(value));
            assertThrows(NumberFormatException.class, () -> store.increment(key));
            assertArrayEquals(bytes(value), store.get(key));
        }
    }

    @Test
    void concurrentIncrementsDoNotLoseUpdates() throws Exception {
        var store = new MiniRedis();
        byte[] key = bytes("counter");
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new ArrayList<Future<?>>();
            for (int i = 0; i < 100; i++) {
                futures.add(executor.submit(() -> {
                    for (int j = 0; j < 1000; j++) store.increment(key);
                }));
            }
            for (var future : futures) future.get();
        }
        assertArrayEquals(bytes("100000"), store.get(key));
    }

    private static final class MutableClock extends Clock {
        long now = 100000;
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return Instant.ofEpochMilli(now); }
        public long millis() { return now; }
    }
}
