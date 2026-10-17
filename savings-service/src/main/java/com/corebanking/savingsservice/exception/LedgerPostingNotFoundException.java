package com.corebanking.savingsservice.exception;

public class LedgerPostingNotFoundException extends RuntimeException {
  public LedgerPostingNotFoundException(String message) {
    super(message);
  }
}
