/*
  GlobalExceptionHandler.java
  Catches exceptions thrown anywhere in the web layer and turns them into a
  consistent JSON ErrorResponse instead of a raw stack trace / default 500.
*/
package com.orbitra.auth_service.exception;

import com.orbitra.auth_service.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.stream.Collectors;

// @RestControllerAdvice = @ControllerAdvice + @ResponseBody: applies to every
// @RestController in the app, and whatever these methods return is serialized
// straight to JSON, same as a normal controller method.
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // Catches every remaining IllegalArgumentException AuthService throws
    // (self-registering as ADMIN, bad login credentials, disabled account,
    // missing partnerType) and reports it as 400 Bad Request. Duplicate email
    // is handled separately below, since that's a conflict, not bad input.
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // The request itself was valid (a well-formed email) - it only fails
    // because that email already belongs to another account, i.e. a conflict
    // with existing server state, not malformed input. That distinction is
    // what earns this its own 409 rather than folding into 400 above.
    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateEmail(DuplicateEmailException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    // Thrown by @Valid when a RegisterRequest/LoginRequest field fails its Bean
    // Validation annotation (@Email, @NotBlank, @Size) - collects every failed
    // field's message into one string rather than reporting only the first.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));

        return buildResponse(HttpStatus.BAD_REQUEST, message);
    }

    // An admin operation targeted an account id that doesn't exist.
    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAccountNotFound(AccountNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    // Thrown when a @PathVariable can't be converted to its target type, e.g.
    // PATCH /auth/admin/accounts/abc/status (non-numeric id). Without this,
    // Spring would let it fall into the generic 500 handler below, which is
    // misleading for what's actually a client input error.
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = "Invalid value for '" + ex.getName() + "': " + ex.getValue();
        return buildResponse(HttpStatus.BAD_REQUEST, message);
    }

    // Fallback for anything not already handled above - a real bug, a DB
    // outage, a null pointer, etc. Spring picks the most specific matching
    // handler for a given exception, so IllegalArgumentException/
    // MethodArgumentNotValidException are still preferred over this one; this
    // only runs when nothing more specific matched.
    //
    // Full exception (with stack trace) is logged server-side for debugging,
    // but the client only ever sees a generic message - the real exception
    // message could leak internal details (e.g. table/column names in a SQL
    // error), which is a security risk to expose to callers.
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
