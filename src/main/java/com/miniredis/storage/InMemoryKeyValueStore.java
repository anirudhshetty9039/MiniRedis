package com.miniredis.storage;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/** Concurrent map with lazy expiry; cleanupExpired is invoked periodically by the server. */
public final class InMemoryKeyValueStore implements KeyValueStore {
    private record Entry(String value, Long expiresAt) {
        boolean expired(long now) {
            return expiresAt != null && expiresAt <= now;
        }
    }

    private final ConcurrentHashMap<String, Entry> values = new ConcurrentHashMap<>();

    public void set(String key, String value, Long expirationMillis) {
        values.put(key, new Entry(value, expirationMillis));
    }

    public Optional<String> get(String key) {
        Entry entry = live(key);
        return entry == null ? Optional.empty() : Optional.of(entry.value());
    }

    public boolean delete(String key) {
        return values.remove(key) != null;
    }

    public boolean exists(String key) {
        return live(key) != null;
    }

    public boolean expire(String key, long seconds) {
        long at = seconds <= 0 ? System.currentTimeMillis()
                : System.currentTimeMillis() + Math.multiplyExact(seconds, 1000);
        final boolean[] changed = {false};
        values.computeIfPresent(key, (k, old) -> {
            if (old.expired(System.currentTimeMillis())) {
                return null;
            }
            changed[0] = true;
            return new Entry(old.value(), at);
        });
        return changed[0];
    }

    public long ttl(String key) {
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

    public Set<String> keys() {
        cleanupExpired();
        return new TreeSet<>(values.keySet());
    }

    public void cleanupExpired() {
        long now = System.currentTimeMillis();
        values.entrySet().removeIf(e -> e.getValue().expired(now));
    }

    private Entry live(String key) {
        Entry entry = values.get(key);
        if (entry != null && entry.expired(System.currentTimeMillis())) {
            values.remove(key, entry);
            return null;
        }
        return entry;
    }
}
