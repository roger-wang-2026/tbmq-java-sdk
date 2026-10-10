/*
 * Copyright 2026 Roger Wang
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.github.roger_wang_2026.tbmq.sdk;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.roger_wang_2026.tbmq.sdk.generated.ApiException;
import io.github.roger_wang_2026.tbmq.sdk.generated.model.ApiAuthTokenPostRequest;
import io.github.roger_wang_2026.tbmq.sdk.generated.model.LoginRequest;
import io.github.roger_wang_2026.tbmq.sdk.generated.model.LoginResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Single-flight access-token renewal. Network I/O never runs while holding the coordination lock. */
final class TbmqTokenManager {
    static final class Token {
        final String accessToken;
        final String refreshToken;
        final long expiresAt;
        final long issuedAtNanos;
        Token(String accessToken, String refreshToken, long issuedAtNanos) {
            this.accessToken = headerToken(accessToken, "access token");
            this.refreshToken = headerToken(refreshToken, "refresh token");
            this.issuedAtNanos = issuedAtNanos;
            this.expiresAt = expiration(this.accessToken);
        }
    }

    static final class AuthFailure extends RuntimeException {
        final boolean backoff;
        final long retryAfterMillis;
        AuthFailure(String message, Throwable cause, boolean backoff, long retryAfterMillis) {
            super(message, cause); this.backoff = backoff; this.retryAfterMillis = Math.max(0, retryAfterMillis);
        }
    }

    private final Object lock = new Object();
    private final String username;
    private final String password;
    private final long refreshAheadMillis;
    private final long backoffNanos;
    private final int waitSeconds;
    private final TbmqOpenApiClient authClient;
    private volatile Token current;
    private CompletableFuture<Token> renewal;
    private Throwable lastFailure;
    private long retryAfterNanos;
    private Token rejected;
    private ApiException rejection;
    private long rejectedUntilNanos;

    TbmqTokenManager(TbmqClient.Builder config, TbmqOpenApiClient authClient) {
        username = config.username;
        password = config.password;
        refreshAheadMillis = TimeUnit.SECONDS.toMillis(config.refreshAheadSeconds);
        backoffNanos = TimeUnit.SECONDS.toNanos(config.authBackoffSeconds);
        waitSeconds = config.timeoutSeconds * 2 + 5;
        this.authClient = authClient;
        current = new Token(config.accessToken, config.refreshToken, Long.MIN_VALUE);
    }

    boolean canRenew() { return text(current.refreshToken) || text(username) && text(password); }

    Token acquire() {
        Token token;
        boolean previouslyRejected;
        synchronized (lock) {
            token = current;
            checkRejected(token);
            previouslyRejected = rejected == token;
        }
        if (previouslyRejected) { return renew(token, false); }
        long now = System.currentTimeMillis();
        if (text(token.accessToken) && (!canRenew() || token.expiresAt == Long.MAX_VALUE
                || token.expiresAt - now > refreshAheadMillis)) { return token; }
        try { return renew(token, text(token.accessToken) && token.expiresAt > now); }
        catch (AuthFailure failure) {
            if (!Thread.currentThread().isInterrupted() && !interrupted(failure)
                    && token.expiresAt > System.currentTimeMillis()) { return token; }
            throw failure;
        }
    }

    boolean isFresh(Token token) {
        long age = System.nanoTime() - token.issuedAtNanos;
        return token.issuedAtNanos != Long.MIN_VALUE && age >= 0 && age < TimeUnit.SECONDS.toNanos(5);
    }

    void reject(Token token, ApiException failure) {
        synchronized (lock) {
            if (current != token) { return; }
            rejected = token; rejection = failure; rejectedUntilNanos = System.nanoTime() + backoffNanos;
        }
    }

    Token renew(Token previous) { return renew(previous, false); }

