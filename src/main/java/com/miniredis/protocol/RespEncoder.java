package com.miniredis.protocol;

import java.io.*;
import java.nio.charset.StandardCharsets;

public final class RespEncoder {
    private static final byte[] CRLF = "\r\n".getBytes(StandardCharsets.US_ASCII);

    private RespEncoder() {
    }

    public static void write(RespValue value, OutputStream out) throws IOException {
        if (value instanceof RespValue.Simple v)
            line('+', v.value(), out);
        else if (value instanceof RespValue.Error v)
            line('-', v.message(), out);
        else if (value instanceof RespValue.Number v)
            line(':', Long.toString(v.value()), out);
        else if (value instanceof RespValue.Bulk v) {
            if (v.isNull()) {
                out.write("$-1\r\n".getBytes(StandardCharsets.US_ASCII));
            } else {
                out.write(('$' + Integer.toString(v.value().length) + "\r\n").getBytes(StandardCharsets.US_ASCII));
                out.write(v.value());
                out.write(CRLF);
            }
        } else if (value instanceof RespValue.Array v) {
            if (v.isNull()) {
                out.write("*-1\r\n".getBytes(StandardCharsets.US_ASCII));
            } else {
                out.write(('*' + Integer.toString(v.values().size()) + "\r\n").getBytes(StandardCharsets.US_ASCII));
                for (RespValue item : v.values())
                    write(item, out);
            }
        }
    }

    public static byte[] encode(RespValue value) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            write(value, out);
            return out.toByteArray();
        } catch (IOException impossible) {
            throw new UncheckedIOException(impossible);
        }
    }

    private static void line(char prefix, String text, OutputStream out) throws IOException {
        out.write((prefix + text + "\r\n").getBytes(StandardCharsets.UTF_8));
    }
}
