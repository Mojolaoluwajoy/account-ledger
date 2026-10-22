package com.accountledger.savingsservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class OpenSavingsAccountRequest {

  @NotNull(message = "Client ID is required")
  private Long clientId;

  @NotNull(message = "Product ID is required")
  private Long productId;

  @NotNull(message = "Initial deposit is required")
  @DecimalMin(
      value = "0.0",
      inclusive = false,
      message = "Initial deposit must be greater than zero")
  @Digits(integer = 36, fraction = 2, message = "Initial deposit can have at most 2 decimal places")
  private BigDecimal initialDeposit;
}
