
package com.cibc.main;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisCommandExecutionException;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.api.sync.RedisJsonCommands;
import io.lettuce.core.json.JsonPath;

import java.util.List;

public class RedisClientWrapper {

    private final RedisClient client;
    private final StatefulRedisConnection<String, String> connection;
    private final RedisCommands<String, String> commands;

    public RedisClientWrapper(RedisClient client) {
        this.client = client;
        this.connection = client.connect();
        this.commands = connection.sync();
    }

    public boolean testConnection() {
        try {
            String response = commands.ping();
            return "PONG".equalsIgnoreCase(response);
        } catch (Exception e) {
            System.err.println("Redis connection test failed: " + e.getMessage());
            return false;
        }
    }

    // CRUD
    public void write(String key, String value) {
        try {
            commands.set(key, value);
        } catch (RedisCommandExecutionException e) {
            throw new IllegalStateException("Error writing to Redis: " + e);
        }
    }

    public List readData(String key) {
        try {
            return commands.jsonGet(key, new JsonPath("data"));
        } catch (RedisCommandExecutionException e) {
            throw new IllegalStateException("Error reading from Redis: " + e);
        }
    }

    public boolean exists(String key) {
        try {
            return commands.exists(key) > 0;
        } catch (RedisCommandExecutionException e) {
            throw new IllegalStateException("Error checking Redis key", e);
        }
    }

    public void delete(String key) {
        try {
            commands.del(key);
        } catch (RedisCommandExecutionException e) {
            throw new IllegalStateException("Error deleting Redis key", e);
        }
    }

    // Shutdown
    public void close() {
        try {
            connection.close();
            client.shutdown();
        } catch (Exception e) {
            System.err.println("Error closing Redis client: " + e.getMessage());
        }
    }
}
