package com.accountledger.savingsservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class CreateSavingsProductRequest {

  @NotBlank(message = "Product name is required")
  private String name;

  @NotBlank(message = "Short name is required")
  private String shortName;

  private String description;

  @NotNull(message = "Interest rate is required")
  @DecimalMin(value = "0.0", message = "Interest rate must be positive")
  @Digits(integer = 36, fraction = 2, message = "Interest rate can have at most 2 decimal places")
  private BigDecimal nominalAnnualInterestRate;

  @NotNull(message = "Minimum opening balance is required")
  @DecimalMin(value = "0.0", message = "Minimum opening balance must be positive")
  @Digits(
      integer = 36,
      fraction = 2,
      message = "Minimum opening balance can have at most 2 decimal places")
  private BigDecimal minimumOpeningBalance;

  @NotNull(message = "Minimum balance is required")
  @DecimalMin(value = "0.0", message = "Minimum balance must be positive")
  @Digits(integer = 36, fraction = 2, message = "Minimum balance can have at most 2 decimal places")
  private BigDecimal minimumBalance;

  private boolean enforceMinimumBalance;
}
