-- One schema per future service (Week 6), so the split is a deployment change, not a data migration.
-- users schema arrives with authentication in Week 5.
CREATE SCHEMA IF NOT EXISTS accounts;
CREATE SCHEMA IF NOT EXISTS transactions;

CREATE TABLE accounts.account (
    id          UUID          PRIMARY KEY,
    owner_name  TEXT          NOT NULL,
    balance     NUMERIC(19,2) NOT NULL CHECK (balance >= 0),  -- backstop for the no-overdraft rule
    created_at  TIMESTAMPTZ   NOT NULL
);

-- No foreign key to accounts.account on purpose: after Week 6 the two tables belong to
-- different services and cannot reference each other.
CREATE TABLE transactions.account_transaction (
    id             UUID          PRIMARY KEY,
    account_id     UUID          NOT NULL,
    type           TEXT          NOT NULL CHECK (type IN ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER_IN', 'TRANSFER_OUT')),
    amount         NUMERIC(19,2) NOT NULL CHECK (amount > 0),
    balance_after  NUMERIC(19,2) NOT NULL,
    transfer_id    UUID,
    created_at     TIMESTAMPTZ   NOT NULL
);

CREATE INDEX account_transaction_account_created_idx
    ON transactions.account_transaction (account_id, created_at DESC);
