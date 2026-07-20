-- V6__create_availability_table.sql
-- Matches Availability.java exactly - Hibernate is set to validate (not
-- update), so this is the single source of truth for the table shape.
-- Per-room-per-date remaining inventory count. A missing row for a given
-- (room, date) means "fully available" (falls back to room.total_inventory
-- in application code) - rows only ever get written to override a date, not
-- to pre-seed a calendar. The unique constraint is what makes an upsert-by-
-- range well-defined: each date maps to exactly one row. Depends on room
-- (V4), created after it.

CREATE TABLE availability (
    id BIGSERIAL PRIMARY KEY,
    room_id BIGINT NOT NULL REFERENCES room(id),
    date DATE NOT NULL,
    available_count INTEGER NOT NULL,
    UNIQUE (room_id, date)
);
