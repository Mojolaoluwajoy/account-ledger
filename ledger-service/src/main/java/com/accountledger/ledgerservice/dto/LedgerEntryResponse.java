package com.accountledger.ledgerservice.dto;

import com.accountledger.ledgerservice.enums.TransactionEntryType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LedgerEntryResponse {

  private Long id;
  private String transactionReference;
  private TransactionEntryType transactionType;
  private String debitAccountCode;
  private String debitAccountName;
  private String creditAccountCode;
  private String creditAccountName;
  private BigDecimal amount;
  private String description;
  private LocalDateTime entryDate;
}
