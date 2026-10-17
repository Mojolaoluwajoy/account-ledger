package com.corebanking.fixeddepositservice.dto;

import com.corebanking.fixeddepositservice.enums.LedgerPostingStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LedgerPostingResponse {

  private Long id;
  private String transactionType;
  private BigDecimal amount;
  private String transactionReference;
  private String description;
  private LedgerPostingStatus status;
  private int attempts;
  private String lastError;
  private LocalDateTime createdAt;
  private LocalDateTime postedAt;
}
