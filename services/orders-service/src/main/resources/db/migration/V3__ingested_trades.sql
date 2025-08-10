-- Idempotency table (1 row per tradeId we’ve processed)
CREATE TABLE ingested_trades (
  trade_id UUID PRIMARY KEY,
  order_id UUID NOT NULL,
  ticker   VARCHAR(16) NOT NULL,
  ts       TIMESTAMPTZ NOT NULL
);

-- Helpful index if you want to query by order later
CREATE INDEX IF NOT EXISTS idx_ingested_trades_order ON ingested_trades(order_id);
