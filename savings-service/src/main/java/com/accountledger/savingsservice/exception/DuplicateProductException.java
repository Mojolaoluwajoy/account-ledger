package com.accountledger.savingsservice.exception;

public class DuplicateProductException extends RuntimeException {
  public DuplicateProductException(String message) {
    super(message);
  }
}
