package com.accountledger.fixeddepositservice.exception;

public class InvalidFundingAccountException extends RuntimeException {
  public InvalidFundingAccountException(String message) {
    super(message);
  }
}
