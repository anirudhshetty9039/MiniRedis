package com.miniredis.command;

import com.miniredis.protocol.*;
import com.miniredis.storage.*;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;

class CommandDispatcherTest {
    private final CommandDispatcher commands = new CommandDispatcher(new InMemoryKeyValueStore(), null);

    @Test
    void executesCommandsAndReportsErrors() {
        assertEquals(new RespValue.Simple("PONG"), run("PING"));
        assertEquals(new RespValue.Simple("OK"), run("SET", "name", "Ada"));
        assertEquals("Ada", text(run("GET", "name")));
        assertEquals(new RespValue.Number(1), run("EXISTS", "name"));
        assertEquals(new RespValue.Number(1), run("DEL", "name"));
        assertTrue(run("GET", "name") instanceof RespValue.Bulk b && b.isNull());
        assertTrue(run("NOPE") instanceof RespValue.Error);
        assertTrue(run("SET", "x") instanceof RespValue.Error);
        assertTrue(run("EXPIRE", "x", "no") instanceof RespValue.Error);
    }

    @Test
    void supportsSetExAndKeys() {
        run("SET", "temporary", "x", "EX", "2");
        assertTrue(((RespValue.Number) run("TTL", "temporary")).value() > 0);
        RespValue.Array keys = (RespValue.Array) run("KEYS", "*");
        assertEquals(1, keys.values().size());
    }

    private RespValue run(String... args) {
        return commands.execute(new RespValue.Array(Arrays.stream(args)
                .map(s -> new RespValue.Bulk(s.getBytes(StandardCharsets.UTF_8))).map(x -> (RespValue) x).toList()));
    }

    private String text(RespValue r) {
        return new String(((RespValue.Bulk) r).value(), StandardCharsets.UTF_8);
    }
}
