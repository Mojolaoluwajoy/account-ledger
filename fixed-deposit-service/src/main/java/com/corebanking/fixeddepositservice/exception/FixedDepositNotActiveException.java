package com.corebanking.fixeddepositservice.exception;

public class FixedDepositNotActiveException extends RuntimeException {
  public FixedDepositNotActiveException(String message) {
    super(message);
  }
}
