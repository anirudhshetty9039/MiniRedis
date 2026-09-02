package com.miniredis.server;

import com.miniredis.command.CommandDispatcher;
import com.miniredis.config.ServerConfig;
import com.miniredis.persistence.AofPersistence;
import com.miniredis.protocol.*;
import com.miniredis.storage.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;

public final class MiniRedisServer implements AutoCloseable {
    private final ServerConfig config;
    private final KeyValueStore store = new InMemoryKeyValueStore();
    private final ExecutorService workers;
    private final ScheduledExecutorService maintenance = Executors.newSingleThreadScheduledExecutor();
    private volatile boolean running;
    private ServerSocket socket;
    private AofPersistence aof;
    private CommandDispatcher dispatcher;
    private Thread acceptor;

    public MiniRedisServer(ServerConfig config) {
        this.config = config;
        workers = Executors.newFixedThreadPool(config.workerThreads());
    }

    public synchronized void start() throws IOException {
        if (running)
            return;
        aof = config.aofEnabled() ? new AofPersistence(config.dataDirectory()) : null;
        dispatcher = new CommandDispatcher(store, aof);
        if (aof != null)
            aof.replay(dispatcher::replay);
        socket = new ServerSocket();
        socket.bind(new InetSocketAddress(config.port()));
        running = true;
        maintenance.scheduleAtFixedRate(store::cleanupExpired, 1, 1, TimeUnit.SECONDS);
        acceptor = new Thread(this::acceptLoop, "miniredis-acceptor");
        acceptor.start();
    }

    private void acceptLoop() {
        while (running)
            try {
                Socket client = socket.accept();
                workers.execute(() -> handle(client));
            } catch (IOException e) {
                if (running)
                    System.err.println("accept failed: " + e.getMessage());
            }
    }

    private void handle(Socket client) {
        try (client;
                InputStream in = new BufferedInputStream(client.getInputStream());
                OutputStream out = new BufferedOutputStream(client.getOutputStream())) {
            while (running && !client.isClosed()) {
                RespValue request;
                try {
                    request = RespParser.read(in);
                } catch (IOException e) {
                    RespEncoder.write(new RespValue.Error("ERR malformed protocol: " + e.getMessage()), out);
                    out.flush();
                    return;
                }
                if (request == null)
                    return;
                RespEncoder.write(dispatcher.execute(request), out);
                out.flush();
            }
        } catch (IOException ignored) {
        }
    }

    public int port() {
        return socket == null ? config.port() : socket.getLocalPort();
    }

    public synchronized void close() throws IOException {
        running = false;
        if (socket != null)
            socket.close();
        maintenance.shutdownNow();
        workers.shutdownNow();
        if (aof != null)
            aof.close();
    }

    public static void main(String[] args) throws Exception {
        ServerConfig config = ServerConfig.fromArgs(args);
        MiniRedisServer server = new MiniRedisServer(config);
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                server.close();
            } catch (IOException ignored) {
            }
        }));
        System.out.println("MiniRedis listening on port " + server.port());
        Thread.currentThread().join();
    }
}
