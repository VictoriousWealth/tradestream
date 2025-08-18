#!/usr/bin/env bash
set -euo pipefail

RED='\033[0;31m'; GRN='\033[0;32m'; YLW='\033[1;33m'; NC='\033[0m'
info(){ echo -e "${YLW}⇒ $*${NC}"; }
ok(){ echo -e "${GRN}✔ $*${NC}"; }
fail(){ echo -e "${RED}✘ $*${NC}"; exit 1; }

# --- config / helpers ---
PRIVATE_NET="$(docker network ls --format '{{.Name}}' | grep -E '_private_net$' | head -n1 || true)"
[[ -z "$PRIVATE_NET" ]] && PRIVATE_NET="tradestream_private_net"

# Prefer service DNS names (more stable than container names)
ORDERS_HOST="${ORDERS_HOST:-orders-service}"
ME_HOST="${ME_HOST:-matching-engine}"
MDS_HOST="${MDS_HOST:-market-data-consumer}"
TX_HOST="${TX_HOST:-transaction-processor}"

ORDERS_PORT=8085
ME_PORT=8086
MDS_PORT=8083
TX_PORT=8084

USER_A="aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
USER_B="bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"

net_curl() {
  local method="$1"; shift
  docker run -i --rm --network "${PRIVATE_NET}" curlimages/curl:8.9.1 -sS -X "$method" "$@"
}

# generic topic watcher (grabs the *next* message after arming)
arm_topic_watcher() {
  local topic="$1" watch_file="$2"
  docker compose exec -T redpanda sh -lc "rm -f ${watch_file}; \
    rpk topic consume ${topic} --offset end -f '%v' -n 1 > ${watch_file} 2>/dev/null & echo \$! > /tmp/e2e_watch.pid"
}

arm_trade_watcher() { arm_topic_watcher "trade.executed.v1" "$1"; }

wait_trade_or_timeout() {
  local watch_file="$1" max_halfsecs="${2:-40}"
  for i in $(seq 1 "$max_halfsecs"); do
    if docker compose exec -T redpanda sh -lc "[ -s ${watch_file} ]"; then
      docker compose exec -T redpanda sh -lc "cat ${watch_file}"
      return 0
    fi
    sleep 0.5
  done
  return 1
}

# wait until our *specific* cancel (orderId) appears on order.cancelled.v1
wait_for_cancel_published() {
  local order_id="$1" watch_file="$2" tries="${3:-60}"
  for i in $(seq 1 "$tries"); do
    if docker compose exec -T redpanda sh -lc "[ -s ${watch_file} ]"; then
      local msg
      msg="$(docker compose exec -T redpanda sh -lc "cat ${watch_file}")"
      local seen
      seen="$(echo "$msg" | jq -r '.orderId // empty' || true)"
      if [[ "$seen" == "$order_id" ]]; then
        echo "$msg"
        return 0
      fi
      # wrong message (some other cancel landed first); re-arm and keep waiting
      arm_topic_watcher "order.cancelled.v1" "$watch_file"
    fi
    sleep 0.5
  done
  return 1
}

# poll rpk until matching-engine has zero lag on a topic (i.e., caught up)
wait_me_caught_up() {
  local topic="$1" timeout="${2:-60}"
  for i in $(seq 1 "$timeout"); do
    local lag
    lag="$(docker compose exec -T redpanda rpk group describe matching-engine 2>/dev/null \
      | awk -v t="$topic" '$1==t {print $6}' | tail -n1)"
    if [[ "$lag" == "0" ]]; then return 0; fi
    sleep 1
  done
  return 1
}

# --- TX-PROCESSOR helpers ---
wait_tx_health() {
  for i in {1..60}; do
    if net_curl GET "http://${TX_HOST}:${TX_PORT}/actuator/health" >/dev/null; then return 0; fi
    sleep 1
  done
  return 1
}

# Wait until txproc consumer group is caught up on trade.executed.v1
wait_txproc_caught_up() {
  local timeout="${1:-60}"
  for i in $(seq 1 "$timeout"); do
    local lag
    lag="$(docker compose exec -T redpanda rpk group describe txproc-journal 2>/dev/null \
      | awk '$1=="trade.executed.v1"{print $6}' | tail -n1)"
    [[ "$lag" == "0" ]] && return 0
    sleep 1
  done
  return 1
}

