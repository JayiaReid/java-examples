package com.cibc.main.redis;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisCommandExecutionException;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

public final class RedisClientWrapper implements AutoCloseable {
    private final RedisClient client;
    private final StatefulRedisConnection<String, String> connection;
    private final RedisCommands<String, String> commands;

    public RedisClientWrapper(RedisClient client) {
        this.client = client;
        this.connection = client.connect();
        this.commands = connection.sync();
    }

    public boolean testConnection() {
        try { return "PONG".equalsIgnoreCase(commands.ping()); }
        catch (Exception e) {
            System.err.println("Redis connection test failed: " + e.getMessage());
            return false;
        }
    }

    public void write(String key, String value) {
        try { commands.set(key, value); }
        catch (RedisCommandExecutionException e) { throw new IllegalStateException("Error writing to Redis", e); }
    }

    public String read(String key) {
        try { return commands.get(key); }
        catch (RedisCommandExecutionException e) { throw new IllegalStateException("Error reading from Redis", e); }
    }

    public boolean exists(String key) {
        try { return commands.exists(key) > 0; }
        catch (RedisCommandExecutionException e) { throw new IllegalStateException("Error checking Redis key", e); }
    }

    public void delete(String key) {
        try { commands.del(key); }
        catch (RedisCommandExecutionException e) { throw new IllegalStateException("Error deleting Redis key", e); }
    }

    @Override
    public void close() {
        try { connection.close(); } catch (Exception e) { System.err.println("Error closing Redis connection: " + e.getMessage()); }
        try { client.shutdown(); } catch (Exception e) { System.err.println("Error shutting down Redis client: " + e.getMessage()); }
    }
}
