/*
  Room.java
  JPA entity for a specific hotel's instance of a RoomType catalog entry -
  holds the hotel-specific capacity, price, inventory, and facilities for that
  room type, while the RoomType entity itself only holds the shared
  name/description.
*/
package com.orbitra.hotel_service.model;

// ------------------- IMPORTS -------------------
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "room",
        // A hotel can add a given room type at most once - matches a plain
        // dropdown-selection UX, no need to disambiguate two rows with the
        // same room type at the same hotel.
        uniqueConstraints = @UniqueConstraint(columnNames = {"hotel_id", "room_type_id"})
)
@Getter
@Setter
@NoArgsConstructor // required by JPA to instantiate entities via reflection
@AllArgsConstructor
@Builder
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // LAZY - loading a Room shouldn't force-load its parent Hotel unless
    // something actually asks for it (e.g. ownership checks do, explicitly).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hotel_id", nullable = false)
    private Hotel hotel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_type_id", nullable = false)
    private RoomType roomType;

    @Column(nullable = false)
    private Integer capacity;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal basePricePerNight;

    // Used only to seed Availability rows when a partner first sets up a date
    // range - the actual per-date remaining count lives in Availability, not
    // here, since the two can diverge once bookings start consuming inventory.
    @Column(nullable = false)
    private Integer totalInventory;

    // Room-level facilities (e.g. "WiFi", "TV", "Balcony") - distinct from
    // Hotel.amenities, which are hotel-wide (e.g. "pool", "gym"). Same
    // separate-table approach as amenities, for the same reason: individually
    // queryable without string-parsing a column.
    @ElementCollection
    @CollectionTable(name = "room_facility", joinColumns = @JoinColumn(name = "room_id"))
    @Column(name = "facility", nullable = false)
    @Builder.Default
    private List<String> facilities = new ArrayList<>();

    @Column(nullable = false)
    private boolean active;
}
