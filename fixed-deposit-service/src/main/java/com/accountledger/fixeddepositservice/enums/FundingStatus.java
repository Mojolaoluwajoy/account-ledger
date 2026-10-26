package com.accountledger.fixeddepositservice.enums;

/* The deposit statuses the funding admin endpoints list. */
public enum FundingStatus {
  /* Still waiting for its money; retried in the background. */
  PENDING_FUNDING,
  /* Refused by the funding account; can be retried by hand. */
  FUNDING_FAILED;

  public FixedDepositStatus toDepositStatus() {
    return FixedDepositStatus.valueOf(name());
  }
}
