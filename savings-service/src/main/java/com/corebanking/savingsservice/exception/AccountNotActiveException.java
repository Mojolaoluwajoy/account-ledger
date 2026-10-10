package com.corebanking.savingsservice.exception;

public class AccountNotActiveException extends RuntimeException {
  public AccountNotActiveException(String message) {
    super(message);
  }
}
