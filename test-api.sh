#!/usr/bin/env bash
set -uo pipefail

BASE="${BASE:-http://localhost:8080}"
URL="https://example-$RANDOM.com"
BODY=/tmp/vigil-test-body
passed=0
failed=0

pass() {
  echo "PASS  $1"
  passed=$((passed + 1))
}

fail() {
  echo "FAIL  $1"
  echo "      $(head -c 300 "$BODY")"
  failed=$((failed + 1))
}

check() {
  local name="$1" expected="$2" method="$3" path="$4" body="${5:-}"
  local args=(-s -o "$BODY" -w '%{http_code}' -X "$method" "$BASE$path")
  [[ -n "$body" ]] && args+=(-H 'Content-Type: application/json' -d "$body")
  local status
  status=$(curl "${args[@]}")
  if [[ "$status" == "$expected" ]]; then
    pass "$name ($status)"
  else
    fail "$name: expected $expected, got $status"
  fi
}

body_has() {
  local name="$1" pattern="$2"
  if grep -qE -- "$pattern" "$BODY"; then
    pass "$name"
  else
    fail "$name: body does not match $pattern"
  fi
}

body_is() {
  local name="$1" expected="$2"
  if [[ "$(cat "$BODY")" == "$expected" ]]; then
    pass "$name"
  else
    fail "$name: expected body $expected"
  fi
}

section() {
  echo
  echo "== $1"
}

if ! curl -s -o /dev/null "$BASE"; then
  echo "api is not reachable at $BASE, start it with ./run.sh api" >&2
  exit 1
fi

section "POST /api/sites"
check "create a site" 201 POST /api/sites "{\"name\":\"Example\",\"url\":\"$URL\"}"
id=$(grep -o '"id":[0-9]*' "$BODY" | head -1 | cut -d: -f2)
body_has "new site is PENDING" '"status":"PENDING"'
body_has "new site has no check time" '"checkedAt":null'
check "create the same url again" 409 POST /api/sites "{\"name\":\"Example\",\"url\":\"$URL\"}"
check "ftp url" 400 POST /api/sites '{"name":"Bad","url":"ftp://example.com"}'
check "url without scheme" 400 POST /api/sites '{"name":"Bad","url":"example.com"}'
check "empty name" 400 POST /api/sites '{"name":"","url":"https://valid.com"}'
check "blank name" 400 POST /api/sites '{"name":"   ","url":"https://valid.com"}'
check "missing url" 400 POST /api/sites '{"name":"No url"}'
check "name over 100 chars" 400 POST /api/sites "{\"name\":\"$(printf 'a%.0s' {1..101})\",\"url\":\"https://long.com\"}"
check "invalid json" 400 POST /api/sites '{"name":'

section "GET /api/sites"
check "list sites" 200 GET /api/sites
body_has "response is a JSON array" '^\['
body_has "created site is in the list" "\"url\":\"$URL\",\"status\":\"PENDING\""

section "GET /api/sites/{id}/checks"
if [[ -n "${id:-}" ]]; then
  check "checks of a site never checked" 200 GET "/api/sites/$id/checks"
  body_is "is an empty list" '[]'
  check "limit=1" 200 GET "/api/sites/$id/checks?limit=1"
  check "limit=200" 200 GET "/api/sites/$id/checks?limit=200"
  check "limit=0" 400 GET "/api/sites/$id/checks?limit=0"
  check "limit=-5" 400 GET "/api/sites/$id/checks?limit=-5"
  check "limit=201" 400 GET "/api/sites/$id/checks?limit=201"
  check "limit=abc" 400 GET "/api/sites/$id/checks?limit=abc"
else
  echo "SKIP  checks tests: no id in the create response"
fi
check "checks of a site that doesn't exist" 404 GET /api/sites/999999/checks
check "checks with a non-numeric id" 400 GET /api/sites/abc/checks

section "GET /api/sites/{id}/history"
if [[ -n "${id:-}" ]]; then
  check "history of a site never checked" 200 GET "/api/sites/$id/history"
  body_is "is an empty list" '[]'
  check "hours=1" 200 GET "/api/sites/$id/history?hours=1"
  check "hours=168" 200 GET "/api/sites/$id/history?hours=168"
  check "hours=0" 400 GET "/api/sites/$id/history?hours=0"
  check "hours=169" 400 GET "/api/sites/$id/history?hours=169"
  check "hours=abc" 400 GET "/api/sites/$id/history?hours=abc"
fi
check "history of a site that doesn't exist" 404 GET /api/sites/999999/history

section "seed data (load dev-seed.sql first)"
curl -s -o "$BODY" "$BASE/api/sites"
seed_id=$(grep -oE '"id":[0-9]+,"name":"Recovered"' "$BODY" | grep -oE '[0-9]+')
if [[ -n "$seed_id" ]]; then
  for expected in 'Always up:UP' 'Went down:DOWN' 'Server error:DOWN' 'Slow:SLOW' 'Recovered:UP' 'Never checked:PENDING'; do
    name="${expected%%:*}"
    status="${expected##*:}"
    body_has "$name is $status" "\"name\":\"$name\",[^}]*\"status\":\"$status\""
  done
  check "checks of Recovered" 200 GET "/api/sites/$seed_id/checks"
  body_has "newest check comes first" '^\[\{"up":true,"statusCode":200'
  body_has "DNS error check is included" '"error":"UnknownHostException"'
  check "limit=2 on Recovered" 200 GET "/api/sites/$seed_id/checks?limit=2"
  body_has "returns exactly 2 checks" '^\[\{[^]]*\},\{[^]]*\}\]$'
  check "history of Recovered" 200 GET "/api/sites/$seed_id/history?hours=168"
  body_has "one 10-minute period" '^\[\{[^]]*\}\]$'
  body_has "period starts on a 10-minute boundary" '"start":"[0-9-]+T[0-9]{2}:[0-9]0:00Z"'
  body_has "average ignores the check without a time" '"avgResponseTimeMs":255'
  body_has "max, count and failures" '"maxResponseTimeMs":300,"checkCount":3,"failedCheckCount":2'
else
  echo "SKIP  seed tests: seed sites not found"
fi

section "DELETE /api/sites/{id}"
if [[ -n "${id:-}" ]]; then
  check "delete the site" 204 DELETE "/api/sites/$id"
  check "delete it again" 404 DELETE "/api/sites/$id"
  check "its checks are gone too" 404 GET "/api/sites/$id/checks"
fi
check "delete a site that never existed" 404 DELETE /api/sites/999999
check "delete with a non-numeric id" 400 DELETE /api/sites/abc

echo
echo "$passed passed, $failed failed"
[[ $failed -eq 0 ]]