# Returns totalElements for user+ticker page query
tx_count_user_ticker() {
  local user="$1" ticker="$2"
  net_curl GET "http://${TX_HOST}:${TX_PORT}/api/transactions/${user}/ticker/${ticker}?page=0&size=1" \
    | jq -r '.totalElements'
}

# Fetch a page (for debugging)
tx_list_user_ticker() {
  local user="$1" ticker="$2" size="${3:-10}"
  net_curl GET "http://${TX_HOST}:${TX_PORT}/api/transactions/${user}/ticker/${ticker}?page=0&size=${size}"
}

post_order() {
  # args: side type tif price quantity ticker user
  local side="$1" type="$2" tif="$3" price="$4" qty="$5" ticker="$6" user="$7"
  local payload
  if [[ "$type" == "MARKET" ]]; then
    payload=$(jq -n --arg user "$user" --arg tick "$ticker" --arg side "$side" --arg type "$type" --arg tif "$tif" --argjson qty "$qty" \
      '{userId:$user,ticker:$tick,side:$side,type:$type,timeInForce:$tif,price:null,quantity:$qty}')
  else
    payload=$(jq -n --arg user "$user" --arg tick "$ticker" --arg side "$side" --arg type "$type" --arg tif "$tif" --argjson price "$price" --argjson qty "$qty" \
      '{userId:$user,ticker:$tick,side:$side,type:$type,timeInForce:$tif,price:$price,quantity:$qty}')
  fi
  local resp; resp="$(echo "$payload" | net_curl POST "http://${ORDERS_HOST}:${ORDERS_PORT}/orders" -H "Content-Type: application/json" --data @-)"
  echo "$resp" | jq -e . >/dev/null || { echo "$resp"; fail "Orders response was not JSON"; }
  local id; id="$(echo "$resp" | jq -r .id)"
  [[ "$id" == "null" || -z "$id" ]] && { echo "$resp"; fail "Order id missing"; }
  echo "$id"
}

get_order() {
  local id="$1"
  net_curl GET "http://${ORDERS_HOST}:${ORDERS_PORT}/orders/${id}"
}

assert_eq() { [[ "$1" == "$2" ]] || fail "assert failed: expected [$2] got [$1] ($3)"; }
assert_num_eq() { awk "BEGIN{exit !($1==$2)}" || fail "assert failed: expected [$2] got [$1] ($3)"; }

# --- bring up core services (now includes tx-proc + its postgres) ---
info "Using docker network: ${PRIVATE_NET}"
docker compose up -d orders_postgres matching_postgres market_postgres postgres redis redpanda \
  orders-service matching-engine market-data-consumer \
  transaction_postgres transaction-processor >/dev/null

# health
info "Waiting for health..."
for i in {1..60}; do
  set +e
  net_curl GET "http://${ORDERS_HOST}:${ORDERS_PORT}/actuator/health" >/dev/null && O=ok || O=
  net_curl GET "http://${ME_HOST}:${ME_PORT}/actuator/health"      >/dev/null && M=ok || M=
  net_curl GET "http://${MDS_HOST}:${MDS_PORT}/actuator/health"    >/dev/null && D=ok || D=
  net_curl GET "http://${TX_HOST}:${TX_PORT}/actuator/health"      >/dev/null && T=ok || T=
  set -e
  [[ "$O$M$D$T" == "okokokok" ]] && break
  sleep 2
done
ok "All healthy."

# Unique tickers to isolate scenarios
SUF="$(date +%s)"
T_PF="E2EPF${SUF}"
T_IOC1="E2IOC1${SUF}"
T_IOC0="E2IOC0${SUF}"
T_FOK="E2FOK${SUF}"
T_MKT="E2MKT${SUF}"
T_CAN="E2CAN${SUF}"
T_RCV="E2RCV${SUF}"
T_DLT="E2DLT${SUF}"

# =============== 1) PARTIAL FILL =================
info "[1/9] Partial fill (${T_PF}): SELL 80 then BUY 50 @150.25"
WATCH="/tmp/pf_trade.json"; arm_trade_watcher "$WATCH"
SELL80_ID="$(post_order SELL LIMIT GTC 150.25 80 "$T_PF" "$USER_A")"
BUY50_ID="$(post_order BUY  LIMIT GTC 150.25 50 "$T_PF" "$USER_B")"
TR_PF="$(wait_trade_or_timeout "$WATCH" 60 || fail "No trade for partial-fill")"
echo "$TR_PF" | jq .
qty="$(echo "$TR_PF" | jq -r .quantity)"; price="$(echo "$TR_PF" | jq -r .price)"
assert_num_eq "$qty" 50 "partial fill qty"
assert_num_eq "$price" 150.25 "partial fill price"

