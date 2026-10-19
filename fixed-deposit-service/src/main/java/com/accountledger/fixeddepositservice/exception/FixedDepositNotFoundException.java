package com.accountledger.fixeddepositservice.exception;

public class FixedDepositNotFoundException extends RuntimeException {
  public FixedDepositNotFoundException(String message) {
    super(message);
  }
}
