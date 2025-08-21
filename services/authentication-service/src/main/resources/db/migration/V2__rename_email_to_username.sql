-- src/main/resources/db/migration/V2__rename_email_to_username.sql
ALTER TABLE users RENAME COLUMN email TO username;

-- ensure uniqueness on the new column name
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes WHERE tablename = 'users' AND indexname = 'users_username_key'
    ) THEN
        ALTER TABLE users ADD CONSTRAINT users_username_key UNIQUE (username);
    END IF;
END $$;

-- give id a default generator so inserts from SQL work too
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
ALTER TABLE users ALTER COLUMN id SET DEFAULT gen_random_uuid();
