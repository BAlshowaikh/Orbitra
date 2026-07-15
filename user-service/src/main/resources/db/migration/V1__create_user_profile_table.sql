-- V1__create_user_profile_table.sql
-- Matches UserProfile.java exactly - Hibernate is set to validate (not
-- update), so this is the single source of truth for the table shape.
-- id is NOT auto-generated: it always equals the account id from
-- auth-service's accounts table, linked only by sharing that value - no
-- cross-database foreign key, since each service owns its own database.

CREATE TABLE user_profile (
    id BIGINT PRIMARY KEY,
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    phone VARCHAR(50),
    address VARCHAR(500),
    date_of_birth DATE,
    profile_photo_url VARCHAR(2048),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
