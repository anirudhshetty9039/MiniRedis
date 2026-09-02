# Protocol

MiniRedis implements RESP2 simple strings (`+`), errors (`-`), integers (`:`), bulk strings (`$` including `$-1`), and arrays (`*` including `*-1`). Commands are arrays of UTF-8 bulk strings. The parser is streaming and distinguishes clean end-of-stream from incomplete values. A malformed client request receives a RESP error and that connection is closed.
