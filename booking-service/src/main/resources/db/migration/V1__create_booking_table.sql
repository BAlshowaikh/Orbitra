-- V1__create_booking_table.sql
-- Matches Booking.java exactly - Hibernate is set to validate (not update),
-- so this is the single source of truth for the table shape.
-- Shared parent table for JOINED inheritance (Class Table Inheritance):
-- holds only the fields common to every booking regardless of type.
-- type is Hibernate's own discriminator column, populated automatically from
-- each subclass's @DiscriminatorValue - not something application code
-- writes directly. No FK dependencies, so it's the first table created.

CREATE TABLE booking (
    id BIGSERIAL PRIMARY KEY,
    traveler_id BIGINT NOT NULL,
    type VARCHAR(31) NOT NULL,
    status VARCHAR(255) NOT NULL,
    total_price NUMERIC(10, 2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);
