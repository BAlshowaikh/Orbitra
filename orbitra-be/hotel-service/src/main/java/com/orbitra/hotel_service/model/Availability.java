/*
  Availability.java
  JPA entity for a Room's remaining inventory count on a specific date - this
  is what search checks against for date-scoped queries, and what the future
  Booking Service will read/decrement.
*/
package com.orbitra.hotel_service.model;

// ------------------- IMPORTS -------------------
import jakarta.persistence.Column;
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

import java.time.LocalDate;

@Entity
@Table(
        name = "availability",
        // One row per room per date - this is what makes an upsert-by-range
        // well-defined (each date in a partner's requested range maps to
        // exactly one row to insert or update).
        uniqueConstraints = @UniqueConstraint(columnNames = {"room_id", "date"})
)
@Getter
@Setter
@NoArgsConstructor // required by JPA to instantiate entities via reflection
@AllArgsConstructor
@Builder
public class Availability {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private Integer availableCount;
}
