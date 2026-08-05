/*
  ProfileNotFoundException.java
  Thrown when the caller has no profile yet (never PUT one) - distinct from
  a generic error so GlobalExceptionHandler can map it to 404 Not Found.
*/
package com.orbitra.user_service.exception;

public class ProfileNotFoundException extends RuntimeException {

    public ProfileNotFoundException(String message) {
        super(message);
    }
}
