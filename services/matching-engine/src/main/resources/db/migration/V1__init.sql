CREATE TABLE IF NOT EXISTS resting_orders (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    ticker VARCHAR(16) NOT NULL,
    side VARCHAR(4) NOT NULL,
    order_type VARCHAR(10) NOT NULL,
    time_in_force VARCHAR(10) NOT NULL,
    price NUMERIC(18,8),
    original_quantity NUMERIC(18,8) NOT NULL,
    remaining_quantity NUMERIC(18,8) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_resting_orders_active ON resting_orders (ticker, side, status);

CREATE TABLE IF NOT EXISTS processed_messages (
    message_id UUID PRIMARY KEY,
    received_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
