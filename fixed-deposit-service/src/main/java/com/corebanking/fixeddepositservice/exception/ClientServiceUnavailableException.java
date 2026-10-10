package com.corebanking.fixeddepositservice.exception;

public class ClientServiceUnavailableException extends RuntimeException {
  public ClientServiceUnavailableException(String message) {
    super(message);
  }
}
