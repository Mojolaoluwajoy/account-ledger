package com.accountledger.ledgerservice.dto;

import com.accountledger.ledgerservice.enums.TransactionEntryType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class PostLedgerEntryRequest {

  @NotNull(message = "Transaction type is required")
  private TransactionEntryType transactionType;

  @NotNull(message = "Amount is required")
  @DecimalMin(value = "0.0", inclusive = false, message = "Amount must be greater than zero")
  private BigDecimal amount;

  private String transactionReference;
  private String description;
}
