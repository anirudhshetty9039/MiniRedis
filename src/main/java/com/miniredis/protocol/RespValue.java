package com.miniredis.protocol;

import java.util.List;

/** Values in the small RESP2 subset used by MiniRedis. */
public sealed interface RespValue permits RespValue.Simple, RespValue.Error, RespValue.Number, RespValue.Bulk, RespValue.Array 
{
    record Simple(String value) implements RespValue {
    }

    record Error(String message) implements RespValue {
    }

    record Number(long value) implements RespValue {
    }

    record Bulk(byte[] value) implements RespValue {
        public boolean isNull() {
            return value == null;
        }
    }

    record Array(List<RespValue> values) implements RespValue {
        public boolean isNull() {
            return values == null;
        }
    }
}
