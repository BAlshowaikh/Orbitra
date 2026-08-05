/*
  ForbiddenException.java
  Thrown by the service layer's per-resource ownership checks (JWT sub vs.
  Hotel.ownerId) - distinct from a role-only Spring Security rejection, since
  SecurityConfig can only confirm "this caller is A partner", not "this
  caller owns THIS hotel". GlobalExceptionHandler maps it to 403 Forbidden.
*/
package com.orbitra.hotel_service.exception;

public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
