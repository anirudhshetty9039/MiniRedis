# Storage and TTL

`InMemoryKeyValueStore` uses `ConcurrentHashMap` entries containing the value and an optional absolute expiration timestamp. Reads, existence checks, TTL, and key enumeration lazily discard expired entries. A single scheduled sweep additionally reclaims idle expired keys; there is never one thread per key. Concurrent updates use map atomic operations where expiry needs to be changed.
