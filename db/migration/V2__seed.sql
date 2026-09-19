-- V2__seed.sql: Seed schema initialization for tests
-- Ensures idempotency when testing backfill across environments
CREATE INDEX IF NOT EXISTS idx_txn_sources_msg ON transaction_sources(message_id);
