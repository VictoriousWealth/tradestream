-- Enable pgcrypto for gen_random_uuid()
-- Safe to run multiple times; CREATE EXTENSION IF NOT EXISTS is idempotent.
CREATE EXTENSION IF NOT EXISTS pgcrypto;
