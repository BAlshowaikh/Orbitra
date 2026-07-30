-- V2__create_hotel_booking_table.sql
-- Matches HotelBooking.java exactly - Hibernate is set to validate (not
-- update), so this is the single source of truth for the table shape.
-- JOINED inheritance extension table: id is NOT its own sequence - it's a
-- foreign key back to booking(id), sharing the exact same id value as its
-- parent row. Only hotel-specific columns live here; everything common
-- (traveler_id, status, total_price, created_at) stays on the parent.
-- Depends on booking (V1), created after it.

CREATE TABLE hotel_booking (
    id BIGINT PRIMARY KEY REFERENCES booking(id),
    hotel_id BIGINT NOT NULL,
    room_id BIGINT NOT NULL,
    check_in_date DATE NOT NULL,
    check_out_date DATE NOT NULL
);
