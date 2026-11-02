CREATE TABLE vehicles (
  id BIGSERIAL PRIMARY KEY,
  marca VARCHAR(100) NOT NULL,
  modelo VARCHAR(100) NOT NULL,
  ano INTEGER NOT NULL,
  valor NUMERIC(15,2) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE outbox_events (
  id UUID PRIMARY KEY,
  event_type VARCHAR(80) NOT NULL,
  vehicle_id BIGINT NOT NULL,
  payload TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  published_at TIMESTAMPTZ
);
CREATE INDEX idx_outbox_pending ON outbox_events (created_at) WHERE published_at IS NULL;
