
package com.cibc.main;

import com.cibc.main.config.Config;
import com.microsoft.sqlserver.jdbc.SQLServerDataSource;

import java.nio.file.Path;
import java.util.List;

public class Main {
    public static void main(String[] args) throws Exception {
        // Config config = null;
        RedisClientWrapper redisClientWrapper = null;

        try {
            // config
            Config config = new Config();

            // client factory
            ClientFactory factory = new ClientFactory(config);
            redisClientWrapper = new RedisClientWrapper(factory.createRedisClient());
            SQLServerDataSource sqlDataSource = factory.createAzureSqlClient();
            SqlServerClientWrapper sqlServerClientWrapper = new SqlServerClientWrapper(sqlDataSource);

            // test connections
            System.out.println("Testing Redis Connection...");
            if (!redisClientWrapper.testConnection()) {
                throw new IllegalStateException("Redis Connection Failed");
            }
            System.out.println("Redis connection successful.");

            System.out.println("Testing Azure SQL Connection...");
            if (!sqlServerClientWrapper.testConnection()) {
                throw new IllegalStateException("Azure SQL Connection Failed");
            }
            System.out.println("Azure SQL connection successful.");

            // Test Runner
            TestRunner runner = new TestRunner(sqlServerClientWrapper, redisClientWrapper);

            Long id = null;
            Path output = Path.of("performance_results/performance_results.xlsx");
            ExcelReportWriter writer = new ExcelReportWriter();

            List<TestResult> results = runner.runSingles();

            for (TestResult result : results) {
                System.out.println(result);
            }

            writer.write(output, results);

        } catch (Exception e) {
            System.err.println("Performance test failed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (redisClientWrapper != null) {
                redisClientWrapper.close();
            }
        }
    }
}
