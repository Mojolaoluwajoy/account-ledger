package com.accountledger.savingsservice.util;

import com.accountledger.savingsservice.dto.LedgerPostingResponse;
import com.accountledger.savingsservice.entity.LedgerPosting;

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
