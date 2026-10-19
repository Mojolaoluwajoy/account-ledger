package com.accountledger.fixeddepositservice.exception;

public class NotRetryableException extends RuntimeException {
  public NotRetryableException(String message) {
    super(message);
  }
}
