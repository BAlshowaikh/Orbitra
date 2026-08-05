/*
  DuplicateEmailException.java
  Thrown when a registration is attempted with an email that's already taken.
  Distinct from IllegalArgumentException so GlobalExceptionHandler can map it
  to 409 Conflict specifically - the request itself is valid, it just conflicts
  with an account that already exists.
*/
package com.orbitra.auth_service.exception;

public class DuplicateEmailException extends RuntimeException {

    public DuplicateEmailException(String message) {
        super(message);
    }
}
