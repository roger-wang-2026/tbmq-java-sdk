/*
 * Copyright 2026 Roger Wang
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.github.roger_wang_2026.tbmq.sdk;

/**
 * Supplies the current raw TBMQ JWT access token for an HTTP request.
 *
 * <p>The implementation must be thread-safe, fast and non-blocking because it is invoked once per HTTP
 * attempt. {@code AtomicReference<String>::get} is suitable for high-throughput applications.</p>
 */
@FunctionalInterface
public interface AccessTokenProvider {

    String getAccessToken();
}
