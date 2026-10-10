/*
 * Copyright 2026 Roger Wang
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.github.roger_wang_2026.tbmq.sdk;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TbmqOpenApiClientTest {

    @Test
    void sendsTbmqAuthorizationHeaderAndNormalizesBaseUrl() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("{}"));

            TbmqOpenApiClient client = TbmqOpenApiClient.create(server.url("/").toString(), "token-123");
            assertNotNull(client.auth().getUser());

            RecordedRequest request = server.takeRequest();
            assertEquals("/api/auth/user", request.getPath());
            assertEquals("Bearer token-123", request.getHeader("X-Authorization"));
        }
    }

    @Test
    void exposesEveryGeneratedControllerFromOneClient() {
        TbmqOpenApiClient client = TbmqOpenApiClient.create("http://localhost:8083");

        assertNotNull(client.admins());
        assertNotNull(client.apps());
        assertNotNull(client.appSharedSubscriptions());
        assertNotNull(client.auth());
        assertNotNull(client.blockedClients());
        assertNotNull(client.clientSessions());
        assertNotNull(client.deviceConnectivity());
        assertNotNull(client.events());
        assertNotNull(client.integrations());
        assertNotNull(client.login());
        assertNotNull(client.mqttAuthProviders());
        assertNotNull(client.mqttClientCredentials());
        assertNotNull(client.mqttPublish());
        assertNotNull(client.nodeDrain());
        assertNotNull(client.retainedMessages());
        assertNotNull(client.subscriptions());
        assertNotNull(client.timeseries());
        assertNotNull(client.unauthorizedClients());
        assertNotNull(client.webSocketConnections());
        assertNotNull(client.webSocketSubscriptions());
    }

    @Test
    void usesTheSuppliedOkHttpClientWithoutReplacingItsConnectionPool() {
        OkHttpClient httpClient = new OkHttpClient.Builder().build();

        TbmqOpenApiClient client = TbmqOpenApiClient.create("http://localhost:8083", httpClient);

        assertSame(httpClient, client.apiClient().getHttpClient());
        assertSame(httpClient.connectionPool(), client.apiClient().getHttpClient().connectionPool());
        assertSame(httpClient.dispatcher(), client.apiClient().getHttpClient().dispatcher());
    }

    @Test
    void resolvesDynamicAccessTokenForEveryRequestAndKeepsSharedResources() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(jsonResponse("{}"));
            server.enqueue(jsonResponse("{}"));

            AtomicReference<String> accessToken = new AtomicReference<>("first-token");
            OkHttpClient sharedHttpClient = new OkHttpClient.Builder().build();
            TbmqOpenApiClient client = TbmqOpenApiClient.create(
                    server.url("/").toString(), sharedHttpClient, accessToken::get);

            client.auth().getUser();
            accessToken.set("second-token");
            client.auth().getUser();

            assertEquals("Bearer first-token",
                    server.takeRequest().getHeader("X-Authorization"));
            assertEquals("Bearer second-token",
                    server.takeRequest().getHeader("X-Authorization"));
            assertSame(sharedHttpClient.connectionPool(),
                    client.apiClient().getHttpClient().connectionPool());
            assertSame(sharedHttpClient.dispatcher(),
                    client.apiClient().getHttpClient().dispatcher());
        }
    }

    @Test
    void rejectsBlankConnectionValues() {
        assertThrows(IllegalArgumentException.class, () -> TbmqOpenApiClient.create(" "));
        assertThrows(IllegalArgumentException.class, () -> TbmqOpenApiClient.create("not-a-url"));
        assertThrows(IllegalArgumentException.class, () -> TbmqOpenApiClient.create("ftp://example.com"));
        assertThrows(IllegalArgumentException.class,
                () -> TbmqOpenApiClient.create("http://localhost:8083", " "));
        assertThrows(IllegalArgumentException.class,
                () -> TbmqOpenApiClient.create("http://localhost:8083", (OkHttpClient) null));
        assertThrows(IllegalArgumentException.class,
                () -> TbmqOpenApiClient.create(
                        "http://localhost:8083", new OkHttpClient(), (AccessTokenProvider) null));
    }

    private MockResponse jsonResponse(String body) {
        return new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }
}
