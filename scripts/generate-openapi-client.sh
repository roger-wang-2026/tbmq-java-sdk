#!/usr/bin/env bash
set -euo pipefail

readonly GENERATOR_VERSION="7.26.0"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
SPEC="${1:-${PROJECT_DIR}/openapi/tbmq-2.4.1.json}"
GENERATED_PACKAGE="${PROJECT_DIR}/src/main/java/io/github/roger_wang_2026/tbmq/sdk/generated"
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "${WORK_DIR}"' EXIT

if [[ ! -f "${SPEC}" ]]; then
  printf 'OpenAPI document not found: %s\n' "${SPEC}" >&2
  exit 1
fi

# TBMQ documents its login flow with the custom HTTP scheme "loginPassword". OpenAPI generators do not
# recognize that scheme, while authenticated endpoints actually expect this API-key-style header.
jq '.components.securitySchemes.HttpLoginForm = {
      "type": "apiKey",
      "in": "header",
      "name": "X-Authorization",
      "description": "TBMQ JWT header. Use the value: Bearer <access-token>."
    }' "${SPEC}" > "${WORK_DIR}/normalized-openapi.json"

java_version="$(java -version 2>&1 | awk -F'[".]' '/version/ { print ($2 == "1" ? $3 : $2); exit }')"
if [[ -z "${java_version}" || "${java_version}" -lt 11 ]]; then
  printf 'OpenAPI Generator requires JDK 11+ (generated sources still target Java 8).\n' >&2
  exit 1
fi

mvn -q org.apache.maven.plugins:maven-dependency-plugin:3.8.1:copy \
  -Dartifact="org.openapitools:openapi-generator-cli:${GENERATOR_VERSION}" \
  -DoutputDirectory="${WORK_DIR}"

java -jar "${WORK_DIR}/openapi-generator-cli-${GENERATOR_VERSION}.jar" generate \
  --generator-name java \
  --input-spec "${WORK_DIR}/normalized-openapi.json" \
  --output "${WORK_DIR}/generated" \
  --skip-validate-spec \
  --additional-properties "library=okhttp-gson,dateLibrary=java8,java8=true,artifactVersion=2.4.1,invokerPackage=io.github.roger_wang_2026.tbmq.sdk.generated,apiPackage=io.github.roger_wang_2026.tbmq.sdk.generated.api,modelPackage=io.github.roger_wang_2026.tbmq.sdk.generated.model,hideGenerationTimestamp=true,serializationLibrary=gson,useJakartaEe=false,disallowAdditionalPropertiesIfNotPresent=false,openApiNullable=false"

rm -rf "${GENERATED_PACKAGE}"
mkdir -p "${GENERATED_PACKAGE}"
cp -R "${WORK_DIR}/generated/src/main/java/io/github/roger_wang_2026/tbmq/sdk/generated/." \
  "${GENERATED_PACKAGE}/"

printf 'Generated %s Java source files from TBMQ OpenAPI %s.\n' \
  "$(find "${GENERATED_PACKAGE}" -type f -name '*.java' | wc -l | tr -d ' ')" \
  "$(jq -r '.info.version' "${SPEC}")"
