package com.corebanking.fixeddepositservice.util;

import com.corebanking.fixeddepositservice.dto.FixedDepositResponse;
import com.corebanking.fixeddepositservice.entity.FixedDeposit;

public class FixedDepositMapper {

  public static FixedDepositResponse toResponse(FixedDeposit deposit) {
    return FixedDepositResponse.builder()
        .id(deposit.getId())
        .clientId(deposit.getClientId())
        .principalAmount(deposit.getPrincipalAmount())
        .interestRate(deposit.getInterestRate())
        .tenureInDays(deposit.getTenureInDays())
        .depositDate(deposit.getDepositDate())
        .maturityDate(deposit.getMaturityDate())
        .maturityAmount(deposit.getMaturityAmount())
        .status(deposit.getStatus())
        .createdAt(deposit.getCreatedAt())
        .payoutAccountId(deposit.getPayoutAccountId())
        .payoutStatus(deposit.getPayoutStatus())
        .payoutError(deposit.getPayoutError())
        .paidOutAt(deposit.getPaidOutAt())
        .build();
  }

  private FixedDepositMapper() {}
}
