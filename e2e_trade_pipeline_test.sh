#!/usr/bin/env bash
set -euo pipefail

RED='\033[0;31m'; GRN='\033[0;32m'; YLW='\033[1;33m'; NC='\033[0m'
info(){ echo -e "${YLW}⇒ $*${NC}"; }
ok(){ echo -e "${GRN}✔ $*${NC}"; }
fail(){ echo -e "${RED}✘ $*${NC}"; exit 1; }

TICKER="${1:-AAPL}"
PRICE="${PRICE:-150.25}"
QTY="${QTY:-50}"

SELL_ORDER_ID="11111111-1111-1111-1111-111111111111"
BUY_ORDER_ID="22222222-2222-2222-2222-222222222222"
USER_A="aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
USER_B="bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"

detect_network() {
  local net
  net="$(docker network ls --format '{{.Name}}' | grep -E '_private_net$' | head -n1 || true)"
  [[ -z "$net" ]] && net="private_net"
  echo "$net"
}

pick_container_by_hint() {
  local hint="$1"
  docker ps --format '{{.Names}}' | grep -E "$hint" | head -n1 || true
}

net_curl() {
  local method="$1"; shift
  docker run --rm --network "${PRIVATE_NET}" curlimages/curl:8.9.1 -sS -X "$method" "$@"
}

PRIVATE_NET="$(detect_network)"
info "Using docker network: ${PRIVATE_NET}"

info "Ensuring core services are running..."
docker compose up -d orders_postgres matching_postgres market_postgres transaction_postgres postgres \
  redis redpanda orders-service matching-engine market-data-consumer >/dev/null

# Prefer service names (more stable). If you really want container names, keep your pick_* lines.
ORDERS_CTN="${ORDERS_CTN:-$(pick_container_by_hint 'orders-service')}"
ME_CTN="${ME_CTN:-$(pick_container_by_hint 'matching-engine')}"
MDS_CTN="${MDS_CTN:-$(pick_container_by_hint 'market-data-consumer')}"
REDPANDA_CTN="${REDPANDA_CTN:-$(pick_container_by_hint 'redpanda')}"

[[ -z "$ORDERS_CTN" || -z "$ME_CTN" || -z "$MDS_CTN" || -z "$REDPANDA_CTN" ]] && \
  fail "Could not resolve one or more containers (orders/matching/market-data/redpanda). Are they up?"

# TIP: swap to service names if DNS ever complains
ORDERS_HOST="${ORDERS_HOST:-$ORDERS_CTN}"   # or: ORDERS_HOST=orders-service
ME_HOST="${ME_HOST:-$ME_CTN}"               # or: ME_HOST=matching-engine
MDS_HOST="${MDS_HOST:-$MDS_CTN}"            # or: MDS_HOST=market-data-consumer
ORDERS_PORT=8085
ME_PORT=8086
MDS_PORT=8083

info "Waiting for service health (Orders, Matching, Market Data)..."
for i in {1..60}; do
  set +e
  net_curl GET "http://${ORDERS_HOST}:${ORDERS_PORT}/actuator/health" >/dev/null && O=ok || O=
  net_curl GET "http://${ME_HOST}:${ME_PORT}/actuator/health" >/dev/null && M=ok || M=
  net_curl GET "http://${MDS_HOST}:${MDS_PORT}/actuator/health" >/dev/null && D=ok || D=
  set -e
  if [[ "$O$M$D" == "okokok" ]]; then ok "All health checks passed."; break; fi
  sleep 2
  [[ $i -eq 60 ]] && fail "Services failed to become healthy in time."
done

info "Arming trade watcher at end of topic..."
WATCH_FILE="/tmp/e2e_trade.json"
docker compose exec -T redpanda sh -lc "rm -f ${WATCH_FILE}; \
  rpk topic consume trade.executed.v1 --offset end -f '%v' -n 1 > ${WATCH_FILE} 2>/dev/null & echo \$! > /tmp/e2e_watch.pid"

info "Placing resting SELL (${QTY} @ ${PRICE}) on ${TICKER}..."
POST_STATUS=$(
  docker run --rm --network "${PRIVATE_NET}" curlimages/curl:8.9.1 -sS -o /dev/stderr -w '%{http_code}' \
    -X POST "http://${ORDERS_HOST}:${ORDERS_PORT}/orders" \
    -H "Content-Type: application/json" \
    -d '{"orderId":"'"${SELL_ORDER_ID}"'","userId":"'"${USER_A}"'","ticker":"'"${TICKER}"'","side":"SELL","type":"LIMIT","timeInForce":"GTC","price":'"${PRICE}"',"quantity":'"${QTY}"'}'
)
[[ "$POST_STATUS" =~ ^20[02]$ ]] || fail "SELL POST failed: HTTP $POST_STATUS"
ok "SELL placed."
sleep 1

info "Placing crossing BUY (${QTY} @ ${PRICE}) on ${TICKER}..."
POST_STATUS=$(
  docker run --rm --network "${PRIVATE_NET}" curlimages/curl:8.9.1 -sS -o /dev/stderr -w '%{http_code}' \
    -X POST "http://${ORDERS_HOST}:${ORDERS_PORT}/orders" \
    -H "Content-Type: application/json" \
    -d '{"orderId":"'"${BUY_ORDER_ID}"'","userId":"'"${USER_B}"'","ticker":"'"${TICKER}"'","side":"BUY","type":"LIMIT","timeInForce":"GTC","price":'"${PRICE}"',"quantity":'"${QTY}"'}'
)
[[ "$POST_STATUS" =~ ^20[02]$ ]] || fail "BUY POST failed: HTTP $POST_STATUS"
ok "BUY placed."

info "Waiting for trade on Kafka (trade.executed.v1)..."
for i in {1..40}; do
  if docker compose exec -T redpanda sh -lc "[ -s ${WATCH_FILE} ]"; then
    TRADE_JSON="$(docker compose exec -T redpanda sh -lc "cat ${WATCH_FILE}")"
    echo "${TRADE_JSON}" | jq . || echo "${TRADE_JSON}"
    ok "Trade event received."
    break
  fi
  sleep 0.5
  [[ $i -eq 40 ]] && fail "No trade received from Kafka (trade.executed.v1)."
done

info "Querying Market Data Consumer for latest 1m candle..."
CANDLE_JSON="$(net_curl GET "http://${MDS_HOST}:${MDS_PORT}/candles/${TICKER}/latest?interval=1m" || true)"
[[ -z "${CANDLE_JSON}" || "${CANDLE_JSON}" == *"Not Found"* ]] && fail "No candle returned from Market Data Consumer."
echo "Candle:"
echo "${CANDLE_JSON}"
ok "Candle received."

ok "End-to-end test PASSED 🎉"
