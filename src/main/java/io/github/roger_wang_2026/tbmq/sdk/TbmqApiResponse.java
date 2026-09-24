/**
 * Copyright 2026 Roger Wang
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.github.roger_wang_2026.tbmq.sdk;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class TbmqApiResponse<T> {
    private final int statusCode;
    private final T body;
    private final Map<String, List<String>> headers;

    TbmqApiResponse(int statusCode, T body, Map<String, List<String>> headers) {
        this.statusCode = statusCode;
        this.body = body;
        this.headers = headers == null ? Collections.<String, List<String>>emptyMap() : headers;
    }

    public int getStatusCode() { return statusCode; }
    public T getBody() { return body; }
    public Map<String, List<String>> getHeaders() { return headers; }
}
