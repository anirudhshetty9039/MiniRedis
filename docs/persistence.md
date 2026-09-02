# Persistence

When enabled, each successful mutating operation is appended as one RESP command array to `data/miniredis.aof` (or `--data-dir`). Writes are serialized and flushed while holding a narrow AOF lock. Startup replays complete valid command arrays; an incomplete or malformed trailing record is ignored, making a partial final write recoverable. There is no fsync policy, checksum, compaction, or snapshotting.
