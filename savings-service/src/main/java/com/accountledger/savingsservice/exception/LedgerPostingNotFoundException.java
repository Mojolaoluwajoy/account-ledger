package com.accountledger.savingsservice.exception;

public class LedgerPostingNotFoundException extends RuntimeException {
  public LedgerPostingNotFoundException(String message) {
    super(message);
  }
}
