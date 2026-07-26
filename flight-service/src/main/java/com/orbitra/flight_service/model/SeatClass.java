/*
  SeatClass.java
  JPA entity for the admin-managed, global seat class vocabulary (e.g.
  "Economy", "Business", "First") that flight partners pick from when adding a
  seat class to their own flight - see FlightSeat, which holds the
  flight-specific instance (price/inventory) of a seat class.
*/
package com.orbitra.flight_service.model;

// ------------------- IMPORTS -------------------
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "seat_class")
@Getter
@Setter
@NoArgsConstructor // required by JPA to instantiate entities via reflection
@AllArgsConstructor
@Builder
public class SeatClass {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Unique at the DB level - one canonical entry per seat class name, not
    // per-flight duplicates of the same name.
    @Column(nullable = false, unique = true)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    // Deactivating an entry (admin-only) hides it from new selection without
    // breaking any FlightSeat rows that already reference it.
    @Column(nullable = false)
    private boolean active;
}
