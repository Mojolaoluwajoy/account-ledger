package com.accountledger.savingsservice.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SavingsProductResponse {

  private Long id;
  private String name;
  private String shortName;
  private String description;
  private BigDecimal nominalAnnualInterestRate;
  private BigDecimal minimumOpeningBalance;
  private BigDecimal minimumBalance;
  private boolean enforceMinimumBalance;
  private LocalDateTime createdAt;
}
