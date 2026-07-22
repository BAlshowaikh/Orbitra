/*
  RoomTypeService.java
  Business logic for the admin-managed RoomType catalog: create/update/status
  by ADMIN, plus the public active-entries list partners pick from.
*/
package com.orbitra.hotel_service.service;

// --------------- IMPORTS ---------------
import com.orbitra.hotel_service.dto.RoomTypeRequest;
import com.orbitra.hotel_service.dto.RoomTypeResponse;
import com.orbitra.hotel_service.exception.DuplicateRoomTypeNameException;
import com.orbitra.hotel_service.exception.InvalidRequestException;
import com.orbitra.hotel_service.exception.RoomTypeNotFoundException;
import com.orbitra.hotel_service.model.RoomType;
import com.orbitra.hotel_service.repository.RoomTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.util.List;

@Service
public class RoomTypeService {

    private final RoomTypeRepository roomTypeRepository;

    public RoomTypeService(RoomTypeRepository roomTypeRepository) {
        this.roomTypeRepository = roomTypeRepository;
    }

    // ---------------- METHOD 1: Create a catalog entry (ADMIN) ----------------
    @Transactional
    public RoomTypeResponse create(RoomTypeRequest request) {
        if (roomTypeRepository.existsByName(request.name())) {
            throw new DuplicateRoomTypeNameException("Room type name already exists: " + request.name());
        }

        RoomType roomType = RoomType.builder()
                .name(request.name())
                .description(request.description())
                .active(true)
                .build();

        roomTypeRepository.save(roomType);
        return toResponse(roomType);
    }

    // ---------------- METHOD 2: Update a catalog entry (ADMIN, partial update) ----------------
    @Transactional
    public RoomTypeResponse update(Long id, JsonNode body) {
        RoomType roomType = roomTypeRepository.findById(id)
                .orElseThrow(() -> new RoomTypeNotFoundException("Room type not found: " + id));

        if (body.has("name")) {
            String name = requireNonBlank(body, "name");
            if (!name.equals(roomType.getName()) && roomTypeRepository.existsByName(name)) {
                throw new DuplicateRoomTypeNameException("Room type name already exists: " + name);
            }
            roomType.setName(name);
        }

        if (body.has("description")) {
            JsonNode node = body.get("description");
            roomType.setDescription(node.isNull() ? null : node.asString());
        }

        roomTypeRepository.save(roomType);
        return toResponse(roomType);
    }

    // ---------------- METHOD 3: Activate/deactivate (ADMIN) ----------------
    @Transactional
    public RoomTypeResponse updateStatus(Long id, boolean active) {
        RoomType roomType = roomTypeRepository.findById(id)
                .orElseThrow(() -> new RoomTypeNotFoundException("Room type not found: " + id));

        roomType.setActive(active);
        roomTypeRepository.save(roomType);
        return toResponse(roomType);
    }

    // ---------------- METHOD 4: List entries (GET /room-types) ----------------
    // includeInactive is true only for ADMIN callers - everyone else only sees active entries.
    @Transactional(readOnly = true)
    public List<RoomTypeResponse> list(boolean includeInactive) {
        List<RoomType> roomTypes = includeInactive ? roomTypeRepository.findAll() : roomTypeRepository.findByActiveTrue();
        return roomTypes.stream().map(this::toResponse).toList();
    }

    // ---------------- Helpers ----------------

    // ------------- HELPER 1: Require a non-blank field in update()'s body -------------
    private String requireNonBlank(JsonNode body, String field) {
        JsonNode node = body.get(field);
        if (node == null || node.isNull() || node.asString().isBlank()) {
            throw new InvalidRequestException(field + " must not be blank");
        }
        return node.asString();
    }

    // ------------- HELPER 2: Convert RoomType to RoomTypeResponse -------------
    private RoomTypeResponse toResponse(RoomType roomType) {
        return new RoomTypeResponse(roomType.getId(), roomType.getName(), roomType.getDescription(), roomType.isActive());
    }
}
