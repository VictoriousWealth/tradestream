#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
USERNAME="${USERNAME:-alice}"
PASSWORD="${PASSWORD:-S3cureP@ss!}"

STATUS="" BODY=""

have_jq() { command -v jq >/dev/null 2>&1; }
say() { printf "\n==> %s\n" "$*"; }
pp() {
  # Print arg as JSON only if it looks like JSON; otherwise print raw.
  local s="${1-}"
  # Trim leading whitespace
  s="${s#"${s%%[![:space:]]*}"}"
  if have_jq && [[ "$s" == \{* || "$s" == \[* ]]; then
    echo "$1" | jq .
  else
    # print raw (and don't fail the script)
    printf "%s\n" "$1"
  fi
}


# Curl wrapper that captures BODY and STATUS in the *parent* shell
curl_json() {
  local method="$1"; shift
  local url="$1"; shift
  local data="${1:-}" ; shift || true
  local args=("$@")

  if [[ -n "$data" ]]; then
    resp=$(curl -sS -w $'\n%{http_code}' -X "$method" "$url" -H "Content-Type: application/json" -d "$data" "${args[@]}")
  else
    resp=$(curl -sS -w $'\n%{http_code}' -X "$method" "$url" "${args[@]}")
  fi

  STATUS="${resp##*$'\n'}"
  BODY="${resp%$'\n'*}"
}

# 0) Health check
say "Gateway health"
curl_json GET "$BASE_URL/actuator/health"
pp "$BODY"
echo "HTTP $STATUS"

# 1) Register (idempotent: 200 first run, 409 next)
say "Register user via Gateway (/api/users/register)"
REGISTER_PAYLOAD=$(jq -n --arg u "$USERNAME" --arg p "$PASSWORD" '{username:$u, password:$p}' 2>/dev/null || echo "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\"}")
curl_json POST "$BASE_URL/api/users/register" "$REGISTER_PAYLOAD"
echo "HTTP $STATUS"
pp "$BODY"

# 2) Login → capture tokens and user_id
say "Login to get access & refresh tokens"
curl_json POST "$BASE_URL/api/auth/login" "$REGISTER_PAYLOAD"
echo "HTTP $STATUS"
pp "$BODY"

if have_jq; then
  ACCESS=$(echo "$BODY"   | jq -r '.access_token // empty')
  REFRESH=$(echo "$BODY"  | jq -r '.refresh_token // empty')
  USER_ID=$(echo "$BODY"  | jq -r '.user_id // empty')
else
  ACCESS=$(echo "$BODY"  | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')
  REFRESH=$(echo "$BODY" | sed -n 's/.*"refresh_token":"\([^"]*\)".*/\1/p')
  USER_ID=$(echo "$BODY" | sed -n 's/.*"user_id":"\([^"]*\)".*/\1/p')
fi

if [[ -z "${ACCESS:-}" || -z "${REFRESH:-}" || -z "${USER_ID:-}" ]]; then
  echo "ERROR: Could not parse tokens/user_id from login response."
  exit 1
fi

# 3) Protected endpoint WITHOUT token → expect 401
say "Protected endpoint without token should be 401"
curl_json GET "$BASE_URL/api/transactions/$USER_ID"
echo "HTTP $STATUS (expected 401)"

# 4) Protected endpoint WITH token
say "Protected endpoint with Bearer token (transactions page)"
curl_json GET "$BASE_URL/api/transactions/$USER_ID?page=0&size=1" "" -H "Authorization: Bearer $ACCESS"
echo "HTTP $STATUS"
pp "$BODY"

# 5) Refresh token via gateway
say "Refresh access token via Gateway"
REFRESH_PAYLOAD=$(jq -n --arg t "$REFRESH" '{refreshToken:$t}' 2>/dev/null || echo "{\"refreshToken\":\"$REFRESH\"}")
curl_json POST "$BASE_URL/api/auth/refresh" "$REFRESH_PAYLOAD"
echo "HTTP $STATUS"
pp "$BODY"

# 6) Market-data checks
TICKER="${TICKER:-AAPL}"

say "Market-data without token (expect 401)"
curl_json GET "$BASE_URL/api/market-data/$TICKER/latest?interval=1m"
echo "HTTP $STATUS (expected 401)"

say "Market-data with token (expect 200 or 404 depending on data)"
curl_json GET "$BASE_URL/api/market-data/$TICKER/latest?interval=1m" "" -H "Authorization: Bearer $ACCESS"
echo "HTTP $STATUS"
pp "$BODY"

# 7) Portfolio
say "Portfolio positions with token (expect 200)"
curl_json GET "$BASE_URL/api/portfolio/$USER_ID/positions" "" -H "Authorization: Bearer $ACCESS"
echo "HTTP $STATUS"
pp "$BODY"

# 8) Circuit-breaker hint
say "Circuit-breaker fallback test: stop a downstream (e.g., orders-service) then curl /api/orders/not-real with token and look for fallback JSON."
say "All done. ✅"

