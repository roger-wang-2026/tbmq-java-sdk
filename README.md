# TBMQ Java SDK

An independent, community-maintained Java 8 client generated from the TBMQ 2.4.1 OpenAPI document. The SDK
contains strongly typed methods and models for all 112 REST operations and 100 schemas exposed by TBMQ 2.4.1.

The Java API uses the community-owned `io.github.roger_wang_2026.tbmq.sdk` namespace. This intentionally avoids
the `org.thingsboard` namespace so the library cannot be mistaken for an official ThingsBoard distribution.

## Dependency

```xml
<dependency>
  <groupId>io.github.roger-wang-2026</groupId>
  <artifactId>tbmq-java-sdk</artifactId>
  <version>2.4.1.3</version>
</dependency>
```

Version 2.4.1.3 is available from Maven Central. Add the dependency above; no local SDK installation is required.

To build and install the SDK from source instead:

```bash
mvn clean install
```

## Usage

For publishing applications, use the thread-safe high-level client. It owns its HTTP resources unless an
application-managed `OkHttpClient` is supplied:

```java
try (TbmqClient client = TbmqClient.builder("https://tbmq.example.com")
        .credentials("sysadmin@thingsboard.org", "password")
        .timeout(Duration.ofSeconds(10))
        .requestBudget(Duration.ofSeconds(30))
        .build()) {
    TbmqPublishResult result = client.publish(TbmqPublishRequest.builder()
            .topic("devices/demo/commands")
            .payload("{\"enabled\":true}")
            .qos(1)
            .build());
}
```

The high-level client provides single-flight token renewal, one bounded 401 recovery, authentication backoff,
strict MQTT topic/payload encoding, request-budget checks, `Retry-After` parsing, and delivery-certainty metadata.
It has no Spring dependency. When a shared `OkHttpClient` is injected, closing `TbmqClient` does not close that
client's dispatcher or connection pool. Use `execute(api -> ...)` to call a synchronous generated endpoint through the same
authentication, budget and retry pipeline:

```java
PageDataShortClientSessionInfoDto sessions = client.execute(api ->
        api.clientSessions().getShortClientSessionInfos(20, 0, null, null, null));
```

`execute()` supports synchronous calls only. Its callback must complete the HTTP request on the calling
thread before returning. Authentication uses a `ThreadLocal` that is cleared when the operation finishes.
Do not call generated `*Async()` methods, call `enqueue()`, switch threads, or return an unexecuted `Call`
inside the callback. An asynchronous request runs on an OkHttp worker thread without that token context
and can fail with `IllegalStateException`; it is also outside the managed 401 recovery, budget checks and
active-call tracking used for shutdown.

`TbmqPublishResult.getDeliveryState()` distinguishes `NOT_SENT`, `UNKNOWN`, and `BROKER_ACCEPTED`. Callers should
only automatically retry `NOT_SENT`; retrying `UNKNOWN` may duplicate a message already accepted by TBMQ.
Closing an SDK-owned client waits up to five seconds for active calls before cancelling them. An injected shared
`OkHttpClient` remains application-owned and is never shut down by `TbmqClient`.

## Compatibility

- SDK version: 2.4.1.3
- TBMQ version: 2.4.1
- Java 8 or newer
- OpenAPI 3.1
- HTTP implementation: OkHttp + Gson
- No dependency on TBMQ server modules or its parent Maven project

## License

Licensed under the Apache License 2.0. TBMQ is a project of ThingsBoard; this repository is an independent
community SDK and is not an official ThingsBoard distribution.
