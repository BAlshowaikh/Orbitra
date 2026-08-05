-- V2__create_flight_table.sql
-- Matches Flight.java exactly - Hibernate is set to validate (not update), so
-- this is the single source of truth for the table shape.
-- owner_id is Account.id from auth-service, linked only by sharing that
-- value - no cross-database foreign key, since each service owns its own
-- database (same pattern as hotel.owner_id in hotel-service).
-- A Flight row is a single dated departure, not a recurring schedule -
-- flight_number is unique for that reason (a real-world identifier for one
-- specific route+schedule, unlike hotel.name). departure_time/arrival_time
-- are plain TIMESTAMP (no timezone), matching LocalDateTime - distinct from
-- created_at's TIMESTAMPTZ, which maps Instant.

CREATE TABLE flight (
    id BIGSERIAL PRIMARY KEY,
    owner_id BIGINT NOT NULL,
    flight_number VARCHAR(255) NOT NULL UNIQUE,
    origin_code VARCHAR(255) NOT NULL,
    destination_code VARCHAR(255) NOT NULL,
    departure_time TIMESTAMP NOT NULL,
    arrival_time TIMESTAMP NOT NULL,
    duration_minutes INTEGER NOT NULL,
    seat_count INTEGER NOT NULL,
    active BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);
