package com.accountledger.savingsservice.dto;

import com.accountledger.savingsservice.enums.SavingsAccountStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SavingsAccountResponse {

  private Long id;
  private Long clientId;
  private String productName;
  private String accountNumber;
  private BigDecimal balance;
  private BigDecimal availableBalance;
  private SavingsAccountStatus status;
  private LocalDateTime createdAt;
}
