package com.accountledger.fixeddepositservice.exception;

public class PrematureWithdrawalException extends RuntimeException {
  public PrematureWithdrawalException(String message) {
    super(message);
  }
}
