/*
 * Copyright 2026 Roger Wang
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.github.roger_wang_2026.tbmq.sdk;

import io.github.roger_wang_2026.tbmq.sdk.generated.ApiClient;
import io.github.roger_wang_2026.tbmq.sdk.generated.api.AdminControllerApi;
import io.github.roger_wang_2026.tbmq.sdk.generated.api.AppControllerApi;
import io.github.roger_wang_2026.tbmq.sdk.generated.api.AppSharedSubscriptionControllerApi;
import io.github.roger_wang_2026.tbmq.sdk.generated.api.AuthControllerApi;
import io.github.roger_wang_2026.tbmq.sdk.generated.api.BlockedClientControllerApi;
import io.github.roger_wang_2026.tbmq.sdk.generated.api.ClientSessionControllerApi;
import io.github.roger_wang_2026.tbmq.sdk.generated.api.DeviceConnectivityControllerApi;
import io.github.roger_wang_2026.tbmq.sdk.generated.api.EventControllerApi;
import io.github.roger_wang_2026.tbmq.sdk.generated.api.IntegrationControllerApi;
import io.github.roger_wang_2026.tbmq.sdk.generated.api.LoginEndpointApi;
import io.github.roger_wang_2026.tbmq.sdk.generated.api.MqttAuthProviderControllerApi;
import io.github.roger_wang_2026.tbmq.sdk.generated.api.MqttClientCredentialsControllerApi;
import io.github.roger_wang_2026.tbmq.sdk.generated.api.MqttPublishControllerApi;
import io.github.roger_wang_2026.tbmq.sdk.generated.api.NodeDrainControllerApi;
import io.github.roger_wang_2026.tbmq.sdk.generated.api.RetainedMsgControllerApi;
import io.github.roger_wang_2026.tbmq.sdk.generated.api.SubscriptionControllerApi;
import io.github.roger_wang_2026.tbmq.sdk.generated.api.TimeseriesControllerApi;
import io.github.roger_wang_2026.tbmq.sdk.generated.api.UnauthorizedClientControllerApi;
import io.github.roger_wang_2026.tbmq.sdk.generated.api.WebSocketConnectionControllerApi;
import io.github.roger_wang_2026.tbmq.sdk.generated.api.WebSocketSubscriptionControllerApi;
import okhttp3.OkHttpClient;
import okhttp3.Request;

/**
 * Entry point for the complete, OpenAPI-generated TBMQ 2.4.1 client.
 *
 * <p>The generated API is kept under the {@code generated} package and matches TBMQ 2.4.1.</p>
 */
public final class TbmqOpenApiClient {

    private static final String AUTHORIZATION_HEADER = "X-Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final ApiClient apiClient;

