-- 1) Add columns if missing
ALTER TABLE processed_messages
  ADD COLUMN IF NOT EXISTS topic varchar(200);

ALTER TABLE processed_messages
  ADD COLUMN IF NOT EXISTS id bigserial;


-- 3) Make 'id' the primary key (drop old PK on message_id if present)
DO $$
BEGIN
  IF EXISTS (
    SELECT 1
      FROM pg_constraint c
      JOIN pg_class t ON t.oid = c.conrelid
     WHERE t.relname = 'processed_messages'
       AND c.contype = 'p'
  ) THEN
    -- Drop whatever the current PK message_id
    EXECUTE (
      SELECT 'ALTER TABLE processed_messages DROP CONSTRAINT ' || quote_ident(c.conname)
        FROM pg_constraint c
        JOIN pg_class t ON t.oid = c.conrelid
       WHERE t.relname = 'processed_messages'
         AND c.contype = 'p'
      LIMIT 1
    );
  END IF;
END $$;

ALTER TABLE processed_messages
  ALTER COLUMN id SET NOT NULL;

ALTER TABLE processed_messages
  ADD CONSTRAINT processed_messages_pkey PRIMARY KEY (id);

-- 4) Enforce NOT NULL on topic
ALTER TABLE processed_messages
  ALTER COLUMN topic SET NOT NULL;

-- 5) Add unique (topic, message_id)
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
      FROM pg_constraint
     WHERE conname = 'uk_processed_topic_msgid'
  ) THEN
    ALTER TABLE processed_messages
      ADD CONSTRAINT uk_processed_topic_msgid UNIQUE (topic, message_id);
  END IF;
END $$;

-- (Optional) index to speed up existence checks (the unique constraint already indexes both columns)
CREATE INDEX IF NOT EXISTS ix_processed_topic_msgid ON processed_messages(topic, message_id);
