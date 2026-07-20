/*
  RoomType.java
  JPA entity for the admin-managed, global room type vocabulary (e.g.
  "Deluxe King", "Suite") that hotel partners pick from when adding a room to
  their own hotel - see Room, which holds the hotel-specific instance
  (capacity/price/inventory) of a room type.
*/
package com.orbitra.hotel_service.model;

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
@Table(name = "room_type")
@Getter
@Setter
@NoArgsConstructor // required by JPA to instantiate entities via reflection
@AllArgsConstructor
@Builder
public class RoomType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Unique at the DB level - the whole point of a catalog is one canonical
    // entry per room type name, not per-hotel duplicates of the same name.
    @Column(nullable = false, unique = true)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    // Deactivating an entry (admin-only) hides it from new selection without
    // breaking any Room rows that already reference it.
    @Column(nullable = false)
    private boolean active;
}
