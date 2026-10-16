package com.corebanking.fixeddepositservice.exception;

public class LedgerPostingNotFoundException extends RuntimeException {
  public LedgerPostingNotFoundException(String message) {
    super(message);
  }
}
