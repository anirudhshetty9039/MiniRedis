package com.miniredis.storage;
import java.util.Optional;
import java.util.Set;

public interface KeyValueStore {
    void set(String key, String value, Long expirationMillis);

    Optional<String> get(String key);

    boolean delete(String key);

    boolean exists(String key);

    boolean expire(String key, long seconds);

    long ttl(String key);

    Set<String> keys();

    void cleanupExpired();
}
