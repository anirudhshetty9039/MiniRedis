package com.miniredis.protocol;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class RespProtocolTest {
    @Test
    void parsesEverySupportedValue() throws Exception {
        assertEquals(new RespValue.Simple("OK"), read("+OK\r\n"));
        assertEquals(new RespValue.Error("bad"), read("-bad\r\n"));
        assertEquals(new RespValue.Number(42), read(":42\r\n"));
        assertEquals("hello", new String(((RespValue.Bulk) read("$5\r\nhello\r\n")).value(), StandardCharsets.UTF_8));
        assertTrue(((RespValue.Bulk) read("$-1\r\n")).isNull());
        assertTrue(((RespValue.Array) read("*-1\r\n")).isNull());
    }

    @Test
    void parsesArraysAndRoundTrips() throws Exception {
        RespValue value = read("*2\r\n$4\r\nPING\r\n$2\r\nhi\r\n");
        assertEquals("PING", new String(((RespValue.Bulk) ((RespValue.Array) value).values().get(0)).value()));
        assertArrayEquals(RespEncoder.encode(value),
                RespEncoder.encode(RespParser.read(new ByteArrayInputStream(RespEncoder.encode(value)))));
    }

    @Test
    void rejectsMalformedAndIncompleteInput() {
        assertThrows(RespException.class, () -> read("?x\r\n"));
        assertThrows(EOFException.class, () -> read("$5\r\nabc"));
        assertThrows(RespException.class, () -> read(":x\r\n"));
    }

    private RespValue read(String value) throws Exception {
        return RespParser.read(new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8)));
    }
}
