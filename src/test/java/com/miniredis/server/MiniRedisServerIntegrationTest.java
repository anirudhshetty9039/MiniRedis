package com.miniredis.server;

import com.miniredis.config.*;
import com.miniredis.protocol.*;
import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class MiniRedisServerIntegrationTest {
    @TempDir
    Path dir;

    @Test
    void servesCommandsOverTcpAndRestoresAof() throws Exception {
        ServerConfig c = new ServerConfig(0, dir, true, 4);
        try (MiniRedisServer server = new MiniRedisServer(c)) {
            server.start();
            try (Socket s = new Socket("localhost", server.port())) {
                assertEquals("PONG", simple(send(s, "PING")));
                assertEquals("OK", simple(send(s, "SET", "name", "Ada")));
                assertEquals("Ada", bulk(send(s, "GET", "name")));
                assertEquals(1, number(send(s, "EXISTS", "name")));
                assertEquals("OK", simple(send(s, "SET", "temp", "x", "EX", "1")));
                assertTrue(number(send(s, "TTL", "temp")) > 0);
                assertEquals(2, ((RespValue.Array) send(s, "KEYS", "*")).values().size());
                Thread.sleep(1100);
                assertTrue(((RespValue.Bulk) send(s, "GET", "temp")).isNull());
            }
        }
        try (MiniRedisServer restarted = new MiniRedisServer(c)) {
            restarted.start();
            try (Socket s = new Socket("localhost", restarted.port())) {
                assertEquals("Ada", bulk(send(s, "GET", "name")));
                assertEquals(1, number(send(s, "DEL", "name")));
                assertTrue(((RespValue.Bulk) send(s, "GET", "name")).isNull());
            }
        }
    }

    private RespValue send(Socket s, String... args) throws Exception {
        List<RespValue> a = Arrays.stream(args).map(x -> new RespValue.Bulk(x.getBytes(StandardCharsets.UTF_8)))
                .map(x -> (RespValue) x).toList();
        RespEncoder.write(new RespValue.Array(a), s.getOutputStream());
        s.getOutputStream().flush();
        return RespParser.read(s.getInputStream());
    }

    private String simple(RespValue v) {
        return ((RespValue.Simple) v).value();
    }

    private String bulk(RespValue v) {
        return new String(((RespValue.Bulk) v).value(), StandardCharsets.UTF_8);
    }

    private long number(RespValue v) {
        return ((RespValue.Number) v).value();
    }
}
