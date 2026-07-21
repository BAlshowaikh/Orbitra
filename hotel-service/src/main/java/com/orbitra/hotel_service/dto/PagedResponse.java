/*
  PagedResponse.java
  Generic paginated response wrapper for list endpoints. Wraps Spring Data's
  Page<T> into our own record rather than returning Page directly, so list
  endpoints stay consistent with every other response on the API (a DTO we
  own, not a framework type on the wire).
*/
package com.orbitra.hotel_service.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record PagedResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static <T> PagedResponse<T> from(Page<T> page) {
        return new PagedResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
