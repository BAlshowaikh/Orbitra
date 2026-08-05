-- V1__create_seat_class_table.sql
-- Matches SeatClass.java exactly - Hibernate is set to validate (not update),
-- so this is the single source of truth for the table shape.
-- Admin-managed, global seat class vocabulary (e.g. "Economy", "Business") -
-- flight partners pick from this rather than typing a free-text name. No FK
-- dependencies, so it's the first table created.

CREATE TABLE seat_class (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    active BOOLEAN NOT NULL
);
