-- V2__create_hotel_table.sql
-- Matches Hotel.java exactly - Hibernate is set to validate (not update), so
-- this is the single source of truth for the table shape.
-- owner_id is Account.id from auth-service, linked only by sharing that
-- value - no cross-database foreign key, since each service owns its own
-- database (same pattern as user_profile.id in user-service).

CREATE TABLE hotel (
    id BIGSERIAL PRIMARY KEY,
    owner_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    address VARCHAR(500) NOT NULL,
    city VARCHAR(255) NOT NULL,
    country VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);
