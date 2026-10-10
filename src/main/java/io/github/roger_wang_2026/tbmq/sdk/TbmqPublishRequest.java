/*
 * Copyright 2026 Roger Wang
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.github.roger_wang_2026.tbmq.sdk;

/** Immutable high-level REST publish request. */
public final class TbmqPublishRequest {
    private final String topic;
    private final String payload;
    private final int qos;
    private final boolean retain;

    private TbmqPublishRequest(Builder builder) {
        this.topic = builder.topic;
        this.payload = builder.payload;
        this.qos = builder.qos;
        this.retain = builder.retain;
    }

    public static Builder builder() { return new Builder(); }
    public String getTopic() { return topic; }
    public String getPayload() { return payload; }
    public int getQos() { return qos; }
    public boolean isRetain() { return retain; }

    public static final class Builder {
        private String topic;
        private String payload;
        private int qos;
        private boolean retain;
        public Builder topic(String value) { topic = value; return this; }
        public Builder payload(String value) { payload = value; return this; }
        public Builder qos(int value) { qos = value; return this; }
        public Builder retain(boolean value) { retain = value; return this; }
        public TbmqPublishRequest build() { return new TbmqPublishRequest(this); }
    }
}
