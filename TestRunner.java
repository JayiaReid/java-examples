package com.cibc.main.test;

import com.cibc.main.config.Config;
import com.cibc.main.redis.RedisClientWrapper;
import com.cibc.main.sql.SqlServerClientWrapper;
import com.cibc.main.sql.SqlServerClientWrapper.SqlTestRecord;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.LockSupport;

public final class TestRunner {
    private final SqlServerClientWrapper sql;
    private final RedisClientWrapper redis;
    private final String keyPrefix;
    private final long timeoutNanos;
    private final long pollIntervalNanos;

    public TestRunner(SqlServerClientWrapper sql, RedisClientWrapper redis, Config config) {
        this.sql = sql;
        this.redis = redis;
        this.keyPrefix = config.getRedisKeyPrefix();
        this.timeoutNanos = Duration.ofMillis(config.getRedisTimeoutMillis()).toNanos();
        this.pollIntervalNanos = Duration.ofMillis(config.getPollIntervalMillis()).toNanos();
        if (timeoutNanos <= 0 || pollIntervalNanos <= 0) {
            throw new IllegalArgumentException("Redis timeout and polling interval must be positive.");
        }
    }

    public List<TestResult> runSingleCrudCycle(long id, String runId) {
        List<TestResult> results = new ArrayList<>();
        results.add(runSingle("INSERT", id, "insert-" + runId + "-" + id, runId));
        // Only update/delete rows that the preceding INSERT succeeded in creating.
        if (results.get(0).success()) {
            results.add(runSingle("UPDATE", id, "update-" + runId + "-" + id, runId));
            if (results.get(1).success()) results.add(runSingle("DELETE", id, null, runId));
        }
        return results;
    }

    public List<TestResult> runBatchCrudCycle(List<Long> ids, String runId) {
        if (ids.isEmpty()) return List.of();
        String batchId = UUID.randomUUID().toString();
        int size = ids.size();
        List<TestResult> results = new ArrayList<>(size * 3);
        List<SqlTestRecord> inserts = ids.stream()
                .map(id -> new SqlTestRecord(id, "insert-" + runId + "-" + id)).toList();

        long totalStart = System.nanoTime();
        long sqlStart = System.nanoTime();
        try {
            sql.batchInsert(inserts);
            long sqlDuration = System.nanoTime() - sqlStart;
            long propagationStart = System.nanoTime();
            for (int i = 0; i < inserts.size(); i++) {
                SqlTestRecord record = inserts.get(i);
                results.add(awaitResult(runId, "INSERT", "BATCH", batchId, size, record.id(),
                        record.data(), false, sqlDuration, i == 0, totalStart, propagationStart));
            }
        } catch (Exception e) {
            long elapsed = System.nanoTime() - totalStart;
            for (int i = 0; i < inserts.size(); i++) {
                results.add(failure(runId, "INSERT", "BATCH", batchId, size, inserts.get(i).id(),
                        elapsed, i == 0 ? elapsed : 0, e));
            }
            return results;
        }

        List<SqlTestRecord> updates = ids.stream()
                .map(id -> new SqlTestRecord(id, "update-" + runId + "-" + id)).toList();
        totalStart = System.nanoTime();
        sqlStart = System.nanoTime();
        try {
            sql.batchUpdate(updates);
            long sqlDuration = System.nanoTime() - sqlStart;
            long propagationStart = System.nanoTime();
            for (int i = 0; i < updates.size(); i++) {
                SqlTestRecord record = updates.get(i);
                results.add(awaitResult(runId, "UPDATE", "BATCH", batchId, size, record.id(),
                        record.data(), false, sqlDuration, i == 0, totalStart, propagationStart));
            }
        } catch (Exception e) {
            long elapsed = System.nanoTime() - totalStart;
            for (int i = 0; i < updates.size(); i++) {
                results.add(failure(runId, "UPDATE", "BATCH", batchId, size, updates.get(i).id(),
                        elapsed, i == 0 ? elapsed : 0, e));
            }
            return results;
        }

        totalStart = System.nanoTime();
        sqlStart = System.nanoTime();
        try {
            sql.batchDelete(ids.stream().map(id -> new SqlTestRecord(id, "")).toList());
            long sqlDuration = System.nanoTime() - sqlStart;
            long propagationStart = System.nanoTime();
            for (int i = 0; i < ids.size(); i++) {
                results.add(awaitResult(runId, "DELETE", "BATCH", batchId, size, ids.get(i),
                        null, true, sqlDuration, i == 0, totalStart, propagationStart));
            }
        } catch (Exception e) {
            long elapsed = System.nanoTime() - totalStart;
            for (int i = 0; i < ids.size(); i++) {
                results.add(failure(runId, "DELETE", "BATCH", batchId, size, ids.get(i),
                        elapsed, i == 0 ? elapsed : 0, e));
            }
        }
        return results;
    }

