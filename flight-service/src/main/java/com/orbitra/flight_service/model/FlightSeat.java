/*
  FlightSeat.java
  JPA entity for a specific flight's instance of a SeatClass catalog entry -
  holds the flight-specific price, inventory, and facilities for that seat
  class, while the SeatClass entity itself only holds the shared
  name/description. No capacity field - unlike a hotel Room (which sleeps
  multiple people), one seat holds exactly one passenger.
*/
package com.orbitra.flight_service.model;

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
        name = "flight_seat",
        // A flight can add a given seat class at most once - matches a plain
        // dropdown-selection UX, no need to disambiguate two rows with the
        // same seat class on the same flight.
        uniqueConstraints = @UniqueConstraint(columnNames = {"flight_id", "seat_class_id"})
)
@Getter
@Setter
@NoArgsConstructor // required by JPA to instantiate entities via reflection
@AllArgsConstructor
@Builder
public class FlightSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // LAZY - loading a FlightSeat shouldn't force-load its parent Flight
    // unless something actually asks for it (e.g. ownership checks do,
    // explicitly).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flight_id", nullable = false)
    private Flight flight;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seat_class_id", nullable = false)
    private SeatClass seatClass;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal basePricePerSeat;

    // Total seats of this class on this flight, set by the partner at
    // creation - fixed capacity, distinct from availableCount below.
    @Column(nullable = false)
    private Integer totalInventory;

    // Seats currently left to book - starts equal to totalInventory, only
    // ever decremented by Booking Service (never partner-editable directly,
    // unlike totalInventory). No per-date dimension needed here since each
    // Flight row is already one specific dated departure.
    @Column(nullable = false)
    private Integer availableCount;

    // Seat-level facilities (e.g. "extra legroom", "meal included", "WiFi") -
    // this service has no flight-level amenities table (unlike
    // Hotel.amenities), since in-flight perks map more naturally to the seat
    // class than the route itself.
    @ElementCollection
    @CollectionTable(name = "flight_seat_facility", joinColumns = @JoinColumn(name = "flight_seat_id"))
    @Column(name = "facility", nullable = false)
    @Builder.Default
    private List<String> facilities = new ArrayList<>();

    @Column(nullable = false)
    private boolean active;
}
