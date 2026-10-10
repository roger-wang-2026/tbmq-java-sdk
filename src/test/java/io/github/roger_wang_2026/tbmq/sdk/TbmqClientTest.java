/*
 * Copyright 2026 Roger Wang
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.github.roger_wang_2026.tbmq.sdk;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

class TbmqClientTest {
    private MockWebServer server;

    @BeforeEach void start() throws IOException { server = new MockWebServer(); server.start(); }
    @AfterEach void stop() throws IOException { server.shutdown(); }

    @Test void logsInAndPublishesWithTypedResult() throws Exception {
        server.enqueue(json(200, "{\"token\":\"access-1\",\"refreshToken\":\"refresh-1\"}"));
        server.enqueue(json(200, "{\"reasonCode\":0}"));
        try (TbmqClient client = base().credentials("admin@example.com", "secret").build()) {
            TbmqPublishResult result = client.publish(request());
            assertEquals(TbmqPublishResult.Status.ACCEPTED, result.getStatus());
            assertEquals("/api/auth/login", server.takeRequest().getPath());
            assertEquals("Bearer access-1", server.takeRequest().getHeader("X-Authorization"));
        }
    }

    @Test void retriesOneUnauthorizedAttemptWithOneSharedCredentialFlow() throws Exception {
        server.enqueue(json(401, "{}"));
        server.enqueue(json(200, "{\"token\":\"access-2\"}"));
        server.enqueue(json(200, "{\"reasonCode\":16}"));
        try (TbmqClient client = base().accessToken("stale-token")
                .credentials("admin@example.com", "secret").build()) {
            assertEquals(TbmqPublishResult.Status.NO_MATCHING_SUBSCRIBERS, client.publish(request()).getStatus());
            assertEquals(3, server.getRequestCount());
        }
    }

    @Test void executesAnyGeneratedEndpointThroughManagedAuthentication() throws Exception {
        server.enqueue(json(200, "{\"token\":\"access-1\"}"));
        server.enqueue(json(200, "{}"));
        try (TbmqClient client = base().credentials("admin@example.com", "secret").build()) {
            assertNotNull(client.execute(api -> api.admins().getSecuritySettings()));
            server.takeRequest();
            assertEquals("Bearer access-1", server.takeRequest().getHeader("X-Authorization"));
        }
    }

    @Test void rejectsMalformedTopicBeforeHttp() {
        try (TbmqClient client = base().accessToken("token").build()) {
            TbmqPublishRequest invalid = TbmqPublishRequest.builder().topic("bad\uD800")
                    .payload("value").qos(0).build();
            assertEquals(TbmqPublishResult.Status.INVALID_REQUEST, client.publish(invalid).getStatus());
            assertEquals(0, server.getRequestCount());
        }
    }

    @Test void closingClientDoesNotOwnInjectedHttpResources() {
        OkHttpClient shared = new OkHttpClient();
        TbmqClient client = base().accessToken("token").httpClient(shared).build();
        client.close();
        assertFalse(shared.dispatcher().executorService().isShutdown());
        shared.dispatcher().executorService().shutdown();
        shared.connectionPool().evictAll();
    }

    @Test void validatesBudgetAgainstRequestTimeout() {
        assertThrows(IllegalArgumentException.class, () -> base().accessToken("token")
                .timeout(Duration.ofSeconds(10)).requestBudget(Duration.ofSeconds(9)).build());
    }

    private TbmqClient.Builder base() {
        return TbmqClient.builder(server.url("/").toString())
                .timeout(Duration.ofSeconds(2)).requestBudget(Duration.ofSeconds(6));
    }
    private TbmqPublishRequest request() {
        return TbmqPublishRequest.builder().topic("devices/demo/commands")
                .payload("{\"enabled\":true}").qos(1).build();
    }
    private MockResponse json(int status, String body) {
        return new MockResponse().setResponseCode(status).addHeader("Content-Type", "application/json").setBody(body);
    }
}
