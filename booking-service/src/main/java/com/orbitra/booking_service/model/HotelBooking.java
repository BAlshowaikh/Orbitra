/*
  HotelBooking.java
  JPA entity for a hotel-stay reservation - extends Booking via JOINED
  inheritance, mapping to its own hotel_booking table. Its id is shared with
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

import java.time.LocalDate;

@Entity
@Table(name = "hotel_booking")
@DiscriminatorValue("HOTEL")
@Getter
@Setter
@NoArgsConstructor // required by JPA to instantiate entities via reflection
@SuperBuilder
public class HotelBooking extends Booking {

    @Column(nullable = false)
    private Long hotelId;

    @Column(nullable = false)
    private Long roomId;

    @Column(nullable = false)
    private LocalDate checkInDate;

    @Column(nullable = false)
    private LocalDate checkOutDate;
}