S="$(get_order "$SELL80_ID")"; B="$(get_order "$BUY50_ID")"
s_status="$(echo "$S" | jq -r .status)"; s_rem="$(echo "$S" | jq -r .remainingQuantity)"
b_status="$(echo "$B" | jq -r .status)"; b_rem="$(echo "$B" | jq -r .remainingQuantity)"
assert_eq "$s_status" "PARTIALLY_FILLED" "sell status"
assert_num_eq "$s_rem" 30 "sell remaining"
assert_eq "$b_status" "FILLED" "buy status"
assert_num_eq "$b_rem" 0 "buy remaining"
ok "Partial fill OK."

# TX-PROCESSOR CHECK
info "Checking tx-processor ledger for ${T_PF}"
wait_txproc_caught_up 60 || fail "tx-processor not caught up"
for i in {1..30}; do
  cA="$(tx_count_user_ticker "$USER_A" "$T_PF")"
  cB="$(tx_count_user_ticker "$USER_B" "$T_PF")"
  [[ "$cA" == "1" && "$cB" == "1" ]] && break
  sleep 1
done
assert_eq "$cA" "1" "seller ledger row for ${T_PF}"
assert_eq "$cB" "1" "buyer ledger row for ${T_PF}"
ok "tx-processor ledger recorded partial-fill."

# =============== 2) IOC partial ==================
info "[2/9] IOC partial (${T_IOC1}): SELL 10, BUY IOC 50"
WATCH="/tmp/ioc1_trade.json"; arm_trade_watcher "$WATCH"
S10="$(post_order SELL LIMIT GTC 150.25 10 "$T_IOC1" "$USER_A")"
BI50="$(post_order BUY  LIMIT IOC 150.25 50 "$T_IOC1" "$USER_B")"
TR_IOC1="$(wait_trade_or_timeout "$WATCH" 60 || fail "No trade for IOC partial")"
echo "$TR_IOC1" | jq .
qty="$(echo "$TR_IOC1" | jq -r .quantity)"; assert_num_eq "$qty" 10 "IOC matched available"
ok "IOC partial OK."

# TX-PROCESSOR CHECK
info "Checking tx-processor ledger for ${T_IOC1}"
wait_txproc_caught_up 60 || fail "tx-processor not caught up"
for i in {1..20}; do
  cA="$(tx_count_user_ticker "$USER_A" "$T_IOC1")"
  cB="$(tx_count_user_ticker "$USER_B" "$T_IOC1")"
  [[ "$cA" == "1" && "$cB" == "1" ]] && break
  sleep 1
done
assert_eq "$cA" "1" "seller ledger row for ${T_IOC1}"
assert_eq "$cB" "1" "buyer ledger row for ${T_IOC1}"
ok "IOC partial recorded in ledger."

# =============== 3) IOC no-liquidity ============
info "[3/9] IOC no-liquidity (${T_IOC0}): BUY IOC 10 (no rests) => no trade"
WATCH="/tmp/ioc0_trade.json"; arm_trade_watcher "$WATCH"
post_order BUY LIMIT IOC 150.25 10 "$T_IOC0" "$USER_B" >/dev/null
if wait_trade_or_timeout "$WATCH" 10; then fail "Unexpected trade for IOC no-liquidity"; fi
ok "IOC no-liquidity OK (no trade)."

# TX-PROCESSOR CHECK
info "Checking no-ledger for ${T_IOC0}"
cA="$(tx_count_user_ticker "$USER_A" "$T_IOC0")"
cB="$(tx_count_user_ticker "$USER_B" "$T_IOC0")"
assert_eq "$cA" "0" "no trade => no seller row"
assert_eq "$cB" "0" "no trade => no buyer row"
ok "IOC no-liquidity produced no ledger rows."

