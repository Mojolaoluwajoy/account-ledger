package com.accountledger.savingsservice.exception;

public class InvalidIdempotencyKeyException extends RuntimeException {
  public InvalidIdempotencyKeyException(String message) {
    super(message);
  }
}
