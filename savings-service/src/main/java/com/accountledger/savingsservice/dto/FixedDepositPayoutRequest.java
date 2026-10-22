package com.accountledger.savingsservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Data;

/* Sent by the fixed deposit service when a deposit matures. */
@Data
public class FixedDepositPayoutRequest {

  @NotBlank(message = "Reference is required")
  private String reference;

  @NotNull(message = "Principal is required")
  @DecimalMin(value = "0.0", inclusive = false, message = "Principal must be greater than zero")
  @Digits(integer = 36, fraction = 2, message = "Principal can have at most 2 decimal places")
  private BigDecimal principal;

  @NotNull(message = "Interest is required")
  @DecimalMin(value = "0.0", message = "Interest cannot be negative")
  @Digits(integer = 36, fraction = 2, message = "Interest can have at most 2 decimal places")
  private BigDecimal interest;
}
