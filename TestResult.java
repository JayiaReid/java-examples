package com.cibc.main;

import java.time.Instant;

public record TestResult(
        String runId,
        Instant timestamp,
        String operation,
        String mode,
        Long recordId,
        int batchSize,
        double sqlDurationMillis,
        double redisPropagationMillis,
        double totalDurationMillis,
        boolean success,
        boolean timeout,
        String error
) {
}
