# TradeStream Microbench Summary

- Orders API: **~1629 req/s**, p50 **11 ms**, p95 **26 ms** (POST /orders, 500 req @ 20 conc, 500/500 2xx).
- Market Data Latest: **~90334 req/s**, p50 **3 ms**, p95 **14 ms** (GET /candles/AAPL/latest?interval=1m, 20000 req @ 400 conc, 20000/20000 2xx).
- Redis cache: hits=39736 | misses=265 | hit_rate=99.3%.
- Kafka/Redpanda snapshot: see `bench_out/kafka.txt`.

Suggested bullets:
- Increased order write throughput to **~1629 req/s (p95 26 ms)** via async eventing and lean validation.
- Delivered **~90334 cached reads/sec (p95 14 ms)** on latest-candle endpoint with Redis “latest” cache and precise evictions.
