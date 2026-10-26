package com.accountledger.fixeddepositservice.util;

import com.accountledger.fixeddepositservice.dto.FixedDepositResponse;
import com.accountledger.fixeddepositservice.entity.FixedDeposit;

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
        .payoutInterest(deposit.getPayoutInterest())
        .closedOn(deposit.getClosedOn())
        .fundingAccountId(deposit.getFundingAccountId())
        .fundingError(deposit.getFundingError())
        .fundingAttempts(deposit.getFundingAttempts())
        .build();
  }

  private FixedDepositMapper() {}
}
