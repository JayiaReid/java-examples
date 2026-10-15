
package com.cibc.main;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenCredential;
import com.azure.core.credential.TokenRequestContext;
import com.cibc.main.config.Config;
import com.microsoft.aad.msal4j.*;
import com.microsoft.sqlserver.jdbc.SQLServerAccessTokenCallback;
import com.microsoft.sqlserver.jdbc.SqlAuthenticationToken;
import io.lettuce.core.*;
import java.io.*;
import com.microsoft.sqlserver.jdbc.SQLServerDataSource;

import java.net.MalformedURLException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.time.*;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
//import com.azure.core.*;
import com.azure.identity.*;

public class ClientFactory {

    private final Config config;

    public ClientFactory(Config config) {
        this.config = config;
    }

    public RedisClient createRedisClient() throws FileNotFoundException {
        RedisURI uri = config.getRedisUri();
        RedisClient redisClient = RedisClient.create(uri);
        redisClient.setOptions(config.getClientOptions());
        return redisClient;
    }

    //

    public SQLServerDataSource createAzureSqlClient() {
        SQLServerDataSource client = new SQLServerDataSource();

        client.setServerName(config.getAzureServer());
        client.setPortNumber(config.getAzurePort());
        client.setDatabaseName(config.getAzureDatabase());
        client.setUser(config.getAzureUsername());
        client.setPassword(config.getAzurePassword());
        client.setEncrypt(config.getAzureEncrypt());
        client.setTrustServerCertificate(
            config.getAzureTrustServerCert()
        );

        return client;
    }

    //
    // private Date getDateFiveMinsFromNow() {
    //     OffsetDateTime fiveMinsFromNow =
    //         java.time.OffsetDateTime.now().plusMinutes(5);
    //     return Date.from(fiveMinsFromNow.toInstant());
    // }

    public SQLServerDataSource createAzureSqlClientUsingSpn()
            throws Exception {

        SQLServerAccessTokenCallback callback =
            new SQLServerAccessTokenCallback() {

                @Override
                public SqlAuthenticationToken getAccessToken(
                        String spn, String stsurl) {

                    Set<String> scope = new HashSet<>();
                    scope.add(config.getAzureSpn());

                    try {
                        ExecutorService executorService =
                            Executors.newSingleThreadExecutor();

                        File certFile =
                            new File(config.getAzureClientCertPath());

                        FileInputStream cfis =
                            new FileInputStream(certFile);

                        IClientCredential credential =
                            ClientCredentialFactory.createFromCertificate(
                                cfis,
                                config.getAzureClientCertPassword()
                            );

                        ConfidentialClientApplication clientApplication =
                            ConfidentialClientApplication
                                .builder(
                                    config.getAzureClientId(),
                                    credential
                                )
                                .executorService(executorService)
                                .authority(config.getAzureStsUrl())
                                .build();

                        CompletableFuture<IAuthenticationResult> future =
                            clientApplication.acquireToken(
                                ClientCredentialParameters
                                    .builder(scope)
                                    .build()
                            );

                        IAuthenticationResult authenticationResult =
                            future.get();

                        String accessToken =
                            authenticationResult.accessToken();

                        return new SqlAuthenticationToken(
                            accessToken,
                            authenticationResult.expiresOnDate().getTime()
                        );

                    } catch (
                            InterruptedException
                            | ExecutionException
                            | CertificateException
                            | UnrecoverableKeyException
                            | NoSuchAlgorithmException
                            | KeyStoreException
                            | NoSuchProviderException
                            | IOException e) {

                        e.printStackTrace();
                    }

                    return null;
                }
            };

        SQLServerDataSource client = new SQLServerDataSource();

        client.setServerName(config.getAzureServer());
        client.setPortNumber(config.getAzurePort());
        client.setDatabaseName(config.getAzureDatabase());

        client.setEncrypt(config.getAzureEncrypt());
        client.setTrustServerCertificate(
            config.getAzureTrustServerCert()
        );

        // SPN
        client.setAccessTokenCallback(callback);
        client.setAccessToken(
            callback.getAccessToken(
                config.getAzureSpn(),
                config.getAzureStsUrl()
            )
        );

        client.getConnection();

        return client;
    }
}
