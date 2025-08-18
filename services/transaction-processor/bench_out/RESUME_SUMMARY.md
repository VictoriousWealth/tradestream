# TradeStream Microbench Summary

- Orders API: **~504 req/s**, p50 **35 ms**, p95 **80 ms** (POST /orders, 500 req @ 20 conc, 500/500 2xx).
- Market Data Latest: **~1414 req/s**, p50 **16 ms**, p95 **61 ms** (GET /candles/AAPL/latest?interval=1m, 2000 req @ 40 conc, 2000/2000 2xx).
- Redis cache: hits=1960 | misses=40 | hit_rate=98.0%.
- Kafka/Redpanda snapshot: see `bench_out/kafka.txt`.

Suggested bullets:
- Increased order write throughput to **~504 req/s (p95 80 ms)** via async eventing and lean validation.
- Delivered **~1414 cached reads/sec (p95 61 ms)** on latest-candle endpoint with Redis “latest” cache and precise evictions.
