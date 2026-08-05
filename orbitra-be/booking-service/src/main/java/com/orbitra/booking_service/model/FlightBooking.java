/*
  FlightBooking.java
  JPA entity for a flight-seat reservation - extends Booking via JOINED
  inheritance, mapping to its own flight_booking table. Its id is shared with
  (a foreign key to) the parent booking row, not its own sequence.
*/
package com.orbitra.booking_service.model;

// ------------------- IMPORTS -------------------
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "flight_booking")
@DiscriminatorValue("FLIGHT")
@Getter
@Setter
@NoArgsConstructor // required by JPA to instantiate entities via reflection
@SuperBuilder
public class FlightBooking extends Booking {

    @Column(nullable = false)
    private Long flightId;

    @Column(nullable = false)
    private Long flightSeatId;
}
