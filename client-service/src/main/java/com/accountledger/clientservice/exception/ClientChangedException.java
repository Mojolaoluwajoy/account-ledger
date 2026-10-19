package com.accountledger.clientservice.exception;

public class ClientChangedException extends RuntimeException {
  public ClientChangedException(String message) {
    super(message);
  }
}
