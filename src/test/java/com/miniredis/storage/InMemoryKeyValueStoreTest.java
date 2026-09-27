package com.miniredis.storage;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class InMemoryKeyValueStoreTest {
    @Test
    void supportsCrudTtlAndKeys() throws Exception {
        KeyValueStore s = new InMemoryKeyValueStore();
        s.set("a", "one", null);
        assertEquals("one", s.get("a").orElseThrow());
        s.set("a", "two", null);
        assertTrue(s.exists("a"));
        assertEquals(-1, s.ttl("a"));
        assertTrue(s.expire("a", 1));
        assertTrue(s.ttl("a") > 0);
        assertTrue(s.keys().contains("a"));
        Thread.sleep(1100);
        assertFalse(s.exists("a"));
        assertEquals(-2, s.ttl("a"));
        assertFalse(s.delete("a"));
    }

    @Test
    void handlesConcurrentWrites() throws Exception {
        KeyValueStore s = new InMemoryKeyValueStore();
        ExecutorService e = Executors.newFixedThreadPool(8);
        for (int i = 0; i < 200; i++) {
            int n = i;
            e.submit(() -> s.set("k" + n, "v" + n, null));
        }
        e.shutdown();
        assertTrue(e.awaitTermination(5, TimeUnit.SECONDS));
        assertEquals(200, s.keys().size());
    }

    @Test
    void evictsLeastRecentlyUsedKey() {
        KeyValueStore s = new InMemoryKeyValueStore(3);
        s.set("A", "1", null);
        s.set("B", "2", null);
        s.set("C", "3", null);
        s.get("A");
        s.set("D", "4", null);
        assertTrue(s.exists("A"));
        assertFalse(s.exists("B"));
        assertTrue(s.exists("C"));
        assertTrue(s.exists("D"));
    }

    @Test
    void concurrentWritesRespectLimit() throws Exception {
        KeyValueStore s = new InMemoryKeyValueStore(10);
        ExecutorService e = Executors.newFixedThreadPool(8);
        for (int i = 0; i < 200; i++) {
            int n = i;
            e.submit(() -> s.set("k" + n, "v", null));
        }
        e.shutdown();
        assertTrue(e.awaitTermination(5, TimeUnit.SECONDS));
        assertEquals(10, s.keys().size());
    }

    @Test
    void zeroMeansUnlimited() {
        KeyValueStore s = new InMemoryKeyValueStore(0);
        for (int i = 0; i < 20; i++)
            s.set("k" + i, "v", null);
        assertEquals(20, s.keys().size());
    }
}
