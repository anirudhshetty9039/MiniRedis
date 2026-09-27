# MiniRedis
# MiniRedis

MiniRedis is a deliberately small, educational Redis-like server for backend interview practice. It uses Java 17, TCP sockets, RESP2, a worker pool, an in-memory string store, TTLs, optional LRU eviction, AOF recovery, and lightweight metrics. It is not intended to be a Redis replacement.

## Architecture and request flow

```text
client socket -> MiniRedisServer -> RespParser
							  -> CommandDispatcher -> KeyValueStore
												   -> AofPersistence
							  <- RespEncoder <- response
```

The server accepts sockets and assigns each connection to a worker. The parser turns one RESP array into command arguments. The dispatcher validates and executes the command, the store owns data/TTL/LRU behavior, and the encoder writes one RESP response. AOF records successful mutations as RESP-framed commands.

See [architecture](docs/architecture.md), [protocol](docs/protocol.md), [storage](docs/storage.md), [persistence](docs/persistence.md), and [concurrency](docs/concurrency.md).

## Supported commands

`PING [message]`, `SET key value [EX seconds]`, `GET key`, `DEL key [key ...]`, `EXISTS key [key ...]`, `EXPIRE key seconds`, `TTL key`, `KEYS *`, and `INFO`.

`GET` returns a null bulk string for a missing or expired key. `TTL` returns `-1` for no expiry and `-2` for a missing key. `KEYS *` is O(N), so it is intended for demonstrations and small data sets. `INFO` returns metrics as a bulk string.

## TTL and persistence

Entries store an absolute expiration timestamp in milliseconds. Reads remove expired entries lazily; a server maintenance thread also scans once per second. AOF appends successful mutations using RESP arrays and replays complete records on startup. A partial trailing record is ignored. As in the original design, `SET ... EX` is replayed with its logged duration, so its TTL can restart after a process restart.

## LRU eviction

`--max-keys=N` enables a maximum number of live keys. `0` means unlimited. The store uses an access-ordered map: reads and other key lookups move a key to the most-recent position, and an insertion over the limit removes the eldest entry. The map update and eviction are synchronized, making the map/list operation atomic and giving O(1) average lookup, update, recency update, and eviction.

## Concurrency model and metrics

The acceptor thread accepts clients and a fixed worker pool handles command loops. The store serializes its compound map/LRU operations; AOF writes use their own lock. `Metrics` uses `AtomicLong` counters for commands, GET hits/misses, SETs, evictions, clients, and uptime. `INFO` obtains the current key count from the store.

## Configuration

Options use `--name=value` syntax:

`--port` (default `6379`), `--data-dir` (default `data`), `--aof` (default `true`), `--workers` (CPU-sized default), and `--max-keys` (default `0`, unlimited).

## Build, run, and CLI

Use Java 17+ and Maven:

```powershell
mvn clean test
mvn package
java -jar target/miniredis-1.0.0.jar --port=6379 --data-dir=data --aof=true --workers=4 --max-keys=10000
java -cp target/miniredis-1.0.0.jar com.miniredis.client.MiniRedisCli localhost 6379
```

The CLI sends whitespace-separated commands interactively. For example: `SET name Ada`, `GET name`, `SET session abc EX 5`, `TTL session`, and `INFO`.

## Benchmark

Start MiniRedis first, preferably with `--aof=false`, then run the standalone utility:

```powershell
java -cp target/miniredis-1.0.0.jar com.miniredis.benchmark.MiniRedisBenchmark localhost 6379 4 10000 MIXED
```

Arguments are host, port, clients, operations per client, and workload (`SET`, `GET`, or `MIXED`). It reports total operations, elapsed seconds, operations per second, client count, and workload. It uses one socket per benchmark client and does not change production server code.

## Testing

Run the complete suite with `mvn test`. Tests cover RESP, commands, AOF recovery, TCP integration, TTL, CRUD, LRU recency and limits, unlimited mode, concurrent writes, and metrics.

## Technical Decisions

- **TCP:** provides a small, realistic request/response transport without a framework.
- **RESP:** is simple to parse incrementally and gives typed responses for the CLI and AOF.
- **ConcurrentHashMap heritage:** the original store used it for safe concurrent key access. LRU needs an atomic map-plus-order update, so the current compact implementation uses a synchronized access-ordered map instead of pretending a concurrent map alone can make the two structures consistent.
- **Worker thread pool:** bounds concurrent command execution and keeps the acceptor responsive.
- **AOF:** is easy to explain and recover because commands are already represented as RESP arrays.
- **TTL cleanup:** lazy reads keep normal operations cheap; periodic cleanup bounds stale entries when keys are not read.
- **LRU:** an access-ordered map gives predictable O(1)-average eviction without a large cache subsystem.
- **Thread-safe metrics:** atomic counters avoid lost updates across command workers.

## Limitations and future improvements

The server stores strings only and has no authentication, transactions, replication, snapshots, pipelining controls, memory-size limit, eviction policies beyond LRU, or AOF compaction. Future interview-sized extensions could include AOF rewriting, bounded command input, more precise TTL persistence, a read/write benchmark comparison, or additional focused commands. Replication, clustering, Pub/Sub, Streams, scripting, and transactions are intentionally out of scope.
