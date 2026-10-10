/*
 * Copyright 2026 Roger Wang
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.github.roger_wang_2026.tbmq.sdk;

import io.github.roger_wang_2026.tbmq.sdk.generated.ApiException;
import io.github.roger_wang_2026.tbmq.sdk.generated.model.RestPublishRequest;
import io.github.roger_wang_2026.tbmq.sdk.generated.model.RestPublishResponse;
import okhttp3.OkHttpClient;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/** Thread-safe, high-level TBMQ client with authentication recovery and typed publish results. */
public final class TbmqClient implements AutoCloseable {
    private final OkHttpClient http;
    private final boolean ownsHttpClient;
    private final TbmqOpenApiClient openApi;
    private final TbmqTokenManager tokens;
    private final ThreadLocal<TbmqTokenManager.Token> requestToken = new ThreadLocal<>();
    private final long budgetNanos;
    private final int maxPayloadBytes;
    private final Charset payloadCharset;
    private final LongSupplier nanoClock;
    private final long closeGraceNanos;
    private final Object lifecycle = new Object();
    private int activeCalls;
    private volatile boolean closed;

    private TbmqClient(Builder builder) {
        builder.validate();
        ownsHttpClient = builder.httpClient == null;
        http = ownsHttpClient ? new OkHttpClient.Builder()
                .connectTimeout(builder.timeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(builder.timeoutSeconds, TimeUnit.SECONDS)
                .callTimeout(builder.timeoutSeconds, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false).build() : builder.httpClient;
        budgetNanos = TimeUnit.SECONDS.toNanos(builder.requestBudgetSeconds);
        maxPayloadBytes = builder.maxPayloadBytes;
        payloadCharset = builder.payloadCharset;
        nanoClock = builder.nanoClock;
        closeGraceNanos = TimeUnit.SECONDS.toNanos(builder.closeGraceSeconds);
        tokens = new TbmqTokenManager(builder, TbmqOpenApiClient.create(builder.baseUrl, http));
        openApi = TbmqOpenApiClient.create(builder.baseUrl, http, this::requestAccessToken);
    }

    public static Builder builder(String baseUrl) { return new Builder(baseUrl); }
    /** Executes any generated API operation through the managed authentication and retry pipeline. */
    public <T> T execute(ApiCall<T> call) throws ApiException {
        if (call == null) { throw new IllegalArgumentException("call must not be null"); }
        beginCall();
        try { return executeOperation(() -> call.invoke(openApi)); }
        finally { endCall(); }
    }

    public TbmqPublishResult publish(TbmqPublishRequest request) {
        if (closed) { return result(TbmqPublishResult.Status.CLOSED, null, null,
                new ClientClosedException()); }
        try {
            validate(request);
            byte[] payload = encode(request.getPayload(), payloadCharset, maxPayloadBytes);
            RestPublishRequest body = new RestPublishRequest().topic(request.getTopic())
                    .payload(Base64.getEncoder().encodeToString(payload))
                    .payloadEncoding(RestPublishRequest.PayloadEncodingEnum.BASE64)
                    .qos(request.getQos()).retain(request.isRetain());
            RestPublishResponse response = execute(api -> api.mqttPublish().publish(body));
            Integer code = response == null ? null : response.getReasonCode();
            TbmqPublishResult.Status status = code == null ? TbmqPublishResult.Status.SERVICE_FAILED
                    : code == 0 ? TbmqPublishResult.Status.ACCEPTED
                    : code == 16 ? TbmqPublishResult.Status.NO_MATCHING_SUBSCRIBERS
                    : TbmqPublishResult.Status.BROKER_REJECTED;
            TbmqPublishResult.DeliveryState delivery = code == null
                    ? TbmqPublishResult.DeliveryState.UNKNOWN
                    : status == TbmqPublishResult.Status.ACCEPTED
                    || status == TbmqPublishResult.Status.NO_MATCHING_SUBSCRIBERS
                    ? TbmqPublishResult.DeliveryState.BROKER_ACCEPTED
                    : TbmqPublishResult.DeliveryState.NOT_SENT;
            return new TbmqPublishResult(status, code, null, null, 0, delivery);
        } catch (Exception failure) { return failure(failure); }
    }

    private <T> T executeOperation(Operation<T> operation) throws ApiException {
        long deadline = nanoClock.getAsLong() + budgetNanos;
        TbmqTokenManager.Token current = tokens.acquire();
        budget(deadline, null);
        requestToken.set(current);
        try {
            try { return operation.run(); }
            catch (ApiException rejection) {
                if (rejection.getCode() != 401 || !tokens.canRenew()) { throw rejection; }
                if (tokens.isFresh(current)) { tokens.reject(current, rejection); throw rejection; }
                budget(deadline, rejection);
                TbmqTokenManager.Token renewed = tokens.renew(current);
                budget(deadline, rejection);
                requestToken.set(renewed);
                try { return operation.run(); }
                catch (ApiException retryFailure) {
                    if (retryFailure.getCode() == 401) { tokens.reject(renewed, retryFailure); }
                    throw retryFailure;
                }
            }
        } finally { requestToken.remove(); }
    }

    private String requestAccessToken() {
        TbmqTokenManager.Token token = requestToken.get();
        if (token == null) { throw new IllegalStateException("TBMQ request is outside an authenticated operation"); }
        return token.accessToken;
    }

    private static void validate(TbmqPublishRequest value) {
        if (value == null || value.getTopic() == null || value.getTopic().isEmpty()
                || value.getTopic().indexOf('\u0000') >= 0 || value.getTopic().contains("+")
                || value.getTopic().contains("#") || value.getPayload() == null
                || value.getQos() < 0 || value.getQos() > 2) {
            throw new InvalidPublishRequestException("Publish requires a topic, payload and QoS 0..2");
        }
        if (encode(value.getTopic(), StandardCharsets.UTF_8, 65535).length > 65535) {
            throw new InvalidPublishRequestException("MQTT topic exceeds 65535 UTF-8 bytes");
        }
    }

    private static byte[] encode(String value, Charset charset, int limit) {
        if (value.length() > limit) { throw new InvalidPublishRequestException("Value exceeds configured byte limit"); }
        try {
            ByteBuffer encoded = charset.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(value));
            if (encoded.remaining() > limit) {
                throw new InvalidPublishRequestException("Value exceeds configured byte limit");
            }
            byte[] bytes = new byte[encoded.remaining()]; encoded.get(bytes); return bytes;
        } catch (CharacterCodingException failure) {
            throw new InvalidPublishRequestException("Value cannot be encoded with the configured charset", failure);
        }
    }

