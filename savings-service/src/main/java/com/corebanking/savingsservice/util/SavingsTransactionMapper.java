package com.corebanking.savingsservice.util;

import com.corebanking.savingsservice.dto.SavingsTransactionResponse;
import com.corebanking.savingsservice.entity.SavingsTransaction;

public class SavingsTransactionMapper {

  public static SavingsTransactionResponse toResponse(SavingsTransaction transaction) {
    return SavingsTransactionResponse.builder()
        .id(transaction.getId())
        .accountId(transaction.getAccount().getId())
        .transactionType(transaction.getTransactionType())
        .amount(transaction.getAmount())
        .balanceBefore(transaction.getBalanceBefore())
        .balanceAfter(transaction.getBalanceAfter())
        .description(transaction.getDescription())
        .transactionDate(transaction.getTransactionDate())
        .build();
  }

  private SavingsTransactionMapper() {}
}
