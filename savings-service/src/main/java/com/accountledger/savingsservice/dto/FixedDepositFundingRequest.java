package com.accountledger.savingsservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Data;

/* Sent by the fixed deposit service to pay for a new deposit from a savings account. */
@Data
public class FixedDepositFundingRequest {

  @NotBlank(message = "Reference is required")
  private String reference;

  @NotNull(message = "Amount is required")
  @DecimalMin(value = "0.0", inclusive = false, message = "Amount must be greater than zero")
  @Digits(integer = 36, fraction = 2, message = "Amount can have at most 2 decimal places")
  private BigDecimal amount;
}
