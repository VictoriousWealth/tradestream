-- Create transactions table
CREATE TABLE transactions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    ticker VARCHAR(10) NOT NULL,
    quantity INT NOT NULL, -- positive for buy, negative for sell
    price DECIMAL(18,4) NOT NULL,
    type VARCHAR(10) CHECK (type IN ('BUY', 'SELL')) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Indexes for better querying
CREATE INDEX idx_transactions_user_id ON transactions(user_id);
CREATE INDEX idx_transactions_ticker ON transactions(ticker);

-- Create portfolio table
CREATE TABLE portfolio (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    ticker VARCHAR(10) NOT NULL,
    quantity INT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    
    CONSTRAINT uq_user_ticker UNIQUE (user_id, ticker)
);

-- Index for fast lookup
CREATE INDEX idx_portfolio_user_id ON portfolio(user_id);
