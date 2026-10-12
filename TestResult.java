package com.cibc.main.test;

import java.time.Instant;

public record TestResult(
        String runId,
        Instant timestamp,
        String operation,
        String mode,
        String batchId,
        int batchSize,
        long recordId,
        long sqlDurationNanos,
        long redisPropagationNanos,
        long totalDurationNanos,
        int pollAttempts,
        boolean success,
        boolean timeout,
        String error
) {
    public double sqlDurationMillis() { return sqlDurationNanos / 1_000_000.0; }
    public double redisPropagationMillis() { return redisPropagationNanos / 1_000_000.0; }
    public double totalDurationMillis() { return totalDurationNanos / 1_000_000.0; }
}
