package com.accountledger.savingsservice.dto;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CloseAccountResponse {

  private SavingsAccountResponse account;

  /* Interest earned up to the day before closing, paid in before closing. */
  private BigDecimal interestPaid;

  private BigDecimal balanceTransferred;
  private Long transferredToAccountId;
}