# =============== 4) FOK insufficient ============
info "[4/9] FOK insufficient (${T_FOK}): SELL 50, then BUY FOK 100 => no trade"
WATCH="/tmp/fok_trade.json"; arm_trade_watcher "$WATCH"
post_order SELL LIMIT GTC 150.25 50 "$T_FOK" "$USER_A" >/dev/null
post_order BUY  LIMIT FOK 150.25 100 "$T_FOK" "$USER_B" >/dev/null
if wait_trade_or_timeout "$WATCH" 10; then fail "Unexpected trade for FOK insufficient"; fi
ok "FOK insufficient OK (no trade)."

# TX-PROCESSOR CHECK
info "Checking no-ledger for ${T_FOK}"
cA="$(tx_count_user_ticker "$USER_A" "$T_FOK")"
cB="$(tx_count_user_ticker "$USER_B" "$T_FOK")"
assert_eq "$cA" "0"; assert_eq "$cB" "0"
ok "FOK insufficient produced no ledger rows."

# =============== 5) MARKET order ================
info "[5/9] MARKET (${T_MKT}): SELL 20@150.30, BUY MARKET 25 => trade 20 @ 150.30"
WATCH="/tmp/mkt_trade.json"; arm_trade_watcher "$WATCH"
post_order SELL LIMIT GTC 150.30 20 "$T_MKT" "$USER_A" >/dev/null
post_order BUY  MARKET GTC 0      25 "$T_MKT" "$USER_B" >/dev/null
TR_MKT="$(wait_trade_or_timeout "$WATCH" 60 || fail "No trade for MARKET")"
echo "$TR_MKT" | jq .
qty="$(echo "$TR_MKT" | jq -r .quantity)"; price="$(echo "$TR_MKT" | jq -r .price)"
assert_num_eq "$qty" 20 "MARKET qty equals available"
assert_num_eq "$price" 150.30 "MARKET executes at resting price"
ok "MARKET behavior OK."

# TX-PROCESSOR CHECK
info "Checking tx-processor ledger for ${T_MKT}"
wait_txproc_caught_up 60 || fail "tx-processor not caught up"
for i in {1..20}; do
  cA="$(tx_count_user_ticker "$USER_A" "$T_MKT")"
  cB="$(tx_count_user_ticker "$USER_B" "$T_MKT")"
  [[ "$cA" == "1" && "$cB" == "1" ]] && break
  sleep 1
done
assert_eq "$cA" "1"; assert_eq "$cB" "1"
ok "MARKET trade recorded in ledger."

# =============== 6) Cancel flow =================
# policy: you do NOT allow cancelling partially-filled orders (only NEW)
info "[6/9] Cancel (${T_CAN}): SELL 40, cancel, then BUY 40 => no trade"
S40="$(post_order SELL LIMIT GTC 150.25 40 "$T_CAN" "$USER_A")"

# Arm cancel watcher BEFORE calling cancel to avoid missing the event
CANCEL_WATCH="/tmp/cancel_evt.json"; arm_topic_watcher "order.cancelled.v1" "$CANCEL_WATCH"

# cancel via Orders API
net_curl POST "http://${ORDERS_HOST}:${ORDERS_PORT}/orders/${S40}/cancel" >/dev/null

# wait until *our* cancel shows on the topic
wait_for_cancel_published "$S40" "$CANCEL_WATCH" 60 || fail "Cancel event not observed on Kafka"

# ensure matching-engine has consumed cancels up to head
wait_me_caught_up "order.cancelled.v1" 60 || fail "Matching engine did not catch up on cancels"

# now place the crossing BUY; there should be NO trade
WATCH="/tmp/can_trade.json"; arm_trade_watcher "$WATCH"
post_order BUY LIMIT GTC 150.25 40 "$T_CAN" "$USER_B" >/dev/null
if wait_trade_or_timeout "$WATCH" 10; then fail "Unexpected trade after cancel"; fi
ok "Cancel flow OK (no trade after cancel)."

# TX-PROCESSOR CHECK
info "Checking no-ledger for ${T_CAN}"
cA="$(tx_count_user_ticker "$USER_A" "$T_CAN")"
cB="$(tx_count_user_ticker "$USER_B" "$T_CAN")"
assert_eq "$cA" "0"; assert_eq "$cB" "0"
ok "Cancel flow produced no ledger rows."

