package com.accountledger.savingsservice.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InterestRunResponse {

  /* The run behaved as if today were this date. */
  private LocalDate asOf;

  /* Account-days of interest recorded. */
  private int daysAccrued;

  private int accountsPaid;
  private BigDecimal totalPaid;
}
