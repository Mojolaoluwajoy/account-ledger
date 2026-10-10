package com.corebanking.clientservice.exception;

public class DuplicatePhoneNumberException extends RuntimeException {
  public DuplicatePhoneNumberException(String message) {
    super(message);
  }
}
