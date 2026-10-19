package com.accountledger.savingsservice.exception;

public class PayoutReferenceConflictException extends RuntimeException {
  public PayoutReferenceConflictException(String message) {
    super(message);
  }
}
