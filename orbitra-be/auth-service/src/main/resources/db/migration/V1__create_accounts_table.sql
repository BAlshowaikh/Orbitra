-- V1__create_accounts_table.sql
-- Matches Account.java exactly - Hibernate is set to validate (not update),
-- so this is now the single source of truth for the accounts table shape.

CREATE TABLE accounts (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(255) NOT NULL
        CHECK (role IN ('TRAVELER', 'PARTNER', 'ADMIN')),
    partner_type VARCHAR(255)
        CHECK (partner_type IN ('HOTEL', 'FLIGHT')),
    enabled BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);
