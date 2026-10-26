package com.accountledger.fixeddepositservice.service;

import com.accountledger.fixeddepositservice.entity.FixedDeposit;
import com.accountledger.fixeddepositservice.enums.FixedDepositStatus;
import com.accountledger.fixeddepositservice.exception.FixedDepositNotFoundException;
import com.accountledger.fixeddepositservice.exception.FundingRefusedException;
import com.accountledger.fixeddepositservice.repository.FixedDepositRepository;
import com.accountledger.fixeddepositservice.util.RemoteErrors;
import com.accountledger.fixeddepositservice.util.SavingsServiceClient;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/*
 * Takes the money for deposits paid for from a savings account. The deposit
 * is saved as PENDING_FUNDING first, so a deposit is never lost after its
 * money has been taken. Then:
 *
 * - the savings service takes the money: the deposit becomes ACTIVE and its
 *   term starts today;
 * - it refuses (a 4xx answer other than 408 or 429, for example not enough
 *   money): the deposit becomes FUNDING_FAILED and nothing was taken;
 * - there is no clear answer: the deposit stays PENDING_FUNDING and the
 *   retry job tries again. The savings service ignores a reference it has
 *   already taken, so the money is never taken twice.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FixedDepositFundingService {

  private static final int MAX_ERROR_LENGTH = 255;

  private final FixedDepositRepository fixedDepositRepository;
  private final SavingsServiceClient savingsServiceClient;

  /*
   * Tries to take the money for the deposit once. Returns the deposit as it
   * now stands; throws FundingRefusedException if the account refused.
   */
  public FixedDeposit fund(Long depositId) {
    FixedDeposit deposit =
        fixedDepositRepository
            .findById(depositId)
            .orElseThrow(
                () ->
                    new FixedDepositNotFoundException(
                        "Fixed deposit not found with ID: " + depositId));
    if (deposit.getStatus() != FixedDepositStatus.PENDING_FUNDING) {
      return deposit;
    }

    deposit.setFundingAttempts(deposit.getFundingAttempts() + 1);
    try {
      savingsServiceClient.fund(deposit);
    } catch (Exception e) {
      deposit.setFundingError(truncate(RemoteErrors.describe("Savings service", e)));
      if (RemoteErrors.isPermanent(e)) {
        deposit.setStatus(FixedDepositStatus.FUNDING_FAILED);
        fixedDepositRepository.save(deposit);
        log.warn(
            "Funding for fixed deposit {} was refused: {}", depositId, deposit.getFundingError());
        throw new FundingRefusedException(
            "Fixed deposit "
                + depositId
                + " could not be paid for from savings account "
                + deposit.getFundingAccountId()
                + ". "
                + deposit.getFundingError());
      }
      log.warn(
          "Funding for fixed deposit {} failed (attempt {}), will retry: {}",
          depositId,
          deposit.getFundingAttempts(),
          deposit.getFundingError());
      return fixedDepositRepository.save(deposit);
    }

    LocalDate today = LocalDate.now();
    deposit.setDepositDate(today);
    deposit.setMaturityDate(today.plusDays(deposit.getTenureInDays()));
    deposit.setStatus(FixedDepositStatus.ACTIVE);
    deposit.setFundingError(null);
    log.info(
        "Fixed deposit {} funded from savings account {}, maturing on {}",
        depositId,
        deposit.getFundingAccountId(),
        deposit.getMaturityDate());
    return fixedDepositRepository.save(deposit);
  }

  /*
   * Retries deposits still waiting for their money. Only deposits created at
   * least a minute ago, so this does not race the request that created one
   * and is still waiting for the savings service.
   */
  @Scheduled(fixedDelayString = "${fixed-deposit.funding.retry-delay-ms:30000}")
  public void fundPending() {
    for (FixedDeposit deposit :
        fixedDepositRepository.findTop50ByStatusAndCreatedAtBeforeOrderByIdAsc(
            FixedDepositStatus.PENDING_FUNDING, LocalDateTime.now().minusMinutes(1))) {
      try {
        fund(deposit.getId());
      } catch (FundingRefusedException e) {
        // Recorded on the deposit by fund.
      } catch (Exception e) {
        log.error("Could not fund fixed deposit {}, will retry", deposit.getId(), e);
      }
    }
  }

  private String truncate(String message) {
    if (message == null || message.length() <= MAX_ERROR_LENGTH) {
      return message;
    }
    return message.substring(0, MAX_ERROR_LENGTH);
  }
}
