package com.accountledger.ledgerservice.exception;

public class LedgerEntryNotFoundException extends RuntimeException {
  public LedgerEntryNotFoundException(String message) {
    super(message);
  }
}
