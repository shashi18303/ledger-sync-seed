a-- V1__initial.sql: Legacy relational ledger store schema
CREATE TABLE IF NOT EXISTS transactions (
    id VARCHAR(64) PRIMARY KEY,
    account_last4 VARCHAR(4) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    direction VARCHAR(8) NOT NULL,
    amount NUMERIC(12, 2) NOT NULL,
    category VARCHAR(16) NOT NULL,
    merchant VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS transaction_sources (
    transaction_id VARCHAR(64) NOT NULL REFERENCES transactions(id),
    message_id VARCHAR(64) NOT NULL,
    PRIMARY KEY (transaction_id, message_id)
);

CREATE INDEX IF NOT EXISTS idx_txn_account ON transactions(account_last4);
CREATE INDEX IF NOT EXISTS idx_txn_occurred_at ON transactions(occurred_at);
