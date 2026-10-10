/*
 * Copyright 2026 Roger Wang
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.github.roger_wang_2026.tbmq.sdk;

/** Result of a high-level REST publish operation. Acceptance is not proof of device delivery. */
public final class TbmqPublishResult {
    public enum DeliveryState { NOT_SENT, UNKNOWN, BROKER_ACCEPTED }

    public enum Status {
        ACCEPTED, NO_MATCHING_SUBSCRIBERS, AUTHENTICATION_FAILED, AUTH_BACKOFF,
        AUTHORIZATION_FAILED, RATE_LIMITED, BUDGET_EXHAUSTED, INTERRUPTED,
        TIMEOUT, TRANSPORT_FAILED, INVALID_REQUEST, BROKER_REJECTED, CLOSED, SERVICE_FAILED
    }

    private final Status status;
    private final Integer reasonCode;
    private final Integer httpStatus;
    private final Throwable cause;
    private final long retryAfterMillis;
    private final DeliveryState deliveryState;

    TbmqPublishResult(Status status, Integer reasonCode, Integer httpStatus,
                      Throwable cause, long retryAfterMillis, DeliveryState deliveryState) {
        this.status = status;
        this.reasonCode = reasonCode;
        this.httpStatus = httpStatus;
        this.cause = cause;
        this.retryAfterMillis = Math.max(0, retryAfterMillis);
        this.deliveryState = deliveryState;
    }

    public Status getStatus() { return status; }
    public Integer getReasonCode() { return reasonCode; }
    public Integer getHttpStatus() { return httpStatus; }
    public Throwable getCause() { return cause; }
    public long getRetryAfterMillis() { return retryAfterMillis; }
    public DeliveryState getDeliveryState() { return deliveryState; }
    public boolean isAccepted() { return status == Status.ACCEPTED || status == Status.NO_MATCHING_SUBSCRIBERS; }

    /** True when retrying may duplicate a message already accepted by TBMQ. */
    public boolean isDeliveryUnknown() {
        return deliveryState == DeliveryState.UNKNOWN;
    }
}
