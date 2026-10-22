package com.accountledger.fixeddepositservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class CreateFixedDepositRequest {

  @NotNull(message = "Client ID is required")
  private Long clientId;

  @NotNull(message = "Principal amount is required")
  @DecimalMin(value = "0.0", inclusive = false, message = "Principal must be greater than zero")
  @Digits(integer = 36, fraction = 2, message = "Principal can have at most 2 decimal places")
  private BigDecimal principalAmount;

  @NotNull(message = "Interest rate is required")
  @DecimalMin(value = "0.0", inclusive = false, message = "Interest rate must be greater than zero")
  @Digits(integer = 36, fraction = 2, message = "Interest rate can have at most 2 decimal places")
  private BigDecimal interestRate;

  @NotNull(message = "Payout account ID is required")
  private Long payoutAccountId;

  @NotNull(message = "Tenure is required")
  @Min(value = 1, message = "Tenure must be at least 1 day")
  private Integer tenureInDays;
}
