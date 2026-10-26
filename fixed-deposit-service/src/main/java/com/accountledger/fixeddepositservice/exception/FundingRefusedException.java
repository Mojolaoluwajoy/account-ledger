package com.accountledger.fixeddepositservice.exception;

public class FundingRefusedException extends RuntimeException {
  public FundingRefusedException(String message) {
    super(message);
  }
}
