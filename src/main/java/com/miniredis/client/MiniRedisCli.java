package com.miniredis.client;

import com.miniredis.protocol.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Minimal interactive demo client; quote handling is intentionally not
 * implemented.
 */
public final class MiniRedisCli {
    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 6379;
        try (Socket socket = new Socket(host, port);
                BufferedReader console = new BufferedReader(new InputStreamReader(System.in));
                InputStream in = socket.getInputStream();
                OutputStream out = socket.getOutputStream()) {
            System.out.println("Connected. Enter Redis commands; Ctrl+Z then Enter exits.");
            String line;
            while ((line = console.readLine()) != null) {
                List<RespValue> values = Arrays.stream(line.trim().split("\\s+")).filter(s -> !s.isEmpty())
                        .map(s -> new RespValue.Bulk(s.getBytes(StandardCharsets.UTF_8))).map(v -> (RespValue) v)
                        .toList();
                if (values.isEmpty())
                    continue;
                RespEncoder.write(new RespValue.Array(values), out);
                out.flush();
                System.out.println(render(RespParser.read(in)));
            }
        }
    }

    private static String render(RespValue v) {
        if (v instanceof RespValue.Bulk b)
            return b.isNull() ? "(nil)" : new String(b.value(), StandardCharsets.UTF_8);
        if (v instanceof RespValue.Simple s)
            return s.value();
        if (v instanceof RespValue.Error e)
            return "(error) " + e.message();
        if (v instanceof RespValue.Number n)
            return Long.toString(n.value());
        if (v instanceof RespValue.Array a)
            return a.values().toString();
        return "(nil)";
    }
}
