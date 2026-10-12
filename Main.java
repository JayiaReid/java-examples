package com.cibc.main;

import com.cibc.main.client.ClientFactory;
import com.cibc.main.config.Config;
import com.cibc.main.redis.RedisClientWrapper;
import com.cibc.main.reporting.ExcelReportWriter;
import com.cibc.main.sql.SqlServerClientWrapper;
import com.cibc.main.test.TestResult;
import com.cibc.main.test.TestRunner;
import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
import io.lettuce.core.RedisClient;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class Main {
    private Main() {}

    public static void main(String[] args) {
        RedisClient redisClient = null;
        RedisClientWrapper redisWrapper = null;
        List<TestResult> results = new ArrayList<>();
        Config config = null;

        try {
            config = new Config();
            ClientFactory factory = new ClientFactory(config);

            redisClient = factory.createTargetClient();
            redisWrapper = new RedisClientWrapper(redisClient);

            SQLServerDataSource sqlDataSource = factory.createSourceClient();
            SqlServerClientWrapper sqlWrapper =
                    new SqlServerClientWrapper(sqlDataSource, config.getTableName());

            System.out.println("Testing Redis connection...");
            if (!redisWrapper.testConnection()) throw new IllegalStateException("Redis connection failed.");
            System.out.println("Redis connection successful.");

            System.out.println("Testing Azure SQL connection...");
            if (!sqlWrapper.testConnection()) throw new IllegalStateException("Azure SQL connection failed.");
            System.out.println("Azure SQL connection successful.");

            TestRunner runner = new TestRunner(sqlWrapper, redisWrapper, config);
            String runId = UUID.randomUUID().toString();
            long baseId = System.currentTimeMillis() * 1000L;

            // Single-record INSERT -> UPDATE -> DELETE cycles.
            for (int i = 0; i < config.getSingleRecords(); i++) {
                results.addAll(runner.runSingleCrudCycle(baseId + i, runId));
            }

            // Batch INSERT -> UPDATE -> DELETE cycles on the same IDs.
            int batchCount = config.getBatchRecords();
            List<Long> batchIds = new ArrayList<>(batchCount);
            for (int i = 0; i < batchCount; i++) batchIds.add(baseId + 100_000L + i);
            results.addAll(runner.runBatchCrudCycle(batchIds, runId));

            Path output = Path.of(config.getOutputFile());
            new ExcelReportWriter().write(output, results);

            long successful = results.stream().filter(TestResult::success).count();
            System.out.println("Test run finished. Measurements: " + results.size()
                    + ", successful: " + successful + ", failed: " + (results.size() - successful));
            System.out.println("Excel report: " + output.toAbsolutePath());
        } catch (Exception e) {
            System.err.println("Performance test failed: " + e.getMessage());
            e.printStackTrace(System.err);
            if (config != null && !results.isEmpty()) {
                try {
                    Path output = Path.of(config.getOutputFile());
                    new ExcelReportWriter().write(output, results);
                    System.err.println("Partial report written to: " + output.toAbsolutePath());
                } catch (Exception reportError) {
                    System.err.println("Could not write partial report: " + reportError.getMessage());
                }
            }
        } finally {
            if (redisWrapper != null) {
                redisWrapper.close();
            } else if (redisClient != null) {
                redisClient.shutdown();
            }
        }
    }
}
