# Architecture

The acceptor owns one `ServerSocket` and submits client sockets to a fixed worker pool. Each worker repeatedly parses one RESP value, dispatches it, and encodes one RESP response. `MiniRedisServer` coordinates lifecycle, AOF replay, and one periodic expiry sweep; it contains no key/value operations.

```
client -> socket worker -> RESP parser -> CommandDispatcher -> KeyValueStore
                                                |                 |
                                                +---- AOF --------+
```