    private void budget(long deadline, ApiException rejection) {
        if (deadline - nanoClock.getAsLong() <= 0) { throw new BudgetExceeded(rejection); }
    }

    private static final class BudgetExceeded extends RuntimeException {
        BudgetExceeded(Throwable cause) { super("TBMQ publish budget exhausted before next attempt", cause); }
    }

    private static final class ClientClosedException extends IllegalStateException {
        ClientClosedException() { super("TBMQ client is closed"); }
    }

    private TbmqPublishResult failure(Throwable failure) {
        Integer status = TbmqClientErrors.httpStatus(failure);
        TbmqPublishResult.Status value;
        TbmqPublishResult.DeliveryState delivery = TbmqPublishResult.DeliveryState.NOT_SENT;
        long retryAfter = 0;
        if (failure instanceof ClientClosedException) {
            value = TbmqPublishResult.Status.CLOSED;
        } else if (failure instanceof InvalidPublishRequestException) {
            value = TbmqPublishResult.Status.INVALID_REQUEST;
        } else if (failure instanceof BudgetExceeded) {
            value = TbmqPublishResult.Status.BUDGET_EXHAUSTED;
        } else if (Thread.currentThread().isInterrupted()
                || TbmqTokenManager.interrupted(failure)) {
            value = TbmqPublishResult.Status.INTERRUPTED;
            if (!(failure instanceof TbmqTokenManager.AuthFailure)) {
                delivery = TbmqPublishResult.DeliveryState.UNKNOWN;
            }
        } else if (failure instanceof TbmqTokenManager.AuthFailure) {
            TbmqTokenManager.AuthFailure auth = (TbmqTokenManager.AuthFailure) failure;
            value = auth.backoff ? TbmqPublishResult.Status.AUTH_BACKOFF : TbmqPublishResult.Status.AUTHENTICATION_FAILED;
            retryAfter = auth.retryAfterMillis;
        } else if (status != null && status == 401) {
            value = TbmqPublishResult.Status.AUTHENTICATION_FAILED;
        } else if (status != null && status == 403) {
            value = TbmqPublishResult.Status.AUTHORIZATION_FAILED;
        } else if (status != null && status == 429) {
            value = TbmqPublishResult.Status.RATE_LIMITED; retryAfter = retryAfter(failure);
        } else if (TbmqClientErrors.has(failure, java.io.InterruptedIOException.class)
                || TbmqClientErrors.has(failure, java.util.concurrent.TimeoutException.class)) {
            value = TbmqPublishResult.Status.TIMEOUT;
            delivery = TbmqPublishResult.DeliveryState.UNKNOWN;
        } else if (status == null && TbmqClientErrors.has(failure, ApiException.class)) {
            value = TbmqPublishResult.Status.TRANSPORT_FAILED;
            delivery = TbmqClientErrors.connectionNotEstablished(failure)
                    ? TbmqPublishResult.DeliveryState.NOT_SENT : TbmqPublishResult.DeliveryState.UNKNOWN;
        } else if (status != null && (status == 400 || status == 413)) {
            value = TbmqPublishResult.Status.INVALID_REQUEST;
        } else {
            value = TbmqPublishResult.Status.SERVICE_FAILED;
            if (status == null || status >= 500) { delivery = TbmqPublishResult.DeliveryState.UNKNOWN; }
        }
        if (value == TbmqPublishResult.Status.AUTH_BACKOFF) { status = null; }
        return result(value, null, status, failure, retryAfter, delivery);
    }

