package com.accountledger.fixeddepositservice.exception;

public class LedgerPostingNotFoundException extends RuntimeException {
  public LedgerPostingNotFoundException(String message) {
    super(message);
  }
}
