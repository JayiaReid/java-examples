
import com.microsoft.sqlserver.jdbc.SQLServerXADataSource;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.*;
import io.netty.handler.ssl.*;
import java.io.*;
import java.util.Properties;

public class Config {

    private final Properties properties;
    private final String redisHost;
    private final int redisPort;
    private final String redisUsername;
    private final String redisPassword;

    // Azure SQL
    private final String azureServerName;
    private final int azurePort;
    private final String azureDatabase;
    private final String azureUsername;
    private final String azurePassword;
    private final String azureEncrypt;
    private final boolean azureTrustServerCert;

    // SPN
    private final String azureSpn;
    private final String azureStsUrl;
    private final String azureClientId;
    private final String azureClientCertPath;
    private final String azureClientCertPassword;
    private final String azureTruststore;
    private final String azureTrustStorePassword;

    private final boolean enableSsl;
    private final String truststorePassword;
    private final String trustStorePath;

    public final ClientOptions clientOptions;

    private final String CONFIG_FILE = "/config/credentials.properties";

    public Config() throws Exception {
        properties = new Properties();

        try (InputStream input =
                     getClass().getResourceAsStream(CONFIG_FILE)) {

            if (input == null) {
                throw new IOException(
                    "Could not find configuration file: " + CONFIG_FILE
                );
            }

            properties.load(input);
        }

        // Redis
        redisHost = properties.getProperty("redis.host");
        redisPort = Integer.parseInt(
            properties.getProperty("redis.port")
        );
        redisUsername = properties.getProperty("redis.username");
        redisPassword = properties.getProperty("redis.password");

        // Azure SQL
        azureServerName = properties.getProperty("azure.serverName");
        azurePort = Integer.parseInt(
            properties.getProperty("azure.port")
        );
        azureDatabase = properties.getProperty("azure.databaseName");

        azureUsername = properties.getProperty("azure.username");
        azurePassword = properties.getProperty("azure.password");
        azureEncrypt = properties.getProperty("azure.setEncrypt");
        azureTrustServerCert = Boolean.parseBoolean(
            properties.getProperty("azure.setTrustServerCert")
        );

        // SPN
        azureSpn = properties.getProperty("azure.spn");
        azureStsUrl = properties.getProperty("azure.stsUrl");
        azureClientId = properties.getProperty("azure.clientId");
        azureClientCertPath =
            properties.getProperty("azure.clientCertPath");
        azureClientCertPassword =
            properties.getProperty("azure.clientCertPassword");
        azureTruststore = properties.getProperty("azure.truststore");
        azureTrustStorePassword =
            properties.getProperty("azure.truststorePassword");

        trustStorePath = properties.getProperty("truststore.path");
        truststorePassword = properties.getProperty("truststore.password");

        enableSsl = Boolean.parseBoolean(
            properties.getProperty("enable.ssl")
        );

        clientOptions = setUpClientOptions();
    }

    private String required(String property) {
        String value = properties.getProperty(property);

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                "Missing required configuration property: " + property
            );
        }

        return value;
    }

    // Redis client
    private ClientOptions setUpClientOptions()
            throws FileNotFoundException {

        SslOptions sslOptions = SslOptions.builder()
            .jdkSslProvider()
            .truststore(new File(trustStorePath), truststorePassword)
            .build();

        return ClientOptions.builder()
            .sslOptions(sslOptions)
            .build();
    }

    public ClientOptions getClientOptions() {
        return clientOptions;
    }

    public RedisURI getRedisUri() {
        return RedisURI.Builder
            .redis(redisHost, redisPort)
            .withSsl(enableSsl)
            .withAuthentication(redisUsername, redisPassword)
            .build();
    }

    // Azure getters
    public String getAzureServer() {
        return azureServerName;
    }

    public Integer getAzurePort() {
        return azurePort;
    }

    public String getAzureDatabase() {
        return azureDatabase;
    }

    public String getAzureUsername() {
        return azureUsername;
    }

    public String getAzurePassword() {
        return azurePassword;
    }

    public String getAzureEncrypt() {
        return azureEncrypt;
    }

    public Boolean getAzureTrustServerCert() {
        return azureTrustServerCert;
    }

    public String getAzureSpn() {
        return azureSpn;
    }

    public String getAzureClientId() {
        return azureClientId;
    }

    public String getAzureStsUrl() {
        return azureStsUrl;
    }

    public String getAzureClientCertPath() {
        return azureClientCertPath;
    }

    public String getAzureClientCertPassword() {
        return azureClientCertPassword;
    }

    public String getAzureTruststore() {
        return azureTruststore;
    }

    public String getAzureTrustStorePassword() {
        return azureTrustStorePassword;
    }
}
