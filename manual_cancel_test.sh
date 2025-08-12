#!/usr/bin/env bash
set -euo pipefail

# --- Config (no stray quotes, correct port) ---
PRIVATE_NET="${PRIVATE_NET:-tradestream_private_net}"
ORDERS_HOST="${ORDERS_HOST:-tradestream-orders-service-1}"
ORDERS_PORT="${ORDERS_PORT:-8085}"
USER_A="${USER_A:-aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa}"
USER_B="${USER_B:-bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb}"
TICK="E2CANTEST$(date +%s)"

echo "NET=$PRIVATE_NET HOST=$ORDERS_HOST PORT=$ORDERS_PORT TICK=$TICK"

# helper: dockerized curl on the compose network (fail if HTTP >= 400)
net_curl() {
  docker run -i --rm --network "$PRIVATE_NET" curlimages/curl:8.9.1 \
    -sS --fail -X "$1" "$2" -H "Content-Type: application/json" "${@:3}"
}

# 1) Place resting SELL
SELL_JSON="$(net_curl POST "http://${ORDERS_HOST}:${ORDERS_PORT}/orders" \
  --data @- <<JSON
{"userId":"$USER_A","ticker":"$TICK","side":"SELL","type":"LIMIT","timeInForce":"GTC","price":150.25,"quantity":40}
JSON
)"
echo "SELL_JSON=$SELL_JSON"
SELL_ID="$(printf '%s' "$SELL_JSON" | jq -r .id)"
if [[ -z "$SELL_ID" || "$SELL_ID" == "null" ]]; then
  echo "❌ Failed to create SELL. Raw response above. Check ORDERS_* and service health." >&2
  exit 1
fi
echo "SELL_ID=$SELL_ID"

# 2) Arm one-shot consumer for cancel (print p/o/key/value)
docker compose exec -T redpanda sh -lc '
  rm -f /tmp/can.out;
  rpk topic consume order.cancelled.v1 --offset end -f "%p %o %k %v" -n 1 > /tmp/can.out 2>/dev/null &
  echo $! > /tmp/can.pid
'

# 3) Issue cancel
net_curl POST "http://${ORDERS_HOST}:${ORDERS_PORT}/orders/${SELL_ID}/cancel" >/dev/null
echo "Cancel requested."

# 4) Print the actual cancel record (partition/offset/key/value)
docker compose exec -T redpanda sh -lc \
  "timeout 10 sh -c 'while [ ! -s /tmp/can.out ]; do sleep 0.2; done; cat /tmp/can.out'"

# 5) Verify engine lag==0 for ALL partitions on order.cancelled.v1
if ! docker compose exec -T redpanda sh -lc \
  "rpk group describe matching-engine | awk '\$1==\"order.cancelled.v1\" {s+=\$6} END{exit !(s==0)}'"; then
  echo "❌ matching-engine still has lag on order.cancelled.v1; not safe to proceed." >&2
  docker compose exec -T redpanda rpk group describe matching-engine || true
  exit 1
fi
echo "Cancel topic lag is 0."

# 6) Send crossing BUY
net_curl POST "http://${ORDERS_HOST}:${ORDERS_PORT}/orders" --data @- <<JSON >/dev/null
{"userId":"$USER_B","ticker":"$TICK","side":"BUY","type":"LIMIT","timeInForce":"GTC","price":150.25,"quantity":40}
JSON
echo "BUY posted."

# 7) See if any trade fires (use shell timeout, not rpk -t)
if docker compose exec -T redpanda sh -lc \
  "timeout 5 rpk topic consume trade.executed.v1 --offset end -f '%p %o %k %v' -n 1"; then
  echo "⚠️ A trade executed (above). If sellOrderId == $SELL_ID, engine processed BUY before cancel apply."
else
  echo "✅ No trade observed within 5s — cancel likely applied before BUY."
fi
