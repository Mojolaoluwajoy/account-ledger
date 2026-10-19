package com.accountledger.fixeddepositservice.exception;

public class FixedDepositNotActiveException extends RuntimeException {
  public FixedDepositNotActiveException(String message) {
    super(message);
  }
}
