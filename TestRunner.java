
package com.cibc.main;

import com.cibc.main.SqlServerClientWrapper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.LockSupport;

import com.cibc.main.config.Config;
import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisBusyException;

public class TestRunner {
    private static final Duration REDIS_TIMEOUT = Duration.ofSeconds(10000);
    private static final long POLL_INTERVAL_NANOS = TimeUnit.MILLISECONDS.toNanos(5);

    private final SqlServerClientWrapper sqlClient;
    private final RedisClientWrapper redisClient;

    public TestRunner(SqlServerClientWrapper sqlClient, RedisClientWrapper redisClient) {
        this.sqlClient = sqlClient;
        this.redisClient = redisClient;
    }

    public List<TestResult> runSingles() throws InterruptedException, ExecutionException {
        List<TestResult> inserts = new ArrayList<>();
        List<TestResult> results = new ArrayList<>();
        String runId = UUID.randomUUID().toString();
        ExecutorService executor = Executors.newFixedThreadPool(10);
        List<Future<TestResult>> futures = new ArrayList<>();

        System.out.println("Running Inserts...");
        for (int i = 0; i < 10; i++) {
            futures.add(executor.submit(() -> runSingle(runId, "INSERT", null, "insert-" + runId + "-" )));
        }

        for (Future<TestResult> future : futures) {
            inserts.add(future.get());
        }

        System.out.println("Running Updates...");
        for (TestResult insert : inserts) {
            futures.add(executor.submit(() -> runSingle(runId, "UPDATE", insert.recordId(), "update-" + runId + "-" )));
        }

        for (Future<TestResult> future : futures) {
            results.add(future.get());
        }

        executor.shutdown();

        return results;
    }

    // Insert test
    public TestResult runSingle(String runId, String operation, Long id, String data) {
        long totalStart = System.currentTimeMillis();

        try {
            long sqlStart = System.currentTimeMillis();

            switch (operation) {
                case "INSERT" -> id = sqlClient.insert(data);
                case "UPDATE" -> sqlClient.update(id, data);
                case "DELETE" -> sqlClient.delete(id);
                default -> throw new IllegalArgumentException("Unsupported operation: " + operation);
            }

            double sqlDuration = System.currentTimeMillis() - sqlStart;
            System.out.println(operation + " operation successfully executed in Azure: " + id);

            String redisKey = buildRedisKey(id);

            // Wait for Redis
            long redisStart = System.currentTimeMillis();

            if (operation == "UPDATE") {
                waitForRedisPropagation(operation, redisKey, data);
            } else {
                waitForRedisPropagation(operation, redisKey);
            }

            double redisPropDuration = System.currentTimeMillis() - redisStart;
            double totalDuration = System.currentTimeMillis() - totalStart;

            TestResult testResult = new TestResult(
                    runId,
                    Instant.now(),
                    operation,
                    "single",
                    id,
                    1,
                    sqlDuration,
                    redisPropDuration,
                    totalDuration,
                    true,
                    false,
                    null
            );

            return testResult;

        } catch (Exception e) {
            return new TestResult(
                    runId,
                    Instant.now(),
                    operation,
                    "single",
                    null,
                    1,
                    0,
                    0,
                    (System.currentTimeMillis() - totalStart) / 1_000_000_000.0,
                    false,
                    e instanceof RedisTimeoutException,
                    e.getMessage()
            );
        }
    }

    private void waitForRedisPropagation(String operation, String key) throws RedisTimeoutException {
        long deadline = System.nanoTime() + REDIS_TIMEOUT.toNanos();

        if (operation == "INSERT") {
            System.out.println("Waiting for key " + key + " to appear in Redis...");
        } else {
            System.out.println("Waiting for key " + key + " to disappear from Redis...");
        }

        while (System.nanoTime() < deadline) {
            boolean appears = redisClient.exists(key);

            if (operation == "INSERT" && appears) {
                return;
            } else if (operation == "DELETE" && !appears) {
                return;
            }

            LockSupport.parkNanos(POLL_INTERVAL_NANOS);
        }

        throw new RedisTimeoutException(
                "Redis did not reflect change within " + REDIS_TIMEOUT.toSeconds() + "seconds"
        );
    }

    private void waitForRedisPropagation(String operation, String key, String data) throws RedisTimeoutException {
        long deadline = System.nanoTime() + REDIS_TIMEOUT.toNanos();

        System.out.println("Waiting for redis to reflect change on key " + key);

        while (System.nanoTime() < deadline) {
            List<Object> newData = redisClient.readData(key);

            String newDataStr = newData.stream()
                    .map(Object obj -> obj.toString())
                    .findFirst() // Get the first element as a string
                    .orElse("").replaceAll("\"", "");

            if (newDataStr.equals(data)) {
                return;
            }

            LockSupport.parkNanos(POLL_INTERVAL_NANOS);
        }

        throw new RedisTimeoutException(
                "Redis did not reflect change within " + REDIS_TIMEOUT.toSeconds() + "seconds"
        );
    }

    // Key formatting
    private String buildRedisKey(long id) {
        return "rdi_test:id:" + id;
    }

    // Custom exception
    private static final class RedisTimeoutException extends Exception {
        private RedisTimeoutException(String message) {
            super(message);
        }
    }

    public static TestRunner create() throws Exception {
        Config config = new Config();

        ClientFactory factory = new ClientFactory(config);

        RedisClientWrapper redisClientWrapper = new RedisClientWrapper(factory.createRedisClient());
        SQLServerDataSource sqlDataSource = factory.createAzureSqlClient();

        SqlServerClientWrapper sqlServerClientWrapper = new SqlServerClientWrapper(sqlDataSource);

        return new TestRunner(sqlServerClientWrapper, redisClientWrapper);
    }
}
