/*
  UpdateActiveRequest.java
  Request body shared by every PATCH .../status endpoint in this service
  (Hotel, Room, RoomType) - activates or deactivates the target without
  deleting it.
*/
package com.orbitra.hotel_service.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateActiveRequest(
        // Boxed Boolean, not primitive boolean - @NotNull can only catch a
        // missing/null field this way; a primitive would silently default to false.
        @NotNull Boolean active
) {
}