    private static long retryAfter(Throwable failure) {
        for (Throwable item : TbmqClientErrors.chain(failure)) {
            if (!(item instanceof ApiException)) { continue; }
            Map<String, List<String>> headers = ((ApiException) item).getResponseHeaders();
            if (headers == null) { continue; }
            for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                if (!"Retry-After".equalsIgnoreCase(entry.getKey()) || entry.getValue() == null) { continue; }
                for (String text : entry.getValue()) {
                    if (text == null) { continue; }
                    try { return Math.max(0, Math.multiplyExact(Long.parseLong(text.trim()), 1000)); }
                    catch (RuntimeException ignored) {
                        try { return Math.max(0, ZonedDateTime.parse(text.trim(), DateTimeFormatter.RFC_1123_DATE_TIME)
                                .toInstant().toEpochMilli() - System.currentTimeMillis()); }
                        catch (RuntimeException invalid) { /* ignore malformed hint */ }
                    }
                }
            }
        }
        return 0;
    }

    private static TbmqPublishResult result(TbmqPublishResult.Status value, Integer reason,
                                             Integer http, Throwable failure) {
        return result(value, reason, http, failure, 0, TbmqPublishResult.DeliveryState.NOT_SENT);
    }
    private static TbmqPublishResult result(TbmqPublishResult.Status value, Integer reason,
                                             Integer http, Throwable failure, long retryAfter,
                                             TbmqPublishResult.DeliveryState delivery) {
        return new TbmqPublishResult(value, reason, http, failure, retryAfter, delivery);
    }

    @Override public void close() {
        synchronized (lifecycle) {
            closed = true;
            long deadline = System.nanoTime() + closeGraceNanos;
            while (ownsHttpClient && activeCalls > 0) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) { break; }
                try { TimeUnit.NANOSECONDS.timedWait(lifecycle, remaining); }
                catch (InterruptedException failure) { Thread.currentThread().interrupt(); break; }
            }
        }
        if (!ownsHttpClient) { return; }
        http.dispatcher().cancelAll();
        http.dispatcher().executorService().shutdown();
        http.connectionPool().evictAll();
    }

    private interface Operation<T> { T run() throws ApiException; }

    private void beginCall() {
        synchronized (lifecycle) {
            if (closed) { throw new ClientClosedException(); }
            activeCalls++;
        }
    }

    private void endCall() {
        synchronized (lifecycle) { activeCalls--; lifecycle.notifyAll(); }
    }

    @FunctionalInterface
    public interface ApiCall<T> { T invoke(TbmqOpenApiClient api) throws ApiException; }

    public static final class Builder {
        final String baseUrl;
        String username;
        String password;
        String accessToken;
        String refreshToken;
        OkHttpClient httpClient;
        int timeoutSeconds = 10;
        int requestBudgetSeconds = 30;
        int refreshAheadSeconds = 60;
        int authBackoffSeconds = 5;
        int maxPayloadBytes = 1024 * 1024;
        int closeGraceSeconds = 5;
        Charset payloadCharset = StandardCharsets.UTF_8;
        LongSupplier wallClock = System::currentTimeMillis;
        LongSupplier nanoClock = System::nanoTime;

        private Builder(String baseUrl) { this.baseUrl = baseUrl; }
        public Builder credentials(String user, String secret) { username = user; password = secret; return this; }
        public Builder accessToken(String value) { accessToken = value; return this; }
        public Builder refreshToken(String value) { refreshToken = value; return this; }
        public Builder httpClient(OkHttpClient value) { httpClient = value; return this; }
        public Builder timeout(Duration value) { timeoutSeconds = seconds(value, "timeout"); return this; }
        public Builder requestBudget(Duration value) { requestBudgetSeconds = seconds(value, "requestBudget"); return this; }
        public Builder refreshAhead(Duration value) { refreshAheadSeconds = nonNegativeSeconds(value, "refreshAhead"); return this; }
        public Builder authBackoff(Duration value) { authBackoffSeconds = seconds(value, "authBackoff"); return this; }
        public Builder maxPayloadBytes(int value) { maxPayloadBytes = value; return this; }
        public Builder payloadCharset(Charset value) { payloadCharset = value; return this; }
        public Builder closeGracePeriod(Duration value) { closeGraceSeconds = nonNegativeSeconds(value, "closeGracePeriod"); return this; }
        public TbmqClient build() { return new TbmqClient(this); }

        void validate() {
            if (baseUrl == null || baseUrl.trim().isEmpty()) { throw new IllegalArgumentException("baseUrl must not be blank"); }
            TbmqOpenApiClient.normalizeBaseUrl(baseUrl);
            if ((accessToken == null || accessToken.trim().isEmpty())
                    && (refreshToken == null || refreshToken.trim().isEmpty())
                    && (username == null || username.trim().isEmpty() || password == null || password.trim().isEmpty())) {
                throw new IllegalArgumentException("access token, refresh token or username/password is required");
            }
            if (requestBudgetSeconds < timeoutSeconds) { throw new IllegalArgumentException("requestBudget must be >= timeout"); }
            if (maxPayloadBytes <= 0 || payloadCharset == null || !payloadCharset.canEncode()) {
                throw new IllegalArgumentException("payload encoding configuration is invalid");
            }
        }
        private static int seconds(Duration value, String name) {
            int result = nonNegativeSeconds(value, name);
            if (result == 0) { throw new IllegalArgumentException(name + " must be positive"); }
            return result;
        }
        private static int nonNegativeSeconds(Duration value, String name) {
            if (value == null || value.isNegative() || value.getNano() != 0 || value.getSeconds() > Integer.MAX_VALUE) {
                throw new IllegalArgumentException(name + " must be a whole number of seconds");
            }
            return (int) value.getSeconds();
        }
    }
}
