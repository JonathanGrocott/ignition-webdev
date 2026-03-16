#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 5 ]]; then
  echo "Usage: $0 <gateway_base_url> <project> <resource_path> <method> <expected_status> [body_file] [basic_user] [basic_pass]"
  echo "Example: $0 http://localhost:8088 myProject api/status GET 200"
  exit 1
fi

BASE_URL="$1"
PROJECT="$2"
RESOURCE_PATH="$3"
METHOD="$4"
EXPECTED_STATUS="$5"
BODY_FILE="${6:-}"
BASIC_USER="${7:-}"
BASIC_PASS="${8:-}"

URL="${BASE_URL%/}/system/webdev/${PROJECT}/${RESOURCE_PATH}"
TMP_BODY="$(mktemp)"
trap 'rm -f "$TMP_BODY"' EXIT

CURL_ARGS=(
  -sS
  --max-time 15
  -o "$TMP_BODY"
  -w "%{http_code}"
  -X "$METHOD"
  "$URL"
)

if [[ "$METHOD" == "HEAD" ]]; then
  CURL_ARGS=(-sS --max-time 15 -I -o "$TMP_BODY" -w "%{http_code}" "$URL")
fi

if [[ -n "$BODY_FILE" ]]; then
  CURL_ARGS+=(--data-binary "@$BODY_FILE")
fi

if [[ -n "$BASIC_USER" ]]; then
  CURL_ARGS+=(-u "${BASIC_USER}:${BASIC_PASS}")
fi

STATUS="$(curl "${CURL_ARGS[@]}")"

echo "URL: $URL"
echo "Method: $METHOD"
echo "Status: $STATUS (expected $EXPECTED_STATUS)"

echo "----- Response Body -----"
cat "$TMP_BODY"
echo
echo "-------------------------"

if [[ "$STATUS" != "$EXPECTED_STATUS" ]]; then
  echo "Smoke test failed: expected status $EXPECTED_STATUS but got $STATUS" >&2
  exit 2
fi

echo "Smoke test passed"
