/*
 * Copyright 2026 Roger Wang
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.github.roger_wang_2026.tbmq.sdk;

import io.github.roger_wang_2026.tbmq.sdk.generated.ApiException;
import java.util.ArrayList;
import java.util.List;

final class TbmqClientErrors {
    private TbmqClientErrors() {}
    static List<Throwable> chain(Throwable value) {
        List<Throwable> result = new ArrayList<>();
        for (int i = 0; value != null && i < 32 && !result.contains(value); i++) {
            result.add(value); value = value.getCause();
        }
        return result;
    }
    static boolean has(Throwable value, Class<? extends Throwable> type) {
        for (Throwable item : chain(value)) { if (type.isInstance(item)) { return true; } }
        return false;
    }
    static Integer httpStatus(Throwable value) {
        for (Throwable item : chain(value)) {
            if (item instanceof ApiException && ((ApiException) item).getCode() > 0) {
                return ((ApiException) item).getCode();
            }
        }
        return null;
    }
    static boolean connectionNotEstablished(Throwable value) {
        boolean beforeConnect = false;
        for (Throwable item : chain(value)) {
            if (item instanceof java.io.EOFException
                    || item instanceof java.net.SocketException && !(item instanceof java.net.ConnectException)
                    || item instanceof java.io.InterruptedIOException) { return false; }
            if (item instanceof java.net.ConnectException || item instanceof java.net.UnknownHostException
                    || item instanceof java.net.NoRouteToHostException
                    || item instanceof javax.net.ssl.SSLHandshakeException) { beforeConnect = true; }
        }
        return beforeConnect;
    }
}
