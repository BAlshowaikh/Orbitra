-- V5__create_room_facility_table.sql
-- Matches Room.java's facilities @ElementCollection mapping exactly.
-- Room-level (e.g. "WiFi", "TV"), distinct from hotel_amenity (V3) which is
-- hotel-wide. Depends on room (V4), created after it.

CREATE TABLE room_facility (
    room_id BIGINT NOT NULL REFERENCES room(id),
    facility VARCHAR(255) NOT NULL
);
