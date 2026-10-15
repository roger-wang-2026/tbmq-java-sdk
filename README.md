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

## Publishing to Maven Central

The `Publish to Maven Central` workflow publishes stable GitHub Releases automatically. It can also be
started manually with an existing tag. The tag must match the POM version. For older tags such as
`v2.4.1.3`, the workflow supplies missing developer metadata and the Central publishing profile from
the workflow revision, while keeping the tagged SDK sources, dependencies and version unchanged.

Before the first publication:

1. Register at [Central Portal](https://central.sonatype.com/) and verify the namespace
   `io.github.roger-wang-2026` for this GitHub account.
2. Generate a Central Portal user token (its username and password are publishing credentials).
3. Create a GPG signing key and publish its public key to a publicly accessible keyserver.
4. Create the GitHub Actions environment `maven-central` and add these environment secrets:

| Secret | Value |
| --- | --- |
| `MAVEN_CENTRAL_USERNAME` | Central Portal token username |
| `MAVEN_CENTRAL_PASSWORD` | Central Portal token password |
| `GPG_PRIVATE_KEY` | ASCII-armored signing private key, including the BEGIN/END lines |
| `GPG_PASSPHRASE` | Signing key passphrase |

Set the next unpublished version in `pom.xml`, commit it together with this workflow, push the matching
`v<version>` tag, and publish its GitHub Release. The workflow tests the project, creates sources and
Javadoc JARs, signs the artifacts, and waits for Central publication to finish. Central versions are
immutable: use a new version after publication, rather than moving an existing tag.

To validate the release build locally without signing or uploading:

```bash
mvn -Pcentral-release -Dgpg.skip=true clean verify
```

See [Sonatype's Maven publishing documentation](https://central.sonatype.org/publish/publish-portal-maven/).

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

For generated asynchronous APIs, use a separate `TbmqOpenApiClient` with a fixed token or a thread-safe
token provider. The application must handle token renewal, retries and asynchronous request lifecycle.

`TbmqPublishResult.getDeliveryState()` distinguishes `NOT_SENT`, `UNKNOWN`, and `BROKER_ACCEPTED`. Callers should
only automatically retry `NOT_SENT`; retrying `UNKNOWN` may duplicate a message already accepted by TBMQ.
Closing an SDK-owned client waits up to five seconds for active calls before cancelling them. An injected shared
`OkHttpClient` remains application-owned and is never shut down by `TbmqClient`.

For direct access to every generated endpoint, create an authenticated OpenAPI client:

Create an authenticated client with the base URL and a TBMQ JWT access token:

```java
import io.github.roger_wang_2026.tbmq.sdk.TbmqOpenApiClient;
import io.github.roger_wang_2026.tbmq.sdk.generated.model.NodeDrainStatus;
import io.github.roger_wang_2026.tbmq.sdk.generated.model.PageDataShortClientSessionInfoDto;

TbmqOpenApiClient client = TbmqOpenApiClient.create(
        "https://tbmq.example.com",
        System.getenv("TBMQ_TOKEN"));

PageDataShortClientSessionInfoDto sessions = client.clientSessions()
        .getShortClientSessionInfos(20, 0, null, null, null);

NodeDrainStatus drainStatus = client.nodeDrain().getDrainStatus();
```

`TbmqOpenApiClient` exposes all 20 generated controller clients by domain, including administrators,
applications, authentication, blocked clients, sessions, integrations, MQTT credentials, REST publishing,
node drain, retained messages, subscriptions, time series, unauthorized clients, and WebSocket resources.

Create the client without a token for login and password-reset endpoints:

```java
TbmqOpenApiClient client = TbmqOpenApiClient.create("https://tbmq.example.com");
LoginResponse login = client.login().apiAuthLoginPost(
        new LoginRequest().username("sysadmin@thingsboard.org").password("password"));
```

TBMQ uses the non-standard `X-Authorization: Bearer ...` header. The SDK generation process normalizes the
OpenAPI security declaration, and `TbmqOpenApiClient` configures the token automatically.

## High-throughput applications

Inject one shared `OkHttpClient` and use a non-blocking token provider when the application publishes at high
throughput. Updating the `AtomicReference` changes the token for subsequent requests without rebuilding the
OpenAPI client or discarding pooled HTTP connections:

```java
AtomicReference<String> accessToken = new AtomicReference<>(initialToken);

Dispatcher dispatcher = new Dispatcher();
dispatcher.setMaxRequests(256);
dispatcher.setMaxRequestsPerHost(256);

OkHttpClient httpClient = new OkHttpClient.Builder()
        .dispatcher(dispatcher)
        .connectionPool(new ConnectionPool(50, 5, TimeUnit.MINUTES))
        .build();

TbmqOpenApiClient client = TbmqOpenApiClient.create(
        "https://tbmq.example.com",
        httpClient,
        accessToken::get);

// After login or refresh:
accessToken.set(refreshedToken);
```

The token provider is invoked once per HTTP attempt and must be thread-safe, fast and non-blocking. The derived
authenticated client shares the supplied client's dispatcher and connection pool. For fixed-token or
unauthenticated use, call `create(baseUrl, token, httpClient)` or `create(baseUrl, httpClient)` respectively.

## Compatibility

- SDK version: 2.4.1.3
- TBMQ version: 2.4.1
- Java 8 or newer
- OpenAPI 3.1
- HTTP implementation: OkHttp + Gson
- No dependency on TBMQ server modules or its parent Maven project

## Updating generated sources

The unmodified server document is committed as `openapi/tbmq-2.4.1.json`. With a local TBMQ instance running,
download it again and regenerate the client:

```bash
./scripts/download-openapi.sh http://localhost:8083
JAVA_HOME=$(/usr/libexec/java_home -v 25) ./scripts/generate-openapi-client.sh
mvn clean test
```

Generation uses OpenAPI Generator 7.26.0 with the `okhttp-gson` Java client. Generated sources target Java 8;
only regeneration requires JDK 11 or newer because of the generator itself. The generation script maps TBMQ's
custom OpenAPI `loginPassword` security scheme to its actual `X-Authorization` header while preserving the
downloaded OpenAPI document unchanged.

## License

Licensed under the Apache License 2.0. TBMQ is a project of ThingsBoard; this repository is an independent
community SDK and is not an official ThingsBoard distribution.
