package com.accountledger.fixeddepositservice.enums;

public enum FixedDepositStatus {
  ACTIVE,
  MATURED,
  CLOSED,
  /* Withdrawn before the maturity date, with reduced interest. */
  CLOSED_EARLY
}
