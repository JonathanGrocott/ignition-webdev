#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 2 ]]; then
  echo "Usage: $0 <gateway_base_url> <project_name>"
  echo "Example: $0 http://localhost:8088 webcompat_live_parity"
  exit 1
fi

BASE_URL="${1%/}"
PROJECT="$2"

LAST_HDR=""
LAST_BODY=""
LAST_CODE=""
TMP_FILES=()

cleanup() {
  if [[ ${#TMP_FILES[@]} -gt 0 ]]; then
    rm -f "${TMP_FILES[@]}"
  fi
}
trap cleanup EXIT

request() {
  local method="$1"
  local path="$2"
  shift 2

  LAST_HDR="$(mktemp)"
  LAST_BODY="$(mktemp)"
  TMP_FILES+=("$LAST_HDR" "$LAST_BODY")

  local url="$BASE_URL/system/webdev/$PROJECT/$path"
  if [[ "$method" == "HEAD" ]]; then
    LAST_CODE="$(curl --path-as-is -sS --max-time 20 -I -D "$LAST_HDR" -o "$LAST_BODY" -w "%{http_code}" "$@" "$url")"
  else
    LAST_CODE="$(curl --path-as-is -sS --max-time 20 -D "$LAST_HDR" -o "$LAST_BODY" -X "$method" -w "%{http_code}" "$@" "$url")"
  fi

  echo "$method $url -> $LAST_CODE"
}

assert_code() {
  local expected="$1"
  if [[ "$LAST_CODE" != "$expected" ]]; then
    echo "Expected status $expected but got $LAST_CODE" >&2
    cat "$LAST_BODY" >&2
    exit 1
  fi
}

assert_body_equals() {
  local expected="$1"
  local body
  body="$(cat "$LAST_BODY")"
  if [[ "$body" != "$expected" ]]; then
    echo "Body mismatch" >&2
    echo "Expected: '$expected'" >&2
    echo "Actual:   '$body'" >&2
    exit 1
  fi
}

assert_header_contains() {
  local pattern="$1"
  if ! grep -qi "$pattern" "$LAST_HDR"; then
    echo "Header check failed. Missing pattern: $pattern" >&2
    cat "$LAST_HDR" >&2
    exit 1
  fi
}

request GET text
assert_code 200
assert_body_equals "phase2 text parity"
assert_header_contains '^Content-Type: text/plain'

request HEAD text
assert_code 200
assert_header_contains '^Content-Length: 18'

request GET file
assert_code 200
assert_body_equals "0123456789abcdef"
assert_header_contains '^Accept-Ranges: bytes'

request GET file -H 'Range: bytes=2-5'
assert_code 206
assert_body_equals "2345"
assert_header_contains '^Content-Range: bytes 2-5/16'

request HEAD file -H 'Range: bytes=2-5'
assert_code 206
assert_header_contains '^Content-Range: bytes 2-5/16'

request GET file -H 'Range: bytes=100-120'
assert_code 416
assert_header_contains '^Content-Range: bytes \*/16'

request GET mounted
assert_code 200
assert_body_equals "mounted index"

request GET mounted/asset.txt -H 'Range: bytes=5-'
assert_code 206
assert_body_equals "fghijklmnopqrstuvwxyz"
assert_header_contains '^Content-Range: bytes 5-25/26'

request GET mounted/../../etc/passwd
assert_code 404

request GET mounted/asset.txt -H 'If-Modified-Since: Wed, 31 Dec 2099 23:59:59 GMT'
assert_code 304

echo
echo "Phase 2 live parity checks passed for project '$PROJECT'."
