package com.miniredis.config;

import java.nio.file.Path;

public record ServerConfig(int port, Path dataDirectory, boolean aofEnabled, int workerThreads) {
    public static ServerConfig defaults() {
        return new ServerConfig(
                6379,
                Path.of("data"),
                true,
                Math.max(2, Runtime.getRuntime().availableProcessors())
        );
    }

    public static ServerConfig fromArgs(String[] args) {
        ServerConfig d = defaults();
        int port = d.port;
        Path data = d.dataDirectory;
        boolean aof = d.aofEnabled;
        int workers = d.workerThreads;

        for (String arg : args) {
            String[] p = arg.split("=", 2);
            if (p.length != 2 || !p[0].startsWith("--")) {
                throw new IllegalArgumentException("invalid option: " + arg);
            }

            switch (p[0]) {
                case "--port" -> port = integer(p[1], "port");
                case "--data-dir" -> data = Path.of(p[1]);
                case "--aof" -> aof = Boolean.parseBoolean(p[1]);
                case "--workers" -> workers = integer(p[1], "workers");
                default -> throw new IllegalArgumentException("unknown option: " + p[0]);
            }
        }

        if (port < 1 || port > 65535 || workers < 1) {
            throw new IllegalArgumentException("port and workers must be positive");
        }
        return new ServerConfig(port, data, aof, workers);
    }

    private static int integer(String value, String name) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid " + name);
        }
    }
}
