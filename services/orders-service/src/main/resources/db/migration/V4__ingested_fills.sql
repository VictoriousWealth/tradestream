-- One row per (order, trade) we have applied
CREATE TABLE IF NOT EXISTS ingested_fills (
  order_id UUID       NOT NULL,
  trade_id UUID       NOT NULL,
  ticker   VARCHAR(16) NOT NULL,
  ts       TIMESTAMPTZ NOT NULL,
  PRIMARY KEY (order_id, trade_id)
);
