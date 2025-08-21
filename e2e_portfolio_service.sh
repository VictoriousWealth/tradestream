#!/usr/bin/env bash
set -euo pipefail

# --- Config / endpoints ---
NET=tradestream_private_net
BROKER=redpanda
ORDERS=http://orders-service:8085
PORTFOLIO=http://portfolio-service:8087

# Fresh identities each run
BUYER=$(uuidgen)
SELLER=$(uuidgen)
CP=$(uuidgen)
PRICE_MAKER_BUYER=$(uuidgen)
PRICE_MAKER_SELLER=$(uuidgen)
TICKER="PT$(date +%s)"

# Helper: tail N new records from a topic (starting at end)
consume_new () {
  local topic="$1" n="$2" out="$3"
  docker compose exec -T "$BROKER" rpk topic consume "$topic" --offset end -n "$n" > "$out" &
  echo $!
}

# Helper: produce a single-line JSON to a topic with key=ticker
produce_trade_executed () {
  local ticker="$1" price="$2" qty="$3"
  # Use UUIDs so this synthetic trade doesn’t collide with anything
  local tid=$(uuidgen) bo=$(uuidgen) so=$(uuidgen)
  docker compose exec -T "$BROKER" bash -lc \
    "printf '%s\n' '{\"tradeId\":\"$tid\",\"buyOrderId\":\"$bo\",\"sellOrderId\":\"$so\",\"ticker\":\"$ticker\",\"price\":$price,\"quantity\":$qty,\"timestamp\":\"'\"$(date -u +%Y-%m-%dT%H:%M:%SZ)\"'\"}' \
     | rpk topic produce trade.executed.v1 -k $ticker" >/dev/null
}

echo "BUYER=$BUYER"
echo "SELLER=$SELLER"
echo "CP=$CP"
echo "TICKER=$TICKER"
echo

echo "==> 0) Health check"
docker run --rm --network "$NET" curlimages/curl:8.9.1 -sS "$PORTFOLIO/actuator/health" ; echo

echo "==> 1) Seed trade: BUYER BUY 10 @100 then SELLER SELL 10 @100 (should cross)"
# BUY
docker run --rm -i --network "$NET" curlimages/curl:8.9.1 -sS \
  -H 'Content-Type: application/json' --data @- "$ORDERS/orders" <<EOF
{"userId":"$BUYER","ticker":"$TICKER","side":"BUY","type":"LIMIT","timeInForce":"GTC","price":100.00,"quantity":10}
EOF
echo

# Start watcher BEFORE counter-order so we definitely capture the 2 ledger rows
FIRST_LEDGER=/tmp/port_e2e_first.$$.log
W1=$(consume_new transaction.recorded.v1 2 "$FIRST_LEDGER")

# SELL (crosses)
docker run --rm -i --network "$NET" curlimages/curl:8.9.1 -sS \
  -H 'Content-Type: application/json' --data @- "$ORDERS/orders" <<EOF
{"userId":"$SELLER","ticker":"$TICKER","side":"SELL","type":"LIMIT","timeInForce":"GTC","price":100.00,"quantity":10}
EOF
echo

# Soft wait for watcher
for i in {1..20}; do kill -0 "$W1" 2>/dev/null || break; sleep 0.5; done
kill -0 "$W1" 2>/dev/null && { echo "(timeout waiting first 2 ledger rows)"; kill "$W1" || true; }
echo "==> First trade ledger rows:"
cat "$FIRST_LEDGER"

echo
echo "==> 2) Check positions:"
echo "   2a) BUYER (expect qty=10, avgCost=100, realized=0)"
docker run --rm --network "$NET" curlimages/curl:8.9.1 -sS \
  "$PORTFOLIO/portfolio/$BUYER/positions" ; echo

echo "   2b) SELLER (expect SHORT qty=-10, avgCost ~100, realized=0) — if shorts supported"
docker run --rm --network "$NET" curlimages/curl:8.9.1 -sS \
  "$PORTFOLIO/portfolio/$SELLER/positions" ; echo

echo "   2c) Optional single-ticker endpoint (if implemented) — BUYER @$TICKER"
if docker run --rm --network "$NET" curlimages/curl:8.9.1 -fsS \
  "$PORTFOLIO/portfolio/$BUYER/positions/$TICKER" >/tmp/pos_one.$$.json 2>/dev/null; then
  cat /tmp/pos_one.$$.json; echo
else
  echo "(positions/{userId}/positions/{ticker} not implemented — skipping)"; echo
fi

echo "==> 3) Realize PnL: BUYER SELL 4 @110; CP BUY 4 @110"
SECOND_LEDGER=/tmp/port_e2e_second.$$.log
W2=$(consume_new transaction.recorded.v1 2 "$SECOND_LEDGER")

# SELL 4 from BUYER @110
docker run --rm -i --network "$NET" curlimages/curl:8.9.1 -sS \
  -H 'Content-Type: application/json' --data @- "$ORDERS/orders" <<EOF
{"userId":"$BUYER","ticker":"$TICKER","side":"SELL","type":"LIMIT","timeInForce":"GTC","price":110.00,"quantity":4}
EOF
echo
# CP BUY 4 @110 (cross)
docker run --rm -i --network "$NET" curlimages/curl:8.9.1 -sS \
  -H 'Content-Type: application/json' --data @- "$ORDERS/orders" <<EOF
{"userId":"$CP","ticker":"$TICKER","side":"BUY","type":"LIMIT","timeInForce":"GTC","price":110.00,"quantity":4}
EOF
echo

for i in {1..20}; do kill -0 "$W2" 2>/dev/null || break; sleep 0.5; done
kill -0 "$W2" 2>/dev/null && { echo "(timeout waiting realization ledger rows)"; kill "$W2" || true; }
echo "==> Realization trade ledger rows:"
cat "$SECOND_LEDGER"

echo
echo "==> 4) Post-realization positions:"
echo "   4a) BUYER (expect qty=6, avgCost=100, realizedPnL≈40)"
docker run --rm --network "$NET" curlimages/curl:8.9.1 -sS \
  "$PORTFOLIO/portfolio/$BUYER/positions" ; echo

echo "   4b) CP (expect qty=4 @110, realized=0)"
docker run --rm --network "$NET" curlimages/curl:8.9.1 -sS \
  "$PORTFOLIO/portfolio/$CP/positions" ; echo

echo
echo "==> 5) Unrealized PnL scenario (set last price via synthetic trade.executed to act as 'mark')"
echo "      Target last price = 125.00. This will NOT change positions/realized PnL."
echo "      If portfolio-service consumes trade.executed.v1 for marks, it should populate lastPrice & unrealized."
produce_trade_executed "$TICKER" 125.00 1

# small settle wait
sleep 1.5

echo "   5a) BUYER positions again (expect lastPrice≈125, unrealized≈(125-100)*6=150)"
docker run --rm --network "$NET" curlimages/curl:8.9.1 -sS \
  "$PORTFOLIO/portfolio/$BUYER/positions" ; echo

echo "   5b) SELLER positions again (if short supported, unrealized≈(avgCost - 125)*10)"
docker run --rm --network "$NET" curlimages/curl:8.9.1 -sS \
  "$PORTFOLIO/portfolio/$SELLER/positions" ; echo

echo
echo "==> 6) Negative/sanity checks"
echo "   6a) Unknown user should return empty []"
docker run --rm --network "$NET" curlimages/curl:8.9.1 -sS \
  "$PORTFOLIO/portfolio/00000000-0000-0000-0000-000000000000/positions" ; echo

echo
echo "==> Done."
