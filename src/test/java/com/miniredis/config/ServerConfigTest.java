package com.miniredis.config;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ServerConfigTest {
    @Test
    void parsesMaxKeysAndKeepsZeroUnlimited() {
        ServerConfig config = ServerConfig.fromArgs(new String[] { "--max-keys=25", "--workers=2" });
        assertEquals(25, config.maxKeys());
        assertEquals(2, config.workerThreads());
        assertEquals(0, ServerConfig.defaults().maxKeys());
    }

    @Test
    void rejectsNegativeMaxKeys() {
        assertThrows(IllegalArgumentException.class,
                () -> ServerConfig.fromArgs(new String[] { "--max-keys=-1" }));
    }
}