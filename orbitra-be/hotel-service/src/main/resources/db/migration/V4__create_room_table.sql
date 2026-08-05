-- V4__create_room_table.sql
-- Matches Room.java exactly - Hibernate is set to validate (not update), so
-- this is the single source of truth for the table shape.
-- A specific hotel's own instance of a room_type: its own capacity, price,
-- and inventory. The unique constraint enforces "one room_type per hotel at
-- most once" at the DB level, not just in application code. Depends on
-- hotel (V2) and room_type (V1), created after both.

CREATE TABLE room (
    id BIGSERIAL PRIMARY KEY,
    hotel_id BIGINT NOT NULL REFERENCES hotel(id),
    room_type_id BIGINT NOT NULL REFERENCES room_type(id),
    capacity INTEGER NOT NULL,
    base_price_per_night NUMERIC(10, 2) NOT NULL,
    total_inventory INTEGER NOT NULL,
    active BOOLEAN NOT NULL,
    UNIQUE (hotel_id, room_type_id)
);