    private TbmqOpenApiClient(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /** Creates an unauthenticated client, suitable for the login endpoints. */
    public static TbmqOpenApiClient create(String baseUrl) {
        return new TbmqOpenApiClient(new ApiClient().setBasePath(normalizeBaseUrl(baseUrl)));
    }

    /** Creates a client that sends the supplied TBMQ JWT access token on every request. */
    public static TbmqOpenApiClient create(String baseUrl, String accessToken) {
        return create(baseUrl).accessToken(accessToken);
    }

    /**
     * Creates an unauthenticated client backed by the supplied shared OkHttp client.
     *
     * <p>The exact {@code OkHttpClient} instance is used, so its dispatcher, connection pool, proxy, TLS,
     * timeout and observability configuration are preserved.</p>
     */
    public static TbmqOpenApiClient create(String baseUrl, OkHttpClient httpClient) {
        if (httpClient == null) {
            throw new IllegalArgumentException("httpClient must not be null");
        }
        return new TbmqOpenApiClient(
                new ApiClient(httpClient).setBasePath(normalizeBaseUrl(baseUrl)));
    }

    /** Creates a fixed-token client backed by the supplied shared OkHttp client. */
    public static TbmqOpenApiClient create(
            String baseUrl, String accessToken, OkHttpClient httpClient) {
        return create(baseUrl, httpClient).accessToken(accessToken);
    }

    /**
     * Creates a high-throughput client that resolves the access token for every HTTP attempt.
     *
     * <p>The derived OkHttp client shares the supplied client's dispatcher and connection pool. Updating the
     * provider therefore changes authentication without rebuilding the client or dropping pooled connections.</p>
     */
    public static TbmqOpenApiClient create(
            String baseUrl, OkHttpClient httpClient, AccessTokenProvider accessTokenProvider) {
        if (httpClient == null) {
            throw new IllegalArgumentException("httpClient must not be null");
        }
        if (accessTokenProvider == null) {
            throw new IllegalArgumentException("accessTokenProvider must not be null");
        }

        OkHttpClient authenticatedHttpClient = httpClient.newBuilder()
                .addInterceptor(chain -> {
                    String accessToken = accessTokenProvider.getAccessToken();
                    if (accessToken == null || accessToken.trim().isEmpty()) {
                        throw new IllegalStateException("accessTokenProvider returned a blank token");
                    }
                    Request authenticatedRequest = chain.request().newBuilder()
                            .header(AUTHORIZATION_HEADER, BEARER_PREFIX + accessToken.trim())
                            .build();
                    return chain.proceed(authenticatedRequest);
                })
                .build();

        return create(baseUrl, authenticatedHttpClient);
    }

    /** Replaces the JWT access token used by subsequent requests. */
    public TbmqOpenApiClient accessToken(String accessToken) {
        if (accessToken == null || accessToken.trim().isEmpty()) {
            throw new IllegalArgumentException("accessToken must not be blank");
        }
        apiClient.setApiKey(BEARER_PREFIX + accessToken.trim());
        return this;
    }

    public ApiClient apiClient() {
        return apiClient;
    }

    private static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("baseUrl must not be blank");
        }
        String normalizedBaseUrl = baseUrl.trim();
        while (normalizedBaseUrl.endsWith("/")) {
            normalizedBaseUrl = normalizedBaseUrl.substring(0, normalizedBaseUrl.length() - 1);
        }
        return normalizedBaseUrl;
    }

    public AdminControllerApi admins() { return new AdminControllerApi(apiClient); }
    public AppControllerApi apps() { return new AppControllerApi(apiClient); }
    public AppSharedSubscriptionControllerApi appSharedSubscriptions() { return new AppSharedSubscriptionControllerApi(apiClient); }
    public AuthControllerApi auth() { return new AuthControllerApi(apiClient); }
    public BlockedClientControllerApi blockedClients() { return new BlockedClientControllerApi(apiClient); }
    public ClientSessionControllerApi clientSessions() { return new ClientSessionControllerApi(apiClient); }
    public DeviceConnectivityControllerApi deviceConnectivity() { return new DeviceConnectivityControllerApi(apiClient); }
    public EventControllerApi events() { return new EventControllerApi(apiClient); }
    public IntegrationControllerApi integrations() { return new IntegrationControllerApi(apiClient); }
    public LoginEndpointApi login() { return new LoginEndpointApi(apiClient); }
    public MqttAuthProviderControllerApi mqttAuthProviders() { return new MqttAuthProviderControllerApi(apiClient); }
    public MqttClientCredentialsControllerApi mqttClientCredentials() { return new MqttClientCredentialsControllerApi(apiClient); }
    public MqttPublishControllerApi mqttPublish() { return new MqttPublishControllerApi(apiClient); }
    public NodeDrainControllerApi nodeDrain() { return new NodeDrainControllerApi(apiClient); }
    public RetainedMsgControllerApi retainedMessages() { return new RetainedMsgControllerApi(apiClient); }
    public SubscriptionControllerApi subscriptions() { return new SubscriptionControllerApi(apiClient); }
    public TimeseriesControllerApi timeseries() { return new TimeseriesControllerApi(apiClient); }
    public UnauthorizedClientControllerApi unauthorizedClients() { return new UnauthorizedClientControllerApi(apiClient); }
    public WebSocketConnectionControllerApi webSocketConnections() { return new WebSocketConnectionControllerApi(apiClient); }
    public WebSocketSubscriptionControllerApi webSocketSubscriptions() { return new WebSocketSubscriptionControllerApi(apiClient); }
}
