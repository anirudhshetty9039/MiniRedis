package com.miniredis.command;

import com.miniredis.persistence.AofPersistence;
import com.miniredis.protocol.*;
import com.miniredis.storage.KeyValueStore;
import com.miniredis.metrics.Metrics;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Converts protocol arrays to commands; storage remains independent of both
 * sockets and RESP.
 */
public final class CommandDispatcher {
    private final KeyValueStore store;
    private final AofPersistence aof;
    private final Metrics metrics;

    public CommandDispatcher(KeyValueStore store, AofPersistence aof) {
        this(store, aof, new Metrics());
    }

    public CommandDispatcher(KeyValueStore store, AofPersistence aof, Metrics metrics) {
        this.store = store;
        this.aof = aof;
        this.metrics = metrics;
    }

    public RespValue execute(RespValue request) {
        try {
            return execute(toStrings(request), true);
        } catch (CommandError e) {
            return new RespValue.Error("ERR " + e.getMessage());
        } catch (IOException e) {
            return new RespValue.Error("ERR persistence failure: " + e.getMessage());
        }
    }

    public void replay(List<String> command) {
        try {
            execute(command, false);
        } catch (Exception ignored) {
            /* a malformed historical command is skipped */ }
    }

    private RespValue execute(List<String> args, boolean persist) throws IOException, CommandError {
        if (args.isEmpty())
            throw new CommandError("empty command");
        String op = args.get(0).toUpperCase(Locale.ROOT);
        if (persist)
            metrics.command();
        RespValue result;
        boolean mutation = false;
        switch (op) {
            case "PING" -> {
                require(args, 1, 2);
                result = args.size() == 1 ? new RespValue.Simple("PONG") : bulk(args.get(1));
            }
            case "GET" -> {
                require(args, 2);
                Optional<String> value = store.get(args.get(1));
                metrics.get(value.isPresent());
                result = value.<RespValue>map(CommandDispatcher::bulk).orElse(new RespValue.Bulk(null));
            }
            case "SET" -> {
                if (args.size() != 3 && args.size() != 5)
                    throw new CommandError("wrong number of arguments for 'set'");
                Long expiry = null;
                if (args.size() == 5) {
                    if (!args.get(3).equalsIgnoreCase("EX"))
                        throw new CommandError("unsupported SET option");
                    long seconds = positive(args.get(4));
                    expiry = System.currentTimeMillis() + Math.multiplyExact(seconds, 1000);
                }
                store.set(args.get(1), args.get(2), expiry);
                if (persist)
                    metrics.set();
                result = new RespValue.Simple("OK");
                mutation = true;
            }
            case "DEL" -> {
                if (args.size() < 2)
                    throw new CommandError("wrong number of arguments for 'del'");
                long n = 0;
                for (int i = 1; i < args.size(); i++)
                    if (store.delete(args.get(i)))
                        n++;
                result = new RespValue.Number(n);
                mutation = n > 0;
            }
            case "EXISTS" -> {
                if (args.size() < 2)
                    throw new CommandError("wrong number of arguments for 'exists'");
                long n = 0;
                for (int i = 1; i < args.size(); i++)
                    if (store.exists(args.get(i)))
                        n++;
                result = new RespValue.Number(n);
            }
            case "EXPIRE" -> {
                require(args, 3);
                boolean changed = store.expire(args.get(1), integer(args.get(2)));
                result = new RespValue.Number(changed ? 1 : 0);
                mutation = changed;
            }
            case "TTL" -> {
                require(args, 2);
                result = new RespValue.Number(store.ttl(args.get(1)));
            }
            case "KEYS" -> {
                require(args, 2);
                if (!args.get(1).equals("*"))
                    throw new CommandError("only KEYS * is supported");
                result = new RespValue.Array(
                        store.keys().stream().map(CommandDispatcher::bulk).map(x -> (RespValue) x).toList());
            }
            case "INFO" -> {
                require(args, 1);
                result = bulk(metrics.info(store.keys().size()));
            }
            default -> throw new CommandError("unknown command '" + args.get(0) + "'");
        }
        if (persist && mutation && aof != null)
            aof.append(args);
        return result;
    }

    private static List<String> toStrings(RespValue request) throws CommandError {
        if (!(request instanceof RespValue.Array a) || a.isNull())
            throw new CommandError("expected command array");
        List<String> out = new ArrayList<>();
        for (RespValue v : a.values()) {
            if (!(v instanceof RespValue.Bulk b) || b.isNull())
                throw new CommandError("command arguments must be bulk strings");
            out.add(new String(b.value(), StandardCharsets.UTF_8));
        }
        return out;
    }

    private static RespValue.Bulk bulk(String value) {
        return new RespValue.Bulk(value.getBytes(StandardCharsets.UTF_8));
    }

    private static void require(List<String> a, int n) throws CommandError {
        if (a.size() != n)
            throw new CommandError("wrong number of arguments");
    }

    private static void require(List<String> a, int min, int max) throws CommandError {
        if (a.size() < min || a.size() > max)
            throw new CommandError("wrong number of arguments");
    }

    private static long integer(String v) throws CommandError {
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException e) {
            throw new CommandError("value is not an integer or out of range");
        }
    }

    private static long positive(String v) throws CommandError {
        long n = integer(v);
        if (n <= 0)
            throw new CommandError("invalid expiration");
        return n;
    }

    private static final class CommandError extends Exception {
        CommandError(String m) {
            super(m);
        }
    }
}
