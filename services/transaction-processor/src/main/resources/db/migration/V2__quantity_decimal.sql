ALTER TABLE transactions
  ALTER COLUMN quantity TYPE DECIMAL(18,6)
  USING quantity::DECIMAL(18,6);
