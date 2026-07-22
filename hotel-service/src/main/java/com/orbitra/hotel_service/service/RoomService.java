/*
  RoomService.java
  Business logic for a hotel's rooms and their date-level availability -
  owner-only CRUD plus the shared effective-availability lookup.
*/
package com.orbitra.hotel_service.service;

// --------------- IMPORTS ---------------
import com.orbitra.hotel_service.dto.AvailabilityRangeRequest;
import com.orbitra.hotel_service.dto.AvailabilityResponse;
import com.orbitra.hotel_service.dto.RoomRequest;
import com.orbitra.hotel_service.dto.RoomResponse;
import com.orbitra.hotel_service.exception.DuplicateRoomException;
import com.orbitra.hotel_service.exception.ForbiddenException;
import com.orbitra.hotel_service.exception.HotelNotFoundException;
import com.orbitra.hotel_service.exception.InvalidRequestException;
import com.orbitra.hotel_service.exception.RoomNotFoundException;
import com.orbitra.hotel_service.exception.RoomTypeNotFoundException;
import com.orbitra.hotel_service.model.Availability;
import com.orbitra.hotel_service.model.Hotel;
import com.orbitra.hotel_service.model.Room;
import com.orbitra.hotel_service.model.RoomType;
import com.orbitra.hotel_service.repository.AvailabilityRepository;
import com.orbitra.hotel_service.repository.HotelRepository;
import com.orbitra.hotel_service.repository.RoomRepository;
import com.orbitra.hotel_service.repository.RoomTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
public class RoomService {

    // Caps how many nights a single availability read/write can span, so a
    // partner can't trigger a huge per-date loop in one request.
    private static final int MAX_RANGE_DAYS = 366;

    private final RoomRepository roomRepository;
    private final RoomTypeRepository roomTypeRepository;
    private final HotelRepository hotelRepository;
    private final AvailabilityRepository availabilityRepository;

    public RoomService(RoomRepository roomRepository, RoomTypeRepository roomTypeRepository,
                        HotelRepository hotelRepository, AvailabilityRepository availabilityRepository) {
        this.roomRepository = roomRepository;
        this.roomTypeRepository = roomTypeRepository;
        this.hotelRepository = hotelRepository;
        this.availabilityRepository = availabilityRepository;
    }

    // ---------------- METHOD 1: Add a room to a hotel (owner only) ----------------
    @Transactional
    public RoomResponse create(Long callerId, Long hotelId, RoomRequest request) {
        Hotel hotel = getOwnedHotel(hotelId, callerId);

        RoomType roomType = roomTypeRepository.findById(request.roomTypeId())
                .filter(RoomType::isActive)
                .orElseThrow(() -> new RoomTypeNotFoundException("Room type not found: " + request.roomTypeId()));

        if (roomRepository.existsByHotelIdAndRoomTypeId(hotelId, request.roomTypeId())) {
            throw new DuplicateRoomException("This hotel already has a room for that room type");
        }

        Room room = Room.builder()
                .hotel(hotel)
                .roomType(roomType)
                .capacity(request.capacity())
                .basePricePerNight(request.basePricePerNight())
                .totalInventory(request.totalInventory())
                .facilities(request.facilities() != null ? request.facilities() : List.of())
                .active(true)
                .build();

        roomRepository.save(room);
        return toResponse(room);
    }

    // ---------------- METHOD 2: Update a room (owner only, partial update) ----------------
    @Transactional
    public RoomResponse update(Long callerId, Long hotelId, Long roomId, JsonNode body) {
        Room room = getOwnedRoom(hotelId, roomId, callerId);

        if (body.has("capacity")) {
            room.setCapacity(requirePositiveInt(body, "capacity"));
        }
        if (body.has("basePricePerNight")) {
            room.setBasePricePerNight(requirePositiveDecimal(body, "basePricePerNight"));
        }
        if (body.has("totalInventory")) {
            room.setTotalInventory(requireNonNegativeInt(body, "totalInventory"));
        }
        if (body.has("facilities")) {
            room.setFacilities(toStringList(body.get("facilities")));
        }

        roomRepository.save(room);
        return toResponse(room);
    }

    // ---------------- METHOD 3: Activate/deactivate a room (owner only) ----------------
    @Transactional
    public RoomResponse updateStatus(Long callerId, Long hotelId, Long roomId, boolean active) {
        Room room = getOwnedRoom(hotelId, roomId, callerId);
        room.setActive(active);
        roomRepository.save(room);
        return toResponse(room);
    }

