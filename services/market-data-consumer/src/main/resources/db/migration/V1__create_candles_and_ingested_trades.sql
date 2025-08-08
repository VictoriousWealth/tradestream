-- Table to store aggregated OHLCV candles
CREATE TABLE IF NOT EXISTS candles (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    ticker TEXT NOT NULL,
    interval TEXT NOT NULL, -- '1m' | '5m' | '1h' | '1d'
    bucket_start TIMESTAMPTZ NOT NULL, -- UTC window start
    open  NUMERIC(18,6) NOT NULL,
    high  NUMERIC(18,6) NOT NULL,
    low   NUMERIC(18,6) NOT NULL,
    close NUMERIC(18,6) NOT NULL,
    volume NUMERIC(20,6) NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_candles UNIQUE (ticker, interval, bucket_start)
);

-- Fast lookups for latest buckets per ticker/interval
CREATE INDEX IF NOT EXISTS idx_candles_ticker_interval_bucket
  ON candles (ticker, interval, bucket_start DESC);

-- Track processed trades for idempotency (avoid double-counting)
CREATE TABLE IF NOT EXISTS ingested_trades (
    trade_id UUID PRIMARY KEY,
    ticker   TEXT NOT NULL,
    ts       TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_ingested_trades_ticker_ts
  ON ingested_trades (ticker, ts DESC);
