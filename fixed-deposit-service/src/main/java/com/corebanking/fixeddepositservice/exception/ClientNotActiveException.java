package com.corebanking.fixeddepositservice.exception;

public class ClientNotActiveException extends RuntimeException {
  public ClientNotActiveException(String message) {
    super(message);
  }
}
