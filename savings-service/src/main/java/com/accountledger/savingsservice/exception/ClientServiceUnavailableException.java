package com.accountledger.savingsservice.exception;

public class ClientServiceUnavailableException extends RuntimeException {
  public ClientServiceUnavailableException(String message) {
    super(message);
  }
}
