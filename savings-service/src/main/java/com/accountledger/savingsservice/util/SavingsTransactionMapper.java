package com.accountledger.savingsservice.util;

import com.accountledger.savingsservice.dto.SavingsTransactionResponse;
import com.accountledger.savingsservice.entity.SavingsTransaction;

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