    private TestResult runSingle(String operation, long id, String value, String runId) {
        String batchId = UUID.randomUUID().toString();
        long totalStart = System.nanoTime();
        long sqlDuration = 0;
        try {
            long sqlStart = System.nanoTime();
            switch (operation) {
                case "INSERT" -> sql.insert(id, value);
                case "UPDATE" -> sql.update(id, value);
                case "DELETE" -> sql.delete(id);
                default -> throw new IllegalArgumentException("Unsupported operation: " + operation);
            }
            sqlDuration = System.nanoTime() - sqlStart;
            long propagationStart = System.nanoTime();
            return awaitResult(runId, operation, "SINGLE", batchId, 1, id, value,
                    "DELETE".equals(operation), sqlDuration, true, totalStart, propagationStart);
        } catch (Exception e) {
            return failure(runId, operation, "SINGLE", batchId, 1, id,
                    System.nanoTime() - totalStart, sqlDuration, e);
        }
    }

    private TestResult awaitResult(String runId, String operation, String mode, String batchId,
                                   int batchSize, long id, String expectedValue, boolean expectMissing,
                                   long sqlDuration, boolean includeSqlSample, long totalStart,
                                   long propagationStart) {
        long deadline = propagationStart + timeoutNanos;
        int attempts = 0;
        try {
            String key = keyPrefix + id;
            while (System.nanoTime() < deadline) {
                attempts++;
                boolean matched;
                if (expectMissing) {
                    matched = !redis.exists(key);
                } else {
                    matched = expectedValue != null && expectedValue.equals(redis.read(key));
                }
                if (matched) {
                    long propagation = System.nanoTime() - propagationStart;
                    return new TestResult(runId, Instant.now(), operation, mode, batchId, batchSize, id,
                            includeSqlSample ? sqlDuration : 0, propagation,
                            System.nanoTime() - totalStart, attempts, true, false, null);
                }
                LockSupport.parkNanos(pollIntervalNanos);
                if (Thread.currentThread().isInterrupted()) {
                    Thread.currentThread().interrupt();
                    throw new InterruptedException("Interrupted while polling Redis");
                }
            }
            throw new RedisTimeoutException("Redis did not reach the expected state within "
                    + Duration.ofNanos(timeoutNanos).toMillis() + " ms");
        } catch (Exception e) {
            return new TestResult(runId, Instant.now(), operation, mode, batchId, batchSize, id,
                    includeSqlSample ? sqlDuration : 0, System.nanoTime() - propagationStart,
                    System.nanoTime() - totalStart, attempts, false,
                    e instanceof RedisTimeoutException, e.getMessage());
        }
    }

    private TestResult failure(String runId, String operation, String mode, String batchId,
                               int batchSize, long id, long totalNanos, long sqlNanos, Exception e) {
        return new TestResult(runId, Instant.now(), operation, mode, batchId, batchSize, id,
                sqlNanos, 0, totalNanos, 0, false, false, message(e));
    }

    private static String message(Exception e) {
        String message = e.getMessage();
        return e.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }

    private static final class RedisTimeoutException extends Exception {
        private RedisTimeoutException(String message) { super(message); }
    }
}
