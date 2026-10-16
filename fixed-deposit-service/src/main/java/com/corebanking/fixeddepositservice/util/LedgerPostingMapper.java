package com.corebanking.fixeddepositservice.util;

import com.corebanking.fixeddepositservice.dto.LedgerPostingResponse;
import com.corebanking.fixeddepositservice.entity.LedgerPosting;

public class LedgerPostingMapper {

  public static LedgerPostingResponse toResponse(LedgerPosting posting) {
    return LedgerPostingResponse.builder()
        .id(posting.getId())
        .transactionType(posting.getTransactionType())
        .amount(posting.getAmount())
        .transactionReference(posting.getTransactionReference())
        .description(posting.getDescription())
        .status(posting.getStatus())
        .attempts(posting.getAttempts())
        .lastError(posting.getLastError())
        .createdAt(posting.getCreatedAt())
        .postedAt(posting.getPostedAt())
        .build();
  }

  private LedgerPostingMapper() {}
}
