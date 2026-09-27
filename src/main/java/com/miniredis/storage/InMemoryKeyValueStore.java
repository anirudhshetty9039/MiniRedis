package com.miniredis.storage;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.LinkedHashMap;
import java.util.Map;

/** Concurrent map with lazy expiry; cleanupExpired is invoked periodically by the server. */
public final class InMemoryKeyValueStore implements KeyValueStore {
    private record Entry(String value, Long expiresAt) {
        boolean expired(long now) {
            return expiresAt != null && expiresAt <= now;
        }
    }

    private final Map<String, Entry> values = new LinkedHashMap<>(16, 0.75f, true);
    private final int maxKeys;
    private final Runnable evictionListener;
    private long evictions;

    public InMemoryKeyValueStore() {
        this(0);
    }

    public InMemoryKeyValueStore(int maxKeys) {
        this(maxKeys, () -> { });
    }

    public InMemoryKeyValueStore(int maxKeys, Runnable evictionListener) {
        if (maxKeys < 0)
            throw new IllegalArgumentException("maxKeys must not be negative");
        this.maxKeys = maxKeys;
        this.evictionListener = evictionListener;
    }

    public synchronized void set(String key, String value, Long expirationMillis) {
        values.put(key, new Entry(value, expirationMillis));
        evictIfNeeded();
    }

    public synchronized Optional<String> get(String key) {
        Entry entry = live(key);
        return entry == null ? Optional.empty() : Optional.of(entry.value());
    }

    public synchronized boolean delete(String key) {
        return values.remove(key) != null;
    }

    public synchronized boolean exists(String key) {
        return live(key) != null;
    }

    public synchronized boolean expire(String key, long seconds) {
        long at = seconds <= 0 ? System.currentTimeMillis()
                : System.currentTimeMillis() + Math.multiplyExact(seconds, 1000);
        Entry old = live(key);
        if (old == null)
            return false;
        values.put(key, new Entry(old.value(), at));
        return true;
    }

    public synchronized long ttl(String key) {
        Entry entry = live(key);
        if (entry == null) {
            return -2;
        }
        if (entry.expiresAt() == null) {
            return -1;
        }
        long left = entry.expiresAt() - System.currentTimeMillis();
        return left <= 0 ? -2 : Math.max(1, (left + 999) / 1000);
    }

    public synchronized Set<String> keys() {
        cleanupExpired();
        return new TreeSet<>(values.keySet());
    }

    public synchronized void cleanupExpired() {
        long now = System.currentTimeMillis();
        values.entrySet().removeIf(e -> e.getValue().expired(now));
    }

    public synchronized long evictionCount() {
        return evictions;
    }

    private Entry live(String key) {
        Entry entry = values.get(key);
        if (entry != null && entry.expired(System.currentTimeMillis())) {
            values.remove(key);
            return null;
        }
        return entry;
    }

    private void evictIfNeeded() {
        while (maxKeys > 0 && values.size() > maxKeys) {
            String eldest = values.keySet().iterator().next();
            values.remove(eldest);
            evictions++;
            evictionListener.run();
        }
    }
}
