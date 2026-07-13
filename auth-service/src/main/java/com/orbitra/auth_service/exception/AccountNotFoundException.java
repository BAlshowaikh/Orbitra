/*
  AccountNotFoundException.java
  Thrown when an admin operation targets an account id that doesn't exist.
  Distinct from IllegalArgumentException so GlobalExceptionHandler can map it
  to 404 Not Found specifically.
*/
package com.orbitra.auth_service.exception;

public class AccountNotFoundException extends RuntimeException {

    public AccountNotFoundException(String message) {
        super(message);
    }
}
