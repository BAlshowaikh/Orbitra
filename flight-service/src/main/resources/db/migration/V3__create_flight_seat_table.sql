-- V3__create_flight_seat_table.sql
-- Matches FlightSeat.java exactly - Hibernate is set to validate (not
-- update), so this is the single source of truth for the table shape.
-- A specific flight's own instance of a seat_class: its own price,
-- inventory, and remaining count. The unique constraint enforces "one
-- seat_class per flight at most once" at the DB level, not just in
-- application code. No capacity column (unlike room) - a seat always holds
-- exactly one passenger. available_count is separate from total_inventory
-- since it changes as bookings consume seats, with no per-date dimension
-- needed (a Flight is already one specific dated departure). Depends on
-- flight (V2) and seat_class (V1), created after both.

CREATE TABLE flight_seat (
    id BIGSERIAL PRIMARY KEY,
    flight_id BIGINT NOT NULL REFERENCES flight(id),
    seat_class_id BIGINT NOT NULL REFERENCES seat_class(id),
    base_price_per_seat NUMERIC(10, 2) NOT NULL,
    total_inventory INTEGER NOT NULL,
    available_count INTEGER NOT NULL,
    active BOOLEAN NOT NULL,
    UNIQUE (flight_id, seat_class_id)
);
