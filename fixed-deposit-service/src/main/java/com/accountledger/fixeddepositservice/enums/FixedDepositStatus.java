package com.accountledger.fixeddepositservice.enums;

public enum FixedDepositStatus {
  ACTIVE,
  MATURED,
  CLOSED,
  /* Withdrawn before the maturity date, with reduced interest. */
  CLOSED_EARLY,
  /* Waiting for the money to be taken from its funding account. */
  PENDING_FUNDING,
  /* The funding account refused to pay for it; nothing was taken. */
  FUNDING_FAILED
}
