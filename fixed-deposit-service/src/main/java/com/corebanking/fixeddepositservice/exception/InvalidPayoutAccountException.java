package com.corebanking.fixeddepositservice.exception;

public class InvalidPayoutAccountException extends RuntimeException {
  public InvalidPayoutAccountException(String message) {
    super(message);
  }
}
