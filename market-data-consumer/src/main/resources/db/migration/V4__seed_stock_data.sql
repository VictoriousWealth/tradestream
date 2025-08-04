INSERT INTO stock_data (
    id, name, ticker, date, open, high, low, close, volume, created_at, updated_at
) VALUES (
    gen_random_uuid(), 'Apple Inc.', 'AAPL', '2025-08-01',
    195.00, 198.00, 194.50, 196.20, 10000000,
    now(), now()
);

INSERT INTO stock_data (
    id, name, ticker, date, open, high, low, close, volume, created_at, updated_at
) VALUES (
    gen_random_uuid(), 'Tesla Inc.', 'TSLA', '2025-08-01',
    250.00, 260.00, 249.00, 255.30, 8000000,
    now(), now()
);
