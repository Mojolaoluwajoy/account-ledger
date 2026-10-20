package com.accountledger.savingsservice.exception;

public class InterestRunInProgressException extends RuntimeException {
  public InterestRunInProgressException(String message) {
    super(message);
  }
}
