-- Add optimistic locking version column to existing customer requests
ALTER TABLE customer_requests
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
