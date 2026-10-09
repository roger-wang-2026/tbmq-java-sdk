/*
 * Copyright 2026 Roger Wang
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.github.roger_wang_2026.tbmq.sdk;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    void rejectsBlankConnectionValues() {
        assertThrows(IllegalArgumentException.class, () -> TbmqOpenApiClient.create(" "));
        assertThrows(IllegalArgumentException.class,
                () -> TbmqOpenApiClient.create("http://localhost:8083", " "));
    }
}
