/*
  SeatClassService.java
  Business logic for the admin-managed SeatClass catalog: create/update/status
  by ADMIN, plus the public active-entries list partners pick from.
*/
package com.orbitra.flight_service.service;

// --------------- IMPORTS ---------------
import com.orbitra.flight_service.dto.SeatClassRequest;
import com.orbitra.flight_service.dto.SeatClassResponse;
import com.orbitra.flight_service.exception.DuplicateSeatClassNameException;
import com.orbitra.flight_service.exception.InvalidRequestException;
import com.orbitra.flight_service.exception.SeatClassNotFoundException;
import com.orbitra.flight_service.model.SeatClass;
import com.orbitra.flight_service.repository.SeatClassRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.util.List;

@Service
public class SeatClassService {

    private final SeatClassRepository seatClassRepository;

    public SeatClassService(SeatClassRepository seatClassRepository) {
        this.seatClassRepository = seatClassRepository;
    }

    // ---------------- METHOD 1: Create a catalog entry (ADMIN) ----------------
    @Transactional
    public SeatClassResponse create(SeatClassRequest request) {
        if (seatClassRepository.existsByName(request.name())) {
            throw new DuplicateSeatClassNameException("Seat class name already exists: " + request.name());
        }

        SeatClass seatClass = SeatClass.builder()
                .name(request.name())
                .description(request.description())
                .active(true)
                .build();

        seatClassRepository.save(seatClass);
        return toResponse(seatClass);
    }

    // ---------------- METHOD 2: Update a catalog entry (ADMIN, partial update) ----------------
    @Transactional
    public SeatClassResponse update(Long id, JsonNode body) {
        SeatClass seatClass = seatClassRepository.findById(id)
                .orElseThrow(() -> new SeatClassNotFoundException("Seat class not found: " + id));

        if (body.has("name")) {
            String name = requireNonBlank(body, "name");
            if (!name.equals(seatClass.getName()) && seatClassRepository.existsByName(name)) {
                throw new DuplicateSeatClassNameException("Seat class name already exists: " + name);
            }
            seatClass.setName(name);
        }

        if (body.has("description")) {
            JsonNode node = body.get("description");
            seatClass.setDescription(node.isNull() ? null : node.asString());
        }

        seatClassRepository.save(seatClass);
        return toResponse(seatClass);
    }

    // ---------------- METHOD 3: Activate/deactivate (ADMIN) ----------------
    @Transactional
    public SeatClassResponse updateStatus(Long id, boolean active) {
        SeatClass seatClass = seatClassRepository.findById(id)
                .orElseThrow(() -> new SeatClassNotFoundException("Seat class not found: " + id));

        seatClass.setActive(active);
        seatClassRepository.save(seatClass);
        return toResponse(seatClass);
    }

    // ---------------- METHOD 4: List entries (GET /seat-classes) ----------------
    // includeInactive is true only for ADMIN callers - everyone else only sees active entries.
    @Transactional(readOnly = true)
    public List<SeatClassResponse> list(boolean includeInactive) {
        List<SeatClass> seatClasses = includeInactive ? seatClassRepository.findAll() : seatClassRepository.findByActiveTrue();
        return seatClasses.stream().map(this::toResponse).toList();
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

    // ------------- HELPER 2: Convert SeatClass to SeatClassResponse -------------
    private SeatClassResponse toResponse(SeatClass seatClass) {
        return new SeatClassResponse(seatClass.getId(), seatClass.getName(), seatClass.getDescription(), seatClass.isActive());
    }
}
