/**
 * Copyright 2026 Roger Wang
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.github.roger_wang_2026.tbmq.sdk;

public class TbmqRestPublishException extends TbmqApiException {

    public TbmqRestPublishException(String message, int statusCode, String responseBody) {
        super(message, statusCode, responseBody);
    }
    public TbmqRestPublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
