/*
  GlobalExceptionHandler.java
  Catches exceptions thrown anywhere in the web layer and turns them into a
  consistent JSON ErrorResponse instead of a raw stack trace / default 500.
*/
package com.orbitra.hotel_service.exception;

// ----------- IMPORTS -----------
import com.orbitra.hotel_service.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // --- Not found (404) ---
    @ExceptionHandler(HotelNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleHotelNotFound(HotelNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(RoomNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleRoomNotFound(RoomNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(RoomTypeNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleRoomTypeNotFound(RoomTypeNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    // --- Ownership rejection (403) ---
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenException ex) {
        return buildResponse(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    // --- Conflict (409) ---
    @ExceptionHandler(DuplicateRoomTypeNameException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateRoomTypeName(DuplicateRoomTypeNameException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(DuplicateRoomException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateRoom(DuplicateRoomException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    // --- Bad input (400) ---
    // Manual validation on JsonNode-bound merge-patch bodies (see each service's update()).
    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRequest(InvalidRequestException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // @Valid failures on HotelRequest/RoomRequest/RoomTypeRequest/UpdateActiveRequest/AvailabilityRangeRequest.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return buildResponse(HttpStatus.BAD_REQUEST, message);
    }

    // A @PathVariable/@RequestParam couldn't be converted (e.g. non-numeric id, unparsable date).
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = "Invalid value for '" + ex.getName() + "': " + ex.getValue();
        return buildResponse(HttpStatus.BAD_REQUEST, message);
    }

    // A required @RequestParam (e.g. RoomController's startDate/endDate) was omitted.
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // Request body isn't valid JSON at all.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMalformedBody(HttpMessageNotReadableException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, "Malformed request body");
    }

    // --- Method not allowed (405) ---
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return buildResponse(HttpStatus.METHOD_NOT_ALLOWED, ex.getMessage());
    }

    // --- Fallback (500) ---
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong");
    }

    private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, String message) {
        ErrorResponse body = new ErrorResponse(Instant.now(), status.value(), message);
        return ResponseEntity.status(status).body(body);
    }
}
