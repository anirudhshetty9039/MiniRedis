package com.miniredis.persistence;

import com.miniredis.command.*;
import com.miniredis.protocol.*;
import com.miniredis.storage.*;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class AofPersistenceTest {
    @TempDir
    Path dir;

    @Test
    void replaysCommandsAndIgnoresPartialTail() throws Exception {
        try (AofPersistence aof = new AofPersistence(dir)) {
            aof.append(List.of("SET", "name", "Ada"));
            aof.append(List.of("DEL", "gone"));
        }
        Files.write(dir.resolve("miniredis.aof"), "*2\r\n$3\r\nSET\r\n$4\r\npart".getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.APPEND);
        InMemoryKeyValueStore store = new InMemoryKeyValueStore();
        try (AofPersistence aof = new AofPersistence(dir)) {
            aof.replay(new CommandDispatcher(store, null)::replay);
        }
        assertEquals("Ada", store.get("name").orElseThrow());
    }
}
