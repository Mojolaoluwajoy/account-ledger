package com.accountledger.fixeddepositservice.util;

import com.accountledger.fixeddepositservice.dto.SavingsAccountLookupResponse;
import com.accountledger.fixeddepositservice.entity.FixedDeposit;
import com.accountledger.fixeddepositservice.exception.InvalidPayoutAccountException;
import com.accountledger.fixeddepositservice.exception.SavingsServiceUnavailableException;
import com.accountledger.fixeddepositservice.feign.SavingsServiceApi;
import feign.FeignException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SavingsServiceClient {

  private static final String ACTIVE = "ACTIVE";

  private final SavingsServiceApi savingsServiceApi;

  /*
   * The payout account must be an active savings account that belongs to the
   * client placing the deposit.
   */
  public void requirePayoutAccount(Long accountId, Long clientId) {
    SavingsAccountLookupResponse response;
    try {
      response = savingsServiceApi.getAccountById(accountId);
    } catch (FeignException.NotFound e) {
      throw new InvalidPayoutAccountException("Payout account not found with ID: " + accountId);
    } catch (FeignException e) {
      log.error("Could not verify payout account {} with savings service", accountId, e);
      throw new SavingsServiceUnavailableException(
          "Could not verify payout account " + accountId + " right now. Please try again shortly.");
    }

    SavingsAccountLookupResponse.Account account = response == null ? null : response.getData();
    if (account == null || account.getStatus() == null || account.getClientId() == null) {
      log.error("Savings service answered for account {} without its details", accountId);
      throw new SavingsServiceUnavailableException(
          "Could not verify payout account " + accountId + " right now. Please try again shortly.");
    }
    if (!account.getClientId().equals(clientId)) {
      throw new InvalidPayoutAccountException(
          "Payout account " + accountId + " does not belong to client " + clientId);
    }
    if (!ACTIVE.equals(account.getStatus())) {
      throw new InvalidPayoutAccountException(
          "Payout account " + accountId + " is not active. Current status: " + account.getStatus());
    }
  }

  /*
   * Pays the matured deposit into its payout account. Throws if the savings
   * service rejects it or cannot be reached, so the caller knows to try again.
   * The savings service ignores a reference it has already credited.
   */
  public void payOut(FixedDeposit deposit) {
    Map<String, Object> request = new HashMap<>();
    request.put("reference", payoutReference(deposit));
    request.put("principal", deposit.getPrincipalAmount());
    request.put("interest", interestToPay(deposit));

    savingsServiceApi.creditFixedDepositPayout(deposit.getPayoutAccountId(), request);
  }

  /* Deposits that ended before payoutInterest was recorded were all paid in full. */
  private static BigDecimal interestToPay(FixedDeposit deposit) {
    return deposit.getPayoutInterest() != null
        ? deposit.getPayoutInterest()
        : deposit.getMaturityAmount().subtract(deposit.getPrincipalAmount());
  }

  public static String payoutReference(FixedDeposit deposit) {
    return "FD-PAYOUT-" + deposit.getId();
  }
}
