package com.accountledger.fixeddepositservice.exception;

public class ClientNotActiveException extends RuntimeException {
  public ClientNotActiveException(String message) {
    super(message);
  }
}
