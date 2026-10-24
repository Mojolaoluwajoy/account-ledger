package com.accountledger.fixeddepositservice.util;

import com.accountledger.fixeddepositservice.dto.SavingsAccountLookupResponse;
import com.accountledger.fixeddepositservice.entity.FixedDeposit;
import com.accountledger.fixeddepositservice.exception.InvalidFundingAccountException;
import com.accountledger.fixeddepositservice.exception.InvalidPayoutAccountException;
import com.accountledger.fixeddepositservice.exception.SavingsServiceUnavailableException;
import com.accountledger.fixeddepositservice.feign.SavingsServiceApi;
import feign.FeignException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
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
    requireClientAccount(accountId, clientId, "Payout", InvalidPayoutAccountException::new);
  }

  /* The same rule for the account a deposit is paid for from. */
  public void requireFundingAccount(Long accountId, Long clientId) {
    requireClientAccount(accountId, clientId, "Funding", InvalidFundingAccountException::new);
  }

  private void requireClientAccount(
      Long accountId, Long clientId, String role, Function<String, RuntimeException> invalid) {
    String lowerRole = role.toLowerCase();
    SavingsAccountLookupResponse response;
    try {
      response = savingsServiceApi.getAccountById(accountId);
    } catch (FeignException.NotFound e) {
      throw invalid.apply(role + " account not found with ID: " + accountId);
    } catch (FeignException e) {
      log.error("Could not verify {} account {} with savings service", lowerRole, accountId, e);
      throw new SavingsServiceUnavailableException(
          "Could not verify "
              + lowerRole
              + " account "
              + accountId
              + " right now. Please try again shortly.");
    }

    SavingsAccountLookupResponse.Account account = response == null ? null : response.getData();
    if (account == null || account.getStatus() == null || account.getClientId() == null) {
      log.error("Savings service answered for account {} without its details", accountId);
      throw new SavingsServiceUnavailableException(
          "Could not verify "
              + lowerRole
              + " account "
              + accountId
              + " right now. Please try again shortly.");
    }
    if (!account.getClientId().equals(clientId)) {
      throw invalid.apply(
          role + " account " + accountId + " does not belong to client " + clientId);
    }
    if (!ACTIVE.equals(account.getStatus())) {
      throw invalid.apply(
          role
              + " account "
              + accountId
              + " is not active. Current status: "
              + account.getStatus());
    }
  }

  /*
   * Takes the deposit's principal from its funding account. Throws if the
   * savings service refuses or cannot be reached. The savings service ignores
   * a reference it has already taken, so calling this again is safe.
   */
  public void fund(FixedDeposit deposit) {
    Map<String, Object> request = new HashMap<>();
    request.put("reference", fundingReference(deposit));
    request.put("amount", deposit.getPrincipalAmount());

    savingsServiceApi.debitFixedDepositFunding(deposit.getFundingAccountId(), request);
  }

  public static String fundingReference(FixedDeposit deposit) {
    return "FD-FUND-" + deposit.getId();
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
