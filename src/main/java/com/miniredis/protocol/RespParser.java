package com.miniredis.protocol;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Streaming RESP2 parser. EOF before a new value returns null; EOF mid-value is
 * an error.
 */
public final class RespParser {
    private RespParser() {
    }

    public static RespValue read(InputStream input) throws IOException {
        return read(input, true);
    }

    private static RespValue read(InputStream input, boolean topLevel) throws IOException {
        int marker = input.read();
        if (marker == -1) {
            if (topLevel)
                return null;
            throw new EOFException("incomplete RESP value");
        }
        String line = line(input);
        return switch (marker) {
            case '+' -> new RespValue.Simple(line);
            case '-' -> new RespValue.Error(line);
            case ':' -> new RespValue.Number(number(line));
            case '$' -> bulk(input, number(line));
            case '*' -> array(input, number(line));
            default -> throw new RespException("unknown RESP type: " + (char) marker);
        };
    }

    private static RespValue.Bulk bulk(InputStream in, long length) throws IOException {
        if (length == -1)
            return new RespValue.Bulk(null);
        if (length < -1 || length > Integer.MAX_VALUE)
            throw new RespException("invalid bulk length");
        byte[] data = in.readNBytes((int) length);
        if (data.length != length)
            throw new EOFException("incomplete bulk string");
        requireCrlf(in);
        return new RespValue.Bulk(data);
    }

    private static RespValue.Array array(InputStream in, long count) throws IOException {
        if (count == -1)
            return new RespValue.Array(null);
        if (count < -1 || count > 1_000_000)
            throw new RespException("invalid array length");
        List<RespValue> values = new ArrayList<>();
        for (int i = 0; i < count; i++)
            values.add(read(in, false));
        return new RespValue.Array(List.copyOf(values));
    }

    private static long number(String text) throws RespException {
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            throw new RespException("invalid integer");
        }
    }

    private static String line(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int previous = -1;
        while (true) {
            int next = in.read();
            if (next == -1)
                throw new EOFException("incomplete RESP line");
            if (previous == '\r' && next == '\n') {
                byte[] raw = out.toByteArray();
                return new String(raw, 0, raw.length - 1, StandardCharsets.UTF_8);
            }
            out.write(next);
            previous = next;
            if (out.size() > 16 * 1024 * 1024)
                throw new RespException("RESP line too large");
        }
    }

    private static void requireCrlf(InputStream in) throws IOException {
        if (in.read() != '\r' || in.read() != '\n')
            throw new RespException("bulk string missing CRLF");
    }
}
