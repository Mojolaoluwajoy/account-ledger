package com.corebanking.savingsservice.exception;

public class NotRetryableException extends RuntimeException {
  public NotRetryableException(String message) {
    super(message);
  }
}
