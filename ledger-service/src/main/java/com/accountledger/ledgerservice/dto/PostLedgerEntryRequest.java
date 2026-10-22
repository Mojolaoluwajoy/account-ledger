package com.accountledger.ledgerservice.dto;

import com.accountledger.ledgerservice.enums.TransactionEntryType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class PostLedgerEntryRequest {

  @NotNull(message = "Transaction type is required")
  private TransactionEntryType transactionType;

  @NotNull(message = "Amount is required")
  @DecimalMin(value = "0.0", inclusive = false, message = "Amount must be greater than zero")
  @Digits(integer = 36, fraction = 2, message = "Amount can have at most 2 decimal places")
  private BigDecimal amount;

  private String transactionReference;
  private String description;
}
