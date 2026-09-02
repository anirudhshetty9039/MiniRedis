# MiniRedis

MiniRedis is a small educational Redis-like server written with Java sockets and the standard library. It speaks a focused RESP2 subset, supports concurrent clients, lazy/periodic expiration, and an append-only command log.

## Build and run

Use Java 17+ and Maven:

```powershell
& 'C:\Program Files\Apache\maven\apache-maven-3.9.16-bin\apache-maven-3.9.16\bin\mvn.cmd' clean test
& 'C:\Program Files\Apache\maven\apache-maven-3.9.16-bin\apache-maven-3.9.16\bin\mvn.cmd' package
java -jar target/miniredis-1.0.0.jar --port=6379 --data-dir=data --aof=true --workers=4
java -cp target/miniredis-1.0.0.jar com.miniredis.client.MiniRedisCli
```

The server defaults to port `6379`, `data/`, enabled AOF, and a CPU-sized worker pool. Options are `--port`, `--data-dir`, `--aof`, and `--workers`.

## Commands

`PING [message]`, `SET key value [EX seconds]`, `GET key`, `DEL key [key ...]`, `EXISTS key [key ...]`, `EXPIRE key seconds`, `TTL key`, and `KEYS *`.

Example: `PING`, `SET name Anirudh`, `GET name`, `SET session abc EX 5`, `TTL session`.

`KEYS *` is O(N) and is intentionally only suitable for demonstration/small data sets.

## Architecture

`TCP socket -> RESP parser -> command dispatcher -> concurrent store -> AOF`.

The network layer owns connections only; commands own validation; the storage interface is independently testable. See [architecture](docs/architecture.md), [protocol](docs/protocol.md), [storage](docs/storage.md), [persistence](docs/persistence.md), and [concurrency](docs/concurrency.md).

## Limitations

This is not Redis: it stores strings only, has no authentication, transactions, replication, snapshots, pipelining controls, memory limits, or AOF compaction. AOF `EXPIRE`/`SET EX` records use relative seconds, so an unexpired TTL is restarted from its logged value after a process restart.
