package com.accountledger.savingsservice.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

/*
 * What the sender sees after a transfer. The destination account's balance is
 * left out on purpose: it may belong to another client.
 */
@Data
@Builder
public class TransferResponse {

  private Long sourceAccountId;
  private Long destinationAccountId;
  private String destinationAccountNumber;
  private BigDecimal amount;
  private BigDecimal balanceBefore;
  private BigDecimal newBalance;
  private LocalDateTime transactionDate;
}
