package com.miniredis.persistence;

import com.miniredis.protocol.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * RESP-framed append-only command log. A partial trailing record is ignored
 * during replay.
 */
public final class AofPersistence implements AutoCloseable {
    @FunctionalInterface
    public interface ReplayTarget {
        void apply(List<String> command);
    }

    private final Path file;
    private final Object lock = new Object();
    private OutputStream output;

    public AofPersistence(Path dataDirectory) throws IOException {
        Files.createDirectories(dataDirectory);
        file = dataDirectory.resolve("miniredis.aof");
        output = new BufferedOutputStream(
                Files.newOutputStream(file, StandardOpenOption.CREATE, StandardOpenOption.APPEND));
    }

    public void append(List<String> command) throws IOException {
        synchronized (lock) {
            List<RespValue> values = command.stream()
                    .map(s -> new RespValue.Bulk(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                    .map(v -> (RespValue) v).toList();
            RespEncoder.write(new RespValue.Array(values), output);
            output.flush();
        }
    }

    public void replay(ReplayTarget target) throws IOException {
        if (!Files.exists(file))
            return;
        try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
            while (true) {
                try {
                    RespValue value = RespParser.read(in);
                    if (value == null)
                        return;
                    List<String> command = command(value);
                    if (command != null)
                        target.apply(command);
                } catch (EOFException | RespException ignoredTrailingRecord) {
                    return;
                }
            }
        }
    }

    private static List<String> command(RespValue value) {
        if (!(value instanceof RespValue.Array array) || array.isNull())
            return null;
        List<String> result = new ArrayList<>();
        for (RespValue item : array.values()) {
            if (!(item instanceof RespValue.Bulk bulk) || bulk.isNull())
                return null;
            result.add(new String(bulk.value(), java.nio.charset.StandardCharsets.UTF_8));
        }
        return result;
    }

    public Path file() {
        return file;
    }

    public void close() throws IOException {
        synchronized (lock) {
            output.close();
        }
    }
}
