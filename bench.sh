#!/usr/bin/env bash
set -euo pipefail

# --- pretty ---
Y='\033[1;33m'; G='\033[0;32m'; R='\033[0;31m'; N='\033[0m'
say(){ echo -e "${Y}$*${N}"; }
ok(){  echo -e "${G}$*${N}"; }
die(){ echo -e "${R}$*${N}"; exit 1; }
trap 'die "bench failed on line $LINENO"' ERR

# --- knobs ---
N_WRITE=${N_WRITE:-500}
C_WRITE=${C_WRITE:-20}
N_READ=${N_READ:-2000}
C_READ=${C_READ:-40}

ORDERS_HOST=${ORDERS_HOST:-orders-service}
MDS_HOST=${MDS_HOST:-market-data-consumer}
REDPANDA_SVC=${REDPANDA_SVC:-redpanda}
REDIS_CTN=${REDIS_CTN:-tradestream_redis}

ORDERS_URL="http://${ORDERS_HOST}:8085/orders"
LATEST_URL="http://${MDS_HOST}:8083/candles/AAPL/latest?interval=1m"

CURL_IMG="curlimages/curl:8.9.1"

USER_A="aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
USER_B="bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
PRICE=${PRICE:-150.25}

# --- detect compose private net ---
detect_net() {
  local n
  n="$(docker network ls --format '{{.Name}}' | grep -E '_private_net$' | head -n1 || true)"
  [[ -z "$n" ]] && n="tradestream_private_net"
  echo "$n"
}
NET="${NET:-$(detect_net)}"

ncurl() { docker run --rm --network "$NET" "$CURL_IMG" -sS "$@"; }

