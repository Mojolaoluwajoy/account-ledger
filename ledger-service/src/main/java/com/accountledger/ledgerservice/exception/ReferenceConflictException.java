package com.accountledger.ledgerservice.exception;

public class ReferenceConflictException extends RuntimeException {
  public ReferenceConflictException(String message) {
    super(message);
  }
}
