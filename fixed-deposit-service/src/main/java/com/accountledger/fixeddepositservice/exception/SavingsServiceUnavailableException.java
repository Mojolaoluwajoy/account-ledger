package com.accountledger.fixeddepositservice.exception;

public class SavingsServiceUnavailableException extends RuntimeException {
  public SavingsServiceUnavailableException(String message) {
    super(message);
  }
}