# --- tiny load runner (no 'hey' needed) ---
# emits lines: "<http_code> <time_total_seconds>"
curl_blast() { # args: TOTAL CONC METHOD URL [JSON]
  local TOTAL="$1" CONC="$2" METHOD="$3" URL="$4" JSON="${5:-}"
  local shcmd='
set -e
TOTAL='"$TOTAL"'
CONC='"$CONC"'
URL="'"$URL"'"
METHOD="'"$METHOD"'"
JSON=$(printf "%s" "'"$(printf "%s" "$JSON" | sed "s/'/'\\\\''/g")"'")
if [ -n "$JSON" ]; then echo "$JSON" >/tmp/payload.json; fi
seq 1 $TOTAL | xargs -n1 -P $CONC -I{} sh -c "
  if [ -n \"$JSON\" ]; then
    curl -s -o /dev/null -w \"%{http_code} %{time_total}\n\" -X $METHOD -H \"Content-Type: application/json\" --data-binary @/tmp/payload.json \"$URL\"
  else
    curl -s -o /dev/null -w \"%{http_code} %{time_total}\n\" \"$URL\"
  fi
"
'
  docker run --rm --network "$NET" "$CURL_IMG" sh -lc "$shcmd"
}

# parse stats from curl_blast output file
# file lines: "CODE TIME_S" ; prints: ok|p50_ms|p95_ms|rps_est
summarize_file() {
  local file="$1" conc="$2"
  local n ok avg p50_idx p95_idx p50 p95 p50_ms p95_ms rps

  n="$(wc -l < "$file" | tr -d ' ')"
  ok="$(awk '$1 ~ /^2/ {c++} END{print c+0}' "$file")"

  if [ "${n:-0}" -eq 0 ]; then
    echo "0|nan|nan|0"
    return
  fi

  avg="$(awk '{s+=$2} END{if(NR>0) printf "%.6f", s/NR; else print "0"}' "$file")"

  # 1-based “ceil” indices for percentiles
  p50_idx=$(( (n*50 + 99)/100 ))
  p95_idx=$(( (n*95 + 99)/100 ))

  p50="$(awk '{print $2}' "$file" | sort -n | sed -n "${p50_idx}p")"
  p95="$(awk '{print $2}' "$file" | sort -n | sed -n "${p95_idx}p")"

  p50_ms="$(awk -v t="$p50" 'BEGIN{printf "%d", (t*1000)+0.5}')"
  p95_ms="$(awk -v t="$p95" 'BEGIN{printf "%d", (t*1000)+0.5}')"
  rps="$(awk -v C="$conc" -v A="$avg" 'BEGIN{ if (A>0) printf "%.0f", C/A; else print 0 }')"

  echo "${ok}|${p50_ms}|${p95_ms}|${rps}"
}


mkdir -p bench_out
: > bench_out/kafka.txt
: > bench_out/redis_summary.txt

say "[sanity] health checks (inside network: ${NET})"
ncurl -o /dev/null -w "%{http_code}\n" "http://${ORDERS_HOST}:8085/actuator/health" | tail -n1
ncurl -o /dev/null -w "%{http_code}\n" "http://${MDS_HOST}:8083/actuator/health" | tail -n1

# --- seed one crossing trade so /latest is warm ---
say "[seed] generating one candle for AAPL (crossing orders)"
ncurl -X POST -H 'Content-Type: application/json' \
  -d "{\"userId\":\"${USER_A}\",\"ticker\":\"AAPL\",\"side\":\"SELL\",\"type\":\"LIMIT\",\"price\":${PRICE},\"timeInForce\":\"GTC\",\"quantity\":10}" \
  "$ORDERS_URL" >/dev/null || true
sleep 0.8
ncurl -X POST -H 'Content-Type: application/json' \
  -d "{\"userId\":\"${USER_B}\",\"ticker\":\"AAPL\",\"side\":\"BUY\",\"type\":\"LIMIT\",\"price\":${PRICE},\"timeInForce\":\"GTC\",\"quantity\":10}" \
  "$ORDERS_URL" >/dev/null || true
sleep 1.2

# --- ORDERS write load ---
say "[orders] ${N_WRITE} req @ conc=${C_WRITE} -> ${ORDERS_URL}"
ORDERS_RAW="bench_out/orders_raw.txt"

docker run --rm --network "$NET" "$CURL_IMG" sh -lc '
  set -e
  TOTAL='"$N_WRITE"'; CONC='"$C_WRITE"';
  URL="'"$ORDERS_URL"'";
  seq 1 $TOTAL | xargs -n1 -P $CONC -I{} sh -c "
    oid=\$(cat /proc/sys/kernel/random/uuid)
    cat <<JSON | curl -s -o /dev/null -w \"%{http_code} %{time_total}\n\" \
      -X POST -H \"Content-Type: application/json\" --data-binary @- \"$URL\"
{\"orderId\":\"\$oid\",\"userId\":\"'"$USER_A"'\",\"ticker\":\"AAPL\",\"side\":\"BUY\",\"type\":\"LIMIT\",\"timeInForce\":\"GTC\",\"price\":'"$PRICE"',\"quantity\":1}
JSON
  "
' | tee "$ORDERS_RAW" >/dev/null

IFS='|' read -r O_OK O_P50 O_P95 O_RPS <<< "$(summarize_file "$ORDERS_RAW" "$C_WRITE")"

# --- MARKET latest read load ---
say "[market] ${N_READ} req @ conc=${C_READ} -> ${LATEST_URL}"
MARKET_RAW="bench_out/market_raw.txt"
curl_blast "$N_READ" "$C_READ" GET "$LATEST_URL" \
  | tee "$MARKET_RAW" >/dev/null
IFS='|' read -r M_OK M_P50 M_P95 M_RPS <<< "$(summarize_file "$MARKET_RAW" "$C_READ")"

# --- Redis stats ---
say "[redis] stats"
if docker ps --format '{{.Names}}' | grep -q "^${REDIS_CTN}$"; then
  docker exec "$REDIS_CTN" redis-cli INFO stats > bench_out/redis_info.txt || true
  HITS=$(awk -F: '/keyspace_hits:/{print $2}' bench_out/redis_info.txt | tr -d '\r')
  MISSES=$(awk -F: '/keyspace_misses:/{print $2}' bench_out/redis_info.txt | tr -d '\r')
  HITS=${HITS:-0}; MISSES=${MISSES:-0}
  if [[ $((HITS+MISSES)) -gt 0 ]]; then
    RATE=$(awk -v h="$HITS" -v m="$MISSES" 'BEGIN{printf "%.1f", (h/(h+m))*100}')
  else
    RATE="n/a"
  fi
  echo "hits=${HITS} | misses=${MISSES} | hit_rate=${RATE}%" | tee bench_out/redis_summary.txt
else
  echo "redis container not found (${REDIS_CTN})" | tee bench_out/redis_summary.txt
fi

# --- Kafka snapshot ---
say "[kafka] rpk snapshot"
{
  echo "== cluster info ==" &&
  docker compose exec -T "$REDPANDA_SVC" rpk -X brokers=redpanda:9092 cluster info 2>/dev/null || true
  echo
  echo "== topics ==" &&
  docker compose exec -T "$REDPANDA_SVC" rpk -X brokers=redpanda:9092 topic list 2>/dev/null || true
  echo
  echo "== groups ==" &&
  for g in matching-engine md-consumer txproc-journal; do
    docker compose exec -T "$REDPANDA_SVC" rpk -X brokers=redpanda:9092 group describe "$g" 2>/dev/null || true
    echo
  done
} | sed 's/\r$//' > bench_out/kafka.txt || echo "redpanda snapshot skipped" >> bench_out/kafka.txt

# --- console summary (compact) ---
echo "orders_ok=${O_OK}/${N_WRITE} | p50=${O_P50}ms | p95=${O_P95}ms | ~rps≈${O_RPS}"
echo "market_ok=${M_OK}/${N_READ}  | p50=${M_P50}ms | p95=${M_P95}ms | ~rps≈${M_RPS}"

if [ "${O_OK:-0}" -lt "$N_WRITE" ] && [ -s "$ORDERS_RAW" ]; then
  echo "[orders] status histogram (top 5):"
  awk '{print $1}' "$ORDERS_RAW" | sort | uniq -c | sort -nr | head -n5 | sed 's/^/[orders] /'
fi
if [ "${M_OK:-0}" -lt "$N_READ" ] && [ -s "$MARKET_RAW" ]; then
  echo "[market] status histogram (top 5):"
  awk '{print $1}' "$MARKET_RAW" | sort | uniq -c | sort -nr | head -n5 | sed 's/^/[market] /'
fi

# --- résumé-ready markdown ---
cat > bench_out/RESUME_SUMMARY.md <<EOF
# TradeStream Microbench Summary

- Orders API: **~${O_RPS} req/s**, p50 **${O_P50} ms**, p95 **${O_P95} ms** (POST /orders, ${N_WRITE} req @ ${C_WRITE} conc, ${O_OK}/${N_WRITE} 2xx).
- Market Data Latest: **~${M_RPS} req/s**, p50 **${M_P50} ms**, p95 **${M_P95} ms** (GET /candles/AAPL/latest?interval=1m, ${N_READ} req @ ${C_READ} conc, ${M_OK}/${N_READ} 2xx).
- Redis cache: $(cat bench_out/redis_summary.txt 2>/dev/null || echo 'n/a').
- Kafka/Redpanda snapshot: see \`bench_out/kafka.txt\`.

Suggested bullets:
- Increased order write throughput to **~${O_RPS} req/s (p95 ${O_P95} ms)** via async eventing and lean validation.
- Delivered **~${M_RPS} cached reads/sec (p95 ${M_P95} ms)** on latest-candle endpoint with Redis “latest” cache and precise evictions.
EOF

say "----------------------------------------------------------------------"
say "Résumé-ready summary -> ./bench_out/RESUME_SUMMARY.md"
say "----------------------------------------------------------------------"