    // ---------------- METHOD 4: Get a room's availability calendar (owner only) ----------------
    @Transactional(readOnly = true)
    public List<AvailabilityResponse> getAvailability(Long callerId, Long hotelId, Long roomId, LocalDate startDate, LocalDate endDate) {
        Room room = getOwnedRoom(hotelId, roomId, callerId);
        validateRange(startDate, endDate);

        List<AvailabilityResponse> result = new ArrayList<>();
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            result.add(new AvailabilityResponse(date, getEffectiveAvailability(room, date)));
        }
        return result;
    }

    // ---------------- METHOD 5: Set a room's availability for a date range (owner only) ----------------
    @Transactional
    public List<AvailabilityResponse> setAvailability(Long callerId, Long hotelId, Long roomId, AvailabilityRangeRequest request) {
        Room room = getOwnedRoom(hotelId, roomId, callerId);
        validateRange(request.startDate(), request.endDate());

        List<AvailabilityResponse> result = new ArrayList<>();
        for (LocalDate date = request.startDate(); !date.isAfter(request.endDate()); date = date.plusDays(1)) {
            final LocalDate current = date;
            Availability row = availabilityRepository.findByRoomIdAndDate(room.getId(), current)
                    .orElseGet(() -> Availability.builder().room(room).date(current).build());
            row.setAvailableCount(request.availableCount());
            availabilityRepository.save(row);
            result.add(new AvailabilityResponse(current, request.availableCount()));
        }
        return result;
    }

    // Effective count for one room/date: explicit row if one exists, else totalInventory.
    public int getEffectiveAvailability(Room room, LocalDate date) {
        return availabilityRepository.findByRoomIdAndDate(room.getId(), date)
                .map(Availability::getAvailableCount)
                .orElse(room.getTotalInventory());
    }

    // ---------------- Helpers ----------------

    // ------------- HELPER 1: Get a hotel and ensure the caller owns it -------------
    private Hotel getOwnedHotel(Long hotelId, Long callerId) {
        Hotel hotel = hotelRepository.findById(hotelId)
                .orElseThrow(() -> new HotelNotFoundException("Hotel not found: " + hotelId));
        if (!hotel.getOwnerId().equals(callerId)) {
            throw new ForbiddenException("You do not own this hotel");
        }
        return hotel;
    }

    // ------------- HELPER 2: Get a room under the caller's own hotel -------------
    private Room getOwnedRoom(Long hotelId, Long roomId, Long callerId) {
        getOwnedHotel(hotelId, callerId);
        return roomRepository.findById(roomId)
                .filter(r -> r.getHotel().getId().equals(hotelId))
                .orElseThrow(() -> new RoomNotFoundException("Room not found: " + roomId));
    }

    // ------------- HELPER 3: Validate a date range's order and length -------------
    private void validateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate.isAfter(endDate)) {
            throw new InvalidRequestException("startDate must not be after endDate");
        }
        if (ChronoUnit.DAYS.between(startDate, endDate) > MAX_RANGE_DAYS) {
            throw new InvalidRequestException("Date range cannot exceed " + MAX_RANGE_DAYS + " days");
        }
    }

    // ------------- HELPER 4: Numeric field checks for update()'s body -------------
    private Integer requirePositiveInt(JsonNode body, String field) {
        JsonNode node = body.get(field);
        if (node == null || node.isNull() || !node.isNumber() || node.asInt() <= 0) {
            throw new InvalidRequestException(field + " must be a positive integer");
        }
        return node.asInt();
    }

    private Integer requireNonNegativeInt(JsonNode body, String field) {
        JsonNode node = body.get(field);
        if (node == null || node.isNull() || !node.isNumber() || node.asInt() < 0) {
            throw new InvalidRequestException(field + " must be zero or a positive integer");
        }
        return node.asInt();
    }

    private BigDecimal requirePositiveDecimal(JsonNode body, String field) {
        JsonNode node = body.get(field);
        if (node == null || node.isNull() || !node.isNumber() || node.decimalValue().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidRequestException(field + " must be a positive number");
        }
        return node.decimalValue();
    }

    // ------------- HELPER 5: Convert a JSON array node to a List<String> -------------
    private List<String> toStringList(JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        node.forEach(n -> values.add(n.asString()));
        return values;
    }

    // ------------- HELPER 6: Convert Room to RoomResponse -------------
    private RoomResponse toResponse(Room room) {
        return new RoomResponse(
                room.getId(), room.getHotel().getId(), room.getRoomType().getId(), room.getRoomType().getName(),
                room.getCapacity(), room.getBasePricePerNight(), room.getTotalInventory(),
                room.getFacilities(), room.isActive()
        );
    }
}
