/*
  HotelService.java
  Business logic for hotel listings: create/update/status by the owning
  partner (or admin for status), the partner's own listing view, public
  search, and the public detail view.
*/
package com.orbitra.hotel_service.service;

// --------------- IMPORTS ---------------
import com.orbitra.hotel_service.dto.HotelDetailResponse;
import com.orbitra.hotel_service.dto.HotelRequest;
import com.orbitra.hotel_service.dto.HotelResponse;
import com.orbitra.hotel_service.dto.HotelSearchResultResponse;
import com.orbitra.hotel_service.dto.PagedResponse;
import com.orbitra.hotel_service.dto.RoomResponse;
import com.orbitra.hotel_service.exception.ForbiddenException;
import com.orbitra.hotel_service.exception.HotelNotFoundException;
import com.orbitra.hotel_service.exception.InvalidRequestException;
import com.orbitra.hotel_service.model.Hotel;
import com.orbitra.hotel_service.model.Room;
import com.orbitra.hotel_service.repository.HotelRepository;
import com.orbitra.hotel_service.repository.RoomRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class HotelService {

    private final HotelRepository hotelRepository;
    private final RoomRepository roomRepository;

    public HotelService(HotelRepository hotelRepository, RoomRepository roomRepository) {
        this.hotelRepository = hotelRepository;
        this.roomRepository = roomRepository;
    }

    // ---------------- METHOD 1: Create a hotel (PARTNER_HOTEL) ----------------
    @Transactional
    public HotelResponse create(Long ownerId, HotelRequest request) {
        Hotel hotel = Hotel.builder()
                .ownerId(ownerId)
                .name(request.name())
                .description(request.description())
                .address(request.address())
                .city(request.city())
                .country(request.country())
                .amenities(request.amenities() != null ? request.amenities() : List.of())
                .active(true)
                .build();

        hotelRepository.save(hotel);
        return toResponse(hotel);
    }

    // ---------------- METHOD 2: Update a hotel (owner only, partial update) ----------------
    @Transactional
    public HotelResponse update(Long callerId, Long hotelId, JsonNode body) {
        Hotel hotel = getOwnedHotel(hotelId, callerId);

        if (body.has("name")) {
            hotel.setName(requireNonBlank(body, "name"));
        }
        if (body.has("address")) {
            hotel.setAddress(requireNonBlank(body, "address"));
        }
        if (body.has("city")) {
            hotel.setCity(requireNonBlank(body, "city"));
        }
        if (body.has("country")) {
            hotel.setCountry(requireNonBlank(body, "country"));
        }
        if (body.has("description")) {
            JsonNode node = body.get("description");
            hotel.setDescription(node.isNull() ? null : node.asString());
        }
        if (body.has("amenities")) {
            hotel.setAmenities(toStringList(body.get("amenities")));
        }

        hotelRepository.save(hotel);
        return toResponse(hotel);
    }

    // ---------------- METHOD 3: Activate/deactivate (owner or admin) ----------------
    @Transactional
    public HotelResponse updateStatus(Long callerId, boolean isAdmin, Long hotelId, boolean active) {
        Hotel hotel = hotelRepository.findById(hotelId)
                .orElseThrow(() -> new HotelNotFoundException("Hotel not found: " + hotelId));

        if (!isAdmin && !hotel.getOwnerId().equals(callerId)) {
            throw new ForbiddenException("You do not own this hotel");
        }

        hotel.setActive(active);
        hotelRepository.save(hotel);
        return toResponse(hotel);
    }

    // ---------------- METHOD 4: Partner's own listings (GET /hotels/mine) ----------------
    @Transactional(readOnly = true)
    public PagedResponse<HotelResponse> getMyHotels(Long ownerId, Pageable pageable) {
        Page<Hotel> hotels = hotelRepository.findByOwnerId(ownerId, pageable);
        return PagedResponse.from(hotels.map(this::toResponse));
    }

    // ---------------- METHOD 5: Public search (GET /hotels) ----------------
    @Transactional(readOnly = true)
    public PagedResponse<HotelSearchResultResponse> search(
            String city, LocalDate checkIn, LocalDate checkOut,
            Integer guests, BigDecimal minPrice, BigDecimal maxPrice,
            Pageable pageable
    ) {
        validateDateRange(checkIn, checkOut);

        Page<Hotel> hotels = hotelRepository.search(city, guests, minPrice, maxPrice, checkIn, checkOut, pageable);
        return PagedResponse.from(hotels.map(this::toSearchResult));
    }

    // ---------------- METHOD 6: Public detail view (GET /hotels/{id}) ----------------
    @Transactional(readOnly = true)
    public HotelDetailResponse getDetail(Long hotelId) {
        Hotel hotel = hotelRepository.findById(hotelId)
                .filter(Hotel::isActive)
                .orElseThrow(() -> new HotelNotFoundException("Hotel not found: " + hotelId));

        List<RoomResponse> rooms = roomRepository.findByHotelIdAndActiveTrue(hotelId).stream()
                .map(this::toRoomResponse)
                .toList();

        return new HotelDetailResponse(
                hotel.getId(), hotel.getName(), hotel.getDescription(), hotel.getAddress(),
                hotel.getCity(), hotel.getCountry(), hotel.getAmenities(), rooms
        );
    }

    // ---------------- Helpers ----------------

    // ------------- HELPER 1: Get a hotel and ensure the caller owns it (or throw ForbiddenException) -------------
    private Hotel getOwnedHotel(Long hotelId, Long callerId) {
        Hotel hotel = hotelRepository.findById(hotelId)
                .orElseThrow(() -> new HotelNotFoundException("Hotel not found: " + hotelId));

        if (!hotel.getOwnerId().equals(callerId)) {
            throw new ForbiddenException("You do not own this hotel");
        }
        return hotel;
    }

    // ------------- HELPER 2: Require a non-blank field in update()'s body -------------
    private String requireNonBlank(JsonNode body, String field) {
        JsonNode node = body.get(field);
        if (node == null || node.isNull() || node.asString().isBlank()) {
            throw new InvalidRequestException(field + " must not be blank");
        }
        return node.asString();
    }

    // ------------- HELPER 3: Convert a JSON array node to a List<String> -------------
    private List<String> toStringList(JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        node.forEach(n -> values.add(n.asString()));
        return values;
    }

    // ------------- HELPER 4: Validate checkIn/checkOut date range -------------
    private void validateDateRange(LocalDate checkIn, LocalDate checkOut) {
        if ((checkIn == null) != (checkOut == null)) {
            throw new InvalidRequestException("checkIn and checkOut must be provided together");
        }
        if (checkIn != null && !checkIn.isBefore(checkOut)) {
            throw new InvalidRequestException("checkOut must be after checkIn");
        }
    }

    // ------------- HELPER 5: Convert Hotel to HotelResponse -------------
    private HotelResponse toResponse(Hotel hotel) {
        return new HotelResponse(
                hotel.getId(), hotel.getOwnerId(), hotel.getName(), hotel.getDescription(),
                hotel.getAddress(), hotel.getCity(), hotel.getCountry(), hotel.getAmenities(),
                hotel.isActive(), hotel.getCreatedAt()
        );
    }

    // ------------- HELPER 6: Convert Hotel to HotelSearchResultResponse -------------
    private HotelSearchResultResponse toSearchResult(Hotel hotel) {
        BigDecimal minPrice = roomRepository.findMinActivePriceByHotelId(hotel.getId()).orElse(null);
        return new HotelSearchResultResponse(hotel.getId(), hotel.getName(), hotel.getCity(), hotel.getCountry(), minPrice);
    }

    // ------------- HELPER 7: Convert Room to RoomResponse -------------
    private RoomResponse toRoomResponse(Room room) {
        return new RoomResponse(
                room.getId(), room.getHotel().getId(), room.getRoomType().getId(), room.getRoomType().getName(),
                room.getCapacity(), room.getBasePricePerNight(), room.getTotalInventory(),
                room.getFacilities(), room.isActive()
        );
    }
}
