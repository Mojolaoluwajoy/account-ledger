package com.corebanking.savingsservice.dto;

import com.corebanking.savingsservice.enums.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SavingsTransactionResponse {

  private Long id;
  private Long accountId;
  private TransactionType transactionType;
  private BigDecimal amount;
  private BigDecimal balanceBefore;
  private BigDecimal balanceAfter;
  private String description;
  private LocalDateTime transactionDate;
}
