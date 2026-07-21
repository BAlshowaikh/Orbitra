/*
  GlobalExceptionHandler.java
  Catches exceptions thrown anywhere in the web layer and turns them into a
  consistent JSON ErrorResponse instead of a raw stack trace / default 500.
*/
package com.orbitra.user_service.exception;

import com.orbitra.user_service.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // No profile exists yet for this caller (hasn't PUT one).
    @ExceptionHandler(ProfileNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleProfileNotFound(ProfileNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    // Manual validation failure on the raw request body (firstName/lastName
    // missing or blank, dateOfBirth unparsable) - see UserProfileService,
    // which reads the body as a JsonNode instead of a validated DTO.
    @ExceptionHandler(InvalidProfileRequestException.class)
    public ResponseEntity<ErrorResponse> handleInvalidProfileRequest(InvalidProfileRequestException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // Thrown when the request body isn't valid JSON at all (truncated body,
    // wrong Content-Type, etc). Without this, Spring would let it fall into
    // the generic 500 handler below, which wrongly implies a server bug
    // instead of a malformed request.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMalformedBody(HttpMessageNotReadableException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, "Malformed request body");
    }

    // Thrown when the URL exists but was called with the wrong HTTP method
    // (e.g. POST /users/profile instead of PUT). Without this, Spring would
    // let it fall into the generic 500 handler below, which wrongly implies
    // a server bug instead of a client mistake.
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return buildResponse(HttpStatus.METHOD_NOT_ALLOWED, ex.getMessage());
    }

    // Fallback for anything not already handled above - a real bug, a DB
    // outage, a null pointer, etc. Full exception (with stack trace) is
    // logged server-side, but the client only ever sees a generic message.
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
