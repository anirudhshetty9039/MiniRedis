package com.miniredis.benchmark;

import com.miniredis.protocol.*;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/** Simple socket benchmark; start MiniRedis separately before running it. */
public final class MiniRedisBenchmark {
    private MiniRedisBenchmark() { }

    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 6379;
        int clients = args.length > 2 ? Integer.parseInt(args[2]) : 4;
        int operations = args.length > 3 ? Integer.parseInt(args[3]) : 10_000;
        String workload = args.length > 4 ? args[4].toUpperCase() : "MIXED";
        if (clients < 1 || operations < 1 || !List.of("SET", "GET", "MIXED").contains(workload))
            throw new IllegalArgumentException("usage: host port clients operations SET|GET|MIXED");

        ExecutorService pool = Executors.newFixedThreadPool(clients);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(clients);
        AtomicLong completed = new AtomicLong();
        long started = System.nanoTime();
        for (int client = 0; client < clients; client++) {
            int id = client;
            pool.execute(() -> {
                try (Socket socket = new Socket(host, port)) {
                    start.await();
                    for (int i = 0; i < operations; i++) {
                        boolean set = workload.equals("SET") || (workload.equals("MIXED") && (i & 1) == 0);
                        send(socket, set ? "SET" : "GET", "bench-" + id + '-' + (i % 100), "value");
                        completed.incrementAndGet();
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        done.await();
        pool.shutdown();
        double seconds = (System.nanoTime() - started) / 1_000_000_000.0;
        System.out.printf("operations=%d duration_seconds=%.3f operations_per_second=%.1f clients=%d workload=%s%n",
                completed.get(), seconds, completed.get() / seconds, clients, workload);
    }

    private static void send(Socket socket, String command, String key, String value) throws IOException {
        List<RespValue> args = command.equals("GET")
                ? List.of(bulk(command), bulk(key))
                : List.of(bulk(command), bulk(key), bulk(value));
        RespEncoder.write(new RespValue.Array(args), socket.getOutputStream());
        socket.getOutputStream().flush();
        if (RespParser.read(socket.getInputStream()) == null)
            throw new EOFException("server closed connection");
    }

    private static RespValue.Bulk bulk(String value) {
        return new RespValue.Bulk(value.getBytes(StandardCharsets.UTF_8));
    }
}