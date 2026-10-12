package com.cibc.main.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisURI;
import io.lettuce.core.SslOptions;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Properties;

public final class Config {
    private static final String CONFIG_FILE = "/config/credentials.properties";
    private final Properties properties = new Properties();
    private final ClientOptions clientOptions;

    public Config() throws IOException {
        try (InputStream input = Config.class.getResourceAsStream(CONFIG_FILE)) {
            if (input == null) {
                throw new IOException("Could not find " + CONFIG_FILE
                        + ". Copy credentials.properties.example to credentials.properties.");
            }
            properties.load(input);
        }
        clientOptions = createRedisClientOptions();
    }

    private String required(String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required configuration property: " + key);
        }
        return value.trim();
    }

    public String getSourceServer() { return required("source.server"); }
    public int getSourcePort() { return Integer.parseInt(required("source.port")); }
    public String getSourceDatabase() { return required("source.databaseName"); }
    public boolean isSourceEncrypt() { return Boolean.parseBoolean(properties.getProperty("source.encrypt", "true")); }
    public boolean isSourceTrustServerCertificate() {
        return Boolean.parseBoolean(properties.getProperty("source.trustServerCertificate", "false"));
    }
    public String getTenantId() { return required("azure.tenantId"); }
    public String getClientId() { return required("azure.clientId"); }
    public String getClientSecret() { return required("azure.clientSecret"); }

    public String getTargetHost() { return required("target.host"); }
    public int getTargetPort() { return Integer.parseInt(required("target.port")); }
    public String getTargetUsername() { return properties.getProperty("target.username", "").trim(); }
    public String getTargetPassword() { return properties.getProperty("target.password", "").trim(); }
    public boolean isTargetSsl() { return Boolean.parseBoolean(properties.getProperty("target.ssl", "true")); }
    public ClientOptions getClientOptions() { return clientOptions; }

    public RedisURI getTargetUri() {
        RedisURI.Builder builder = RedisURI.builder()
                .withHost(getTargetHost())
                .withPort(getTargetPort());
        if (isTargetSsl()) builder.withSsl(true);

        String username = getTargetUsername();
        String password = getTargetPassword();
        if (!username.isBlank()) {
            if (password.isBlank()) throw new IllegalArgumentException("Redis password is required when username is configured.");
            builder.withAuthentication(username, password);
        } else if (!password.isBlank()) {
            builder.withPassword(password.toCharArray());
        }
        return builder.build();
    }

    public String getTableName() {
        String table = properties.getProperty("test.tableName", "dbo.RDI_TEST").trim();
        // Restrict this configurable SQL identifier to a simple schema.table form.
        if (!table.matches("[A-Za-z0-9_]+\\.[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("test.tableName must look like dbo.RDI_TEST");
        }
        return table;
    }
    public String getRedisKeyPrefix() { return properties.getProperty("test.redisKeyPrefix", "rdi_test:id:").trim(); }
    public long getRedisTimeoutMillis() { return Long.parseLong(properties.getProperty("test.redisTimeoutMillis", "10000")); }
    public long getPollIntervalMillis() { return Long.parseLong(properties.getProperty("test.pollIntervalMillis", "5")); }
    public int getRecordsPerBatch() { return Integer.parseInt(properties.getProperty("test.recordsPerBatch", "25")); }
    public int getSingleRecords() { return Integer.parseInt(properties.getProperty("test.singleRecords", "3")); }
    public int getBatchRecords() { return Integer.parseInt(properties.getProperty("test.batchRecords", "25")); }
    public String getOutputFile() { return properties.getProperty("test.outputFile", "output/rdi-performance.xlsx").trim(); }

    private ClientOptions createRedisClientOptions() {
        String trustStorePath = properties.getProperty("truststore.path", "").trim();
        String trustStorePassword = properties.getProperty("truststore.password", "");
        if (trustStorePath.isBlank()) return ClientOptions.builder().build();
        try {
            SslOptions sslOptions = SslOptions.builder()
                    .jdkSslProvider()
                    .truststore(Path.of(trustStorePath), trustStorePassword)
                    .build();
            return ClientOptions.builder().sslOptions(sslOptions).build();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to configure Redis SSL truststore", e);
        }
    }
}
