/*
  ForbiddenException.java
  Thrown by the service layer's per-resource ownership checks (JWT sub vs.
  Flight.ownerId) - distinct from a role-only Spring Security rejection,
  since SecurityConfig can only confirm "this caller is A partner", not "this
  caller owns THIS flight". GlobalExceptionHandler maps it to 403 Forbidden.
*/
package com.orbitra.flight_service.exception;

public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
