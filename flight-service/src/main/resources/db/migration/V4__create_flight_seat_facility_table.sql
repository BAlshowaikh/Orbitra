-- V4__create_flight_seat_facility_table.sql
-- Matches FlightSeat.java's facilities @ElementCollection mapping exactly.
-- Seat-level (e.g. "extra legroom", "meal included") - this service has no
-- flight-level equivalent to hotel_amenity, since in-flight perks map more
-- naturally to the seat class than the route itself. Depends on flight_seat
-- (V3), created after it.

CREATE TABLE flight_seat_facility (
    flight_seat_id BIGINT NOT NULL REFERENCES flight_seat(id),
    facility VARCHAR(255) NOT NULL
);
