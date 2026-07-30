-- V3__create_flight_booking_table.sql
-- Matches FlightBooking.java exactly - Hibernate is set to validate (not
-- update), so this is the single source of truth for the table shape.
-- Same JOINED-inheritance shape as hotel_booking (V2): id is a foreign key
-- back to booking(id), not its own sequence. Depends on booking (V1),
-- created after it.

CREATE TABLE flight_booking (
    id BIGINT PRIMARY KEY REFERENCES booking(id),
    flight_id BIGINT NOT NULL,
    flight_seat_id BIGINT NOT NULL
);
