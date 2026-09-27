package com.miniredis.metrics;

import java.util.concurrent.atomic.AtomicLong;

/** Small lock-free counters shared by command workers. */
public final class Metrics {
    private final long startedAt = System.nanoTime();
    private final AtomicLong commandsProcessed = new AtomicLong();
    private final AtomicLong getCommands = new AtomicLong();
    private final AtomicLong setCommands = new AtomicLong();
    private final AtomicLong cacheHits = new AtomicLong();
    private final AtomicLong cacheMisses = new AtomicLong();
    private final AtomicLong evictions = new AtomicLong();
    private final AtomicLong connectedClients = new AtomicLong();

    public void command() { commandsProcessed.incrementAndGet(); }
    public void get(boolean hit) {
        getCommands.incrementAndGet();
        (hit ? cacheHits : cacheMisses).incrementAndGet();
    }
    public void set() { setCommands.incrementAndGet(); }
    public void eviction() { evictions.incrementAndGet(); }
    public void clientConnected() { connectedClients.incrementAndGet(); }
    public void clientDisconnected() { connectedClients.decrementAndGet(); }

    public String info(int keys) {
        return "commands_processed:" + commandsProcessed.get() + "\n"
                + "get_commands:" + getCommands.get() + "\n"
                + "set_commands:" + setCommands.get() + "\n"
                + "cache_hits:" + cacheHits.get() + "\n"
                + "cache_misses:" + cacheMisses.get() + "\n"
                + "keys:" + keys + "\n"
                + "evictions:" + evictions.get() + "\n"
                + "uptime_seconds:" + ((System.nanoTime() - startedAt) / 1_000_000_000L) + "\n"
                + "connected_clients:" + connectedClients.get();
    }
}