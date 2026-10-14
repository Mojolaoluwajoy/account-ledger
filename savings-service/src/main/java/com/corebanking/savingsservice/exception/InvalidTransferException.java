package com.corebanking.savingsservice.exception;

public class InvalidTransferException extends RuntimeException {
  public InvalidTransferException(String message) {
    super(message);
  }
}
