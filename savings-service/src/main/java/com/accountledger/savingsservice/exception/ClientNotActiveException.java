package com.accountledger.savingsservice.exception;

public class ClientNotActiveException extends RuntimeException {
  public ClientNotActiveException(String message) {
    super(message);
  }
}
