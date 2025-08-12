-- Remove legacy idempotency table no longer in use
DROP TABLE IF EXISTS ingested_trades;
-- (Dropping the table also drops idx_ingested_trades_order automatically)
