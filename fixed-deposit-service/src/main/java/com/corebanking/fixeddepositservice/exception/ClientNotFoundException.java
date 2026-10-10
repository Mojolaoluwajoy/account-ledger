package com.corebanking.fixeddepositservice.exception;

public class ClientNotFoundException extends RuntimeException {
  public ClientNotFoundException(String message) {
    super(message);
  }
}
