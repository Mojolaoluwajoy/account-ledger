package com.corebanking.fixeddepositservice.service;

import com.corebanking.fixeddepositservice.entity.FixedDeposit;
import com.corebanking.fixeddepositservice.enums.PayoutStatus;
import com.corebanking.fixeddepositservice.exception.FixedDepositNotFoundException;
import com.corebanking.fixeddepositservice.exception.NotRetryableException;
import com.corebanking.fixeddepositservice.repository.FixedDepositRepository;
import com.corebanking.fixeddepositservice.util.RemoteErrors;
import com.corebanking.fixeddepositservice.util.SavingsServiceClient;
import feign.FeignException;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FixedDepositPayoutService {

  private static final int MAX_ERROR_LENGTH = 255;

  private final FixedDepositRepository fixedDepositRepository;
  private final SavingsServiceClient savingsServiceClient;

  /*
   * Pays matured deposits into their payout accounts. A payout stays PENDING
   * and is retried on the next run if the savings service fails; the savings
   * service ignores a payout it has already credited, so retrying is safe.
   *
   * A payout the savings service rejects (a 4xx answer, for example because
   * the account has been closed) would be rejected again every time, so it is
   * marked FAILED and left for someone to look at.
   */
  @Scheduled(fixedDelayString = "${fixed-deposit.payout.retry-delay-ms:5000}")
  public void payOutPending() {
    for (FixedDeposit deposit :
        fixedDepositRepository.findTop50ByPayoutStatusOrderByIdAsc(PayoutStatus.PENDING)) {
      String reference = SavingsServiceClient.payoutReference(deposit);
      try {
        savingsServiceClient.payOut(deposit);
        deposit.setPayoutStatus(PayoutStatus.PAID);
        deposit.setPaidOutAt(LocalDateTime.now());
        deposit.setPayoutError(null);
        log.info(
            "Fixed deposit {} paid into savings account {}",
            deposit.getId(),
            deposit.getPayoutAccountId());
      } catch (FeignException.FeignClientException e) {
        deposit.setPayoutStatus(PayoutStatus.FAILED);
        deposit.setPayoutAttempts(deposit.getPayoutAttempts() + 1);
        deposit.setPayoutError(truncate(RemoteErrors.describe("Savings service", e)));
        log.error(
            "Payout {} was rejected by the savings service and will not be retried: {}",
            reference,
            deposit.getPayoutError());
      } catch (Exception e) {
        deposit.setPayoutAttempts(deposit.getPayoutAttempts() + 1);
        deposit.setPayoutError(truncate(RemoteErrors.describe("Savings service", e)));
        log.warn(
            "Payout {} failed (attempt {}), will retry: {}",
            reference,
            deposit.getPayoutAttempts(),
            deposit.getPayoutError());
      }
      fixedDepositRepository.save(deposit);
    }
  }

  public List<FixedDeposit> findByPayoutStatus(PayoutStatus status) {
    return fixedDepositRepository.findByPayoutStatusOrderByIdAsc(status);
  }

  /*
   * Puts a FAILED payout back in the queue, optionally into a different
   * savings account. payOutPending picks it up on its next run. The payout
   * keeps its reference, so it still cannot be credited twice.
   */
  @Transactional
  public FixedDeposit retry(Long depositId, Long newPayoutAccountId) {
    FixedDeposit deposit =
        fixedDepositRepository
            .findByIdForUpdate(depositId)
            .orElseThrow(
                () ->
                    new FixedDepositNotFoundException(
                        "Fixed deposit not found with ID: " + depositId));

    if (deposit.getPayoutStatus() != PayoutStatus.FAILED) {
      throw new NotRetryableException(
          "Payout of fixed deposit "
              + depositId
              + " is "
              + deposit.getPayoutStatus()
              + "; only FAILED payouts can be retried");
    }

    if (newPayoutAccountId != null) {
      savingsServiceClient.requirePayoutAccount(newPayoutAccountId, deposit.getClientId());
      deposit.setPayoutAccountId(newPayoutAccountId);
    }

    deposit.setPayoutStatus(PayoutStatus.PENDING);
    log.info(
        "Payout of fixed deposit {} queued again into savings account {}",
        depositId,
        deposit.getPayoutAccountId());
    return fixedDepositRepository.save(deposit);
  }

  private String truncate(String message) {
    if (message == null || message.length() <= MAX_ERROR_LENGTH) {
      return message;
    }
    return message.substring(0, MAX_ERROR_LENGTH);
  }
}
