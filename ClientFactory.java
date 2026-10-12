package com.cibc.main.client;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenCredential;
import com.azure.core.util.TokenRequestContext;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.cibc.main.config.Config;
import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
import io.lettuce.core.RedisClient;

import java.time.OffsetDateTime;

public final class ClientFactory {
    private final Config config;

    public ClientFactory(Config config) { this.config = config; }

    // Preserve the V1 Lettuce client-creation pattern.
    public RedisClient createTargetClient() {
        RedisClient redisClient = RedisClient.create(config.getTargetUri());
        redisClient.setOptions(config.getClientOptions());
        return redisClient;
    }

    /**
     * Draft authentication implementation only. This currently uses a client secret.
     * Replace with the approved internal token retriever/certificate flow when confirmed.
     */
    public SQLServerDataSource createSourceClient() {
        SQLServerDataSource dataSource = new SQLServerDataSource();
        dataSource.setServerName(config.getSourceServer());
        dataSource.setPortNumber(config.getSourcePort());
        dataSource.setDatabaseName(config.getSourceDatabase());
        dataSource.setEncrypt(config.isSourceEncrypt());
        dataSource.setTrustServerCertificate(config.isSourceTrustServerCertificate());
        dataSource.setApplicationName("RDI-Performance-Testing");

        TokenCredential credential = new ClientSecretCredentialBuilder()
                .tenantId(config.getTenantId())
                .clientId(config.getClientId())
                .clientSecret(config.getClientSecret())
                .build();

        // SQLServerDataSource access-token callback APIs vary by JDBC driver version.
        // To avoid pretending this unverified callback compiles in every driver version,
        // use the driver's built-in Entra service-principal mode for this draft.
        // The SQL wrapper/test/report code is independent of this authentication choice.
        dataSource.setAuthentication("ActiveDirectoryServicePrincipal");
        dataSource.setUser(config.getClientId());
        dataSource.setPassword(config.getClientSecret());
        return dataSource;
    }
}
