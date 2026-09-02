# Concurrency

Client connections run in a bounded fixed worker pool. The store is a concurrent map rather than a server-wide lock. Expiry cleanup is a single scheduled task. AOF serialization is intentionally scoped only to writing one log record, preventing interleaved RESP records while leaving reads and ordinary store operations concurrent.
