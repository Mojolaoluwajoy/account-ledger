package com.accountledger.clientservice.exception;

public class DuplicatePhoneNumberException extends RuntimeException {
  public DuplicatePhoneNumberException(String message) {
    super(message);
  }
}
