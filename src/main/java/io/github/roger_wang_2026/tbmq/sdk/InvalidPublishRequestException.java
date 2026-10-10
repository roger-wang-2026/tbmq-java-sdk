/*
 * Copyright 2026 Roger Wang
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.github.roger_wang_2026.tbmq.sdk;

/** Indicates that a publish request was rejected locally before any HTTP request was sent. */
public final class InvalidPublishRequestException extends IllegalArgumentException {
    InvalidPublishRequestException(String message) {
        super(message);
    }

    InvalidPublishRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
