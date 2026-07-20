-- V1__create_room_type_table.sql
-- Matches RoomType.java exactly - Hibernate is set to validate (not update),
-- so this is the single source of truth for the table shape.
-- Admin-managed, global room type vocabulary (e.g. "Deluxe King") - hotel
-- partners pick from this rather than typing a free-text name. No FK
-- dependencies, so it's the first table created.

CREATE TABLE room_type (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    active BOOLEAN NOT NULL
);
