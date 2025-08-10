CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE orders (
    id                UUID PRIMARY KEY,
    user_id           UUID        NOT NULL,
    ticker            VARCHAR(16) NOT NULL,
    side              VARCHAR(8)  NOT NULL,         -- BUY / SELL
    type              VARCHAR(8)  NOT NULL,         -- MARKET / LIMIT
    time_in_force     VARCHAR(16) NOT NULL,         -- IOC / FOK / GTC / DAY
    quantity          NUMERIC(18,6) NOT NULL CHECK (quantity > 0),
    price             NUMERIC(18,6),                -- nullable for MARKET
    status            VARCHAR(24) NOT NULL,         -- NEW, etc.
    created_at        TIMESTAMPTZ NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL,
    version           BIGINT      NOT NULL DEFAULT 0
);

CREATE INDEX idx_orders_user ON orders(user_id);
CREATE INDEX idx_orders_ticker ON orders(ticker);
