package com.accountledger.savingsservice.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TransactionResponse {

  private Long accountId;
  private String transactionType;
  private BigDecimal amount;
  private BigDecimal balanceBefore;
  private BigDecimal newBalance;
  private LocalDateTime transactionDate;
}
