package com.accountledger.fixeddepositservice.dto;

import com.accountledger.fixeddepositservice.enums.FixedDepositStatus;
import com.accountledger.fixeddepositservice.enums.PayoutStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FixedDepositResponse {

  private Long id;
  private Long clientId;
  private BigDecimal principalAmount;
  private BigDecimal interestRate;
  private Integer tenureInDays;
  private LocalDate depositDate;
  private LocalDate maturityDate;
  private BigDecimal maturityAmount;
  private FixedDepositStatus status;
  private LocalDateTime createdAt;
  private Long payoutAccountId;
  private PayoutStatus payoutStatus;
  private String payoutError;
  private LocalDateTime paidOutAt;
  private BigDecimal payoutInterest;
  private LocalDate closedOn;
}
