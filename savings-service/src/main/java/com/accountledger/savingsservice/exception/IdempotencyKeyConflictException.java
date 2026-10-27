package com.accountledger.savingsservice.exception;

public class IdempotencyKeyConflictException extends RuntimeException {
  public IdempotencyKeyConflictException(String message) {
    super(message);
  }
}
