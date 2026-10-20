package com.accountledger.savingsservice.exception;

public class AccountHasBalanceException extends RuntimeException {
  public AccountHasBalanceException(String message) {
    super(message);
  }
}