    private Token renew(Token previous, boolean allowStale) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(waitSeconds);
        for (;;) {
            CompletableFuture<Token> pending;
            boolean leader = false;
            synchronized (lock) {
                if (current != previous) { checkRejected(current); return current; }
                checkRejected(previous);
                if (renewal != null) {
                    if (allowStale && previous.expiresAt > System.currentTimeMillis()) { return previous; }
                    pending = renewal;
                } else {
                    long remaining = retryAfterNanos - System.nanoTime();
                    if (lastFailure != null && remaining > 0) {
                        throw new AuthFailure("TBMQ authentication is in backoff", lastFailure, true, millis(remaining));
                    }
                    pending = renewal = new CompletableFuture<>(); leader = true;
                }
            }
            if (leader) {
                try {
                    Token next = authenticate(previous);
                    synchronized (lock) {
                        current = next; lastFailure = null; rejected = null; rejection = null;
                        rejectedUntilNanos = 0; renewal = null; pending.complete(next);
                    }
                    return next;
                } catch (Throwable failure) {
                    boolean callerInterrupted = interrupted(failure) || Thread.currentThread().isInterrupted();
                    synchronized (lock) {
                        lastFailure = callerInterrupted ? null : failure;
                        retryAfterNanos = callerInterrupted ? 0 : System.nanoTime() + backoffNanos;
                        renewal = null;
                        pending.completeExceptionally(callerInterrupted ? new LeaderInterrupted(failure) : failure);
                    }
                    throw authFailure(failure);
                }
            }
            try {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) { throw new TimeoutException(); }
                return pending.get(remaining, TimeUnit.NANOSECONDS);
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new AuthFailure("Interrupted while awaiting TBMQ authentication", failure, false, 0);
            } catch (TimeoutException failure) {
                throw new AuthFailure("Timed out awaiting TBMQ authentication", failure, false, 0);
            } catch (ExecutionException failure) {
                if (failure.getCause() instanceof LeaderInterrupted) { continue; }
                throw authFailure(failure.getCause());
            }
        }
    }

    private Token authenticate(Token previous) throws ApiException {
        if (text(previous.refreshToken)) {
            try {
                LoginResponse response = authClient.login().apiAuthTokenPost(
                        new ApiAuthTokenPostRequest().refreshToken(previous.refreshToken));
                return response(response, previous.refreshToken);
            } catch (ApiException failure) {
                if ((failure.getCode() != 400 && failure.getCode() != 401) || !text(username) || !text(password)) {
                    throw failure;
                }
            }
        }
        if (!text(username) || !text(password)) { throw new IllegalStateException("No renewable TBMQ credentials"); }
        return response(authClient.login().apiAuthLoginPost(
                new LoginRequest().username(username).password(password)), null);
    }

    private Token response(LoginResponse response, String previousRefresh) {
        if (response == null || !text(response.getToken())) {
            throw new IllegalStateException("TBMQ authentication response contains no access token");
        }
        return new Token(response.getToken(), text(response.getRefreshToken()) ? response.getRefreshToken() : previousRefresh,
                System.nanoTime());
    }

    private void checkRejected(Token token) {
        long remaining = rejectedUntilNanos - System.nanoTime();
        if (rejected == token && remaining > 0) {
            throw new AuthFailure("TBMQ rejected current token; authentication is in backoff", rejection, true, millis(remaining));
        }
    }

    private static AuthFailure authFailure(Throwable value) {
        return value instanceof AuthFailure ? (AuthFailure) value
                : new AuthFailure("TBMQ authentication failed", value, false, 0);
    }
    private static boolean interrupted(Throwable value) {
        for (Throwable item : TbmqClientErrors.chain(value)) {
            if (item instanceof InterruptedException) { return true; }
            if (item instanceof java.io.InterruptedIOException
                    && !(item instanceof java.net.SocketTimeoutException)
                    && "interrupted".equalsIgnoreCase(item.getMessage())) { return true; }
        }
        return false;
    }
    private static final class LeaderInterrupted extends RuntimeException {
        LeaderInterrupted(Throwable cause) { super(cause); }
    }
    private static long millis(long nanos) { return Math.max(1, TimeUnit.NANOSECONDS.toMillis(nanos)); }
    private static boolean text(String value) { return value != null && !value.trim().isEmpty(); }
    private static String headerToken(String value, String source) {
        if (value == null) { return null; }
        String token = value.trim();
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (c <= 32 || c >= 127) { throw new IllegalArgumentException(source + " contains invalid header characters"); }
        }
        return token;
    }
    private static long expiration(String token) {
        try {
            if (!text(token)) { return Long.MAX_VALUE; }
            String[] parts = token.split("\\.");
            if (parts.length != 3) { return Long.MAX_VALUE; }
            JsonObject value = JsonParser.parseString(new String(Base64.getUrlDecoder().decode(parts[1]),
                    StandardCharsets.UTF_8)).getAsJsonObject();
            if (!value.has("exp") || !value.get("exp").isJsonPrimitive()
                    || !value.getAsJsonPrimitive("exp").isNumber()) { return Long.MAX_VALUE; }
            long seconds = value.get("exp").getAsBigDecimal().longValueExact();
            return seconds <= 0 || seconds > Long.MAX_VALUE / 1000 ? Long.MAX_VALUE : seconds * 1000;
        } catch (RuntimeException ignored) { return Long.MAX_VALUE; }
    }
}
