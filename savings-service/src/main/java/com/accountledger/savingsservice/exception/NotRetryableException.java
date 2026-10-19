package com.accountledger.savingsservice.exception;

public class NotRetryableException extends RuntimeException {
  public NotRetryableException(String message) {
    super(message);
  }
}
