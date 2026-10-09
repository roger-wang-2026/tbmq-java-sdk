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

/**
 * Entry point for the complete, OpenAPI-generated TBMQ 2.4.1 client.
 *
 * <p>The generated API is kept under the {@code generated} package and matches TBMQ 2.4.1.</p>
 */
public final class TbmqOpenApiClient {

    private static final String BEARER_PREFIX = "Bearer ";

    private final ApiClient apiClient;

    private TbmqOpenApiClient(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /** Creates an unauthenticated client, suitable for the login endpoints. */
    public static TbmqOpenApiClient create(String baseUrl) {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("baseUrl must not be blank");
        }
        String normalizedBaseUrl = baseUrl.trim();
        while (normalizedBaseUrl.endsWith("/")) {
            normalizedBaseUrl = normalizedBaseUrl.substring(0, normalizedBaseUrl.length() - 1);
        }
        return new TbmqOpenApiClient(new ApiClient().setBasePath(normalizedBaseUrl));
    }

    /** Creates a client that sends the supplied TBMQ JWT access token on every request. */
    public static TbmqOpenApiClient create(String baseUrl, String accessToken) {
        return create(baseUrl).accessToken(accessToken);
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
