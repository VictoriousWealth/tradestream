CREATE TABLE IF NOT EXISTS positions (
  user_id   UUID        NOT NULL,
  ticker    VARCHAR(16) NOT NULL,
  quantity  NUMERIC(18,8) NOT NULL DEFAULT 0,
  avg_cost  NUMERIC(18,8),
  realized_pnl NUMERIC(18,8) NOT NULL DEFAULT 0,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, ticker)
);

CREATE TABLE IF NOT EXISTS processed_messages (
  id BIGSERIAL PRIMARY KEY,
  topic VARCHAR(200) NOT NULL,
  message_id UUID NOT NULL,
  received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uk_processed_topic_msgid UNIQUE (topic, message_id)
);

CREATE INDEX IF NOT EXISTS ix_positions_user ON positions(user_id);
