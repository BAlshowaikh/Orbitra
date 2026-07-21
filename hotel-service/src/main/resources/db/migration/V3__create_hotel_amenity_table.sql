-- V3__create_hotel_amenity_table.sql
-- Matches Hotel.java's amenities @ElementCollection mapping exactly. One row
-- per amenity string, individually queryable (e.g. "hotels with a pool")
-- without string-parsing a delimited column. No own primary key, same shape
-- Hibernate itself would generate for a simple element collection. Depends
-- on hotel (V2), created after it.

CREATE TABLE hotel_amenity (
    hotel_id BIGINT NOT NULL REFERENCES hotel(id),
    amenity VARCHAR(255) NOT NULL
);