# =============== 7) Idempotency ================
info "[7/9] Idempotency: re-publish previous partial-fill trade, expect no double-apply"
TKEY="$T_PF"
TJSON="$(echo "$TR_PF" | tr -d '\n')"
S_BEFORE="$(get_order "$SELL80_ID")"; B_BEFORE="$(get_order "$BUY50_ID")"
s_filled_pre="$(echo "$S_BEFORE" | jq -r .filledQuantity)"
b_filled_pre="$(echo "$B_BEFORE" | jq -r .filledQuantity)"
printf "%s\n" "$TJSON" | docker compose exec -T redpanda sh -lc "rpk topic produce trade.executed.v1 -k $TKEY >/dev/null"
printf "%s\n" "$TJSON" | docker compose exec -T redpanda sh -lc "rpk topic produce trade.executed.v1 -k $TKEY >/dev/null"
sleep 2
S_AFTER="$(get_order "$SELL80_ID")"; B_AFTER="$(get_order "$BUY50_ID")"
s_filled_post="$(echo "$S_AFTER" | jq -r .filledQuantity)"
b_filled_post="$(echo "$B_AFTER" | jq -r .filledQuantity)"
assert_eq "$s_filled_post" "$s_filled_pre" "sell filled should not change"
assert_eq "$b_filled_post" "$b_filled_pre" "buy filled should not change"
ok "Idempotency OK (duplicate trade ignored)."

# TX-PROCESSOR CHECK
info "Checking idempotency in ledger for ${T_PF}"
cA2="$(tx_count_user_ticker "$USER_A" "$T_PF")"
cB2="$(tx_count_user_ticker "$USER_B" "$T_PF")"
assert_eq "$cA2" "1" "duplicate should not add seller rows"
assert_eq "$cB2" "1" "duplicate should not add buyer rows"
ok "Ledger idempotency OK."

# =============== 8) Recovery ====================
info "[8/9] Recovery: place SELL 15, restart engine, then BUY 15 => trade"
WATCH="/tmp/rcv_trade.json"; arm_trade_watcher "$WATCH"
S15="$(post_order SELL LIMIT GTC 150.25 15 "$T_RCV" "$USER_A")"
docker compose restart matching-engine >/dev/null
for i in {1..60}; do
  net_curl GET "http://${ME_HOST}:${ME_PORT}/actuator/health" >/dev/null && break || true
  sleep 2
done
post_order BUY LIMIT GTC 150.25 15 "$T_RCV" "$USER_B" >/dev/null
TR_RCV="$(wait_trade_or_timeout "$WATCH" 60 || fail "No trade after restart")"
echo "$TR_RCV" | jq .
ok "Recovery warm-start OK."

# TX-PROCESSOR CHECK
info "Checking tx-processor ledger for ${T_RCV}"
wait_txproc_caught_up 60 || fail "tx-processor not caught up"
for i in {1..20}; do
  cA="$(tx_count_user_ticker "$USER_A" "$T_RCV")"
  cB="$(tx_count_user_ticker "$USER_B" "$T_RCV")"
  [[ "$cA" == "1" && "$cB" == "1" ]] && break
  sleep 1
done
assert_eq "$cA" "1"; assert_eq "$cB" "1"
ok "Recovery trade recorded in ledger."

# =============== 9) DLT poison ==================
info "[9/9] DLT: send bad side to order.placed.v1 => should land in order.placed.v1.DLT"
DLT_FILE="/tmp/dlt_msg.json"
docker compose exec -T redpanda sh -lc "rm -f ${DLT_FILE}; \
  rpk topic consume order.placed.v1.DLT --offset end -f '%v' -n 1 > ${DLT_FILE} 2>/dev/null &"
docker compose exec -T redpanda sh -lc "cat <<'JSON' | rpk topic produce order.placed.v1 -k ${T_DLT}
{\"orderId\":\"$(uuidgen)\",\"userId\":\"$USER_A\",\"ticker\":\"${T_DLT}\",\"side\":\"BYYY\",\"type\":\"LIMIT\",\"timeInForce\":\"GTC\",\"price\":150.25,\"quantity\":10}
JSON"
for i in {1..40}; do
  if docker compose exec -T redpanda sh -lc "[ -s ${DLT_FILE} ]"; then
    MSG="$(docker compose exec -T redpanda sh -lc "cat ${DLT_FILE}")"
    echo "$MSG"
    ok "DLT poison captured."
    break
  fi
  sleep 0.5
  [[ $i -eq 40 ]] && fail "Poison message did not appear on DLT."
done

ok "ALL SCENARIOS PASSED 🎉"
