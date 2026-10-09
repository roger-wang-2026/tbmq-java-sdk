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
  <version>2.4.1</version>
</dependency>
```

Until the artifact is published to a Maven repository, build and install it locally:

```bash
mvn clean install
```

## Usage

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

## Compatibility

- SDK version: 2.4.1
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
