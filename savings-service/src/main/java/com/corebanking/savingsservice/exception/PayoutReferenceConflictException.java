package com.corebanking.savingsservice.exception;

public class PayoutReferenceConflictException extends RuntimeException {
  public PayoutReferenceConflictException(String message) {
    super(message);
  }
}
