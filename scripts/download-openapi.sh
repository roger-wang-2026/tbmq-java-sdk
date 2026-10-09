#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
BASE_URL="${1:-http://localhost:8083}"
OUTPUT="${2:-${PROJECT_DIR}/openapi/tbmq-2.4.1.json}"

mkdir -p "$(dirname "${OUTPUT}")"
curl --fail --silent --show-error \
  "${BASE_URL%/}/v3/api-docs/TBMQ" \
  --output "${OUTPUT}"

jq -e '.openapi and .info.version and .paths' "${OUTPUT}" >/dev/null
printf 'Downloaded TBMQ OpenAPI %s (%s paths) to %s\n' \
  "$(jq -r '.info.version' "${OUTPUT}")" \
  "$(jq '.paths | length' "${OUTPUT}")" \
  "${OUTPUT}"
