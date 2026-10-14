package com.corebanking.fixeddepositservice.service;

import com.corebanking.fixeddepositservice.entity.FixedDeposit;
import com.corebanking.fixeddepositservice.enums.PayoutStatus;
import com.corebanking.fixeddepositservice.repository.FixedDepositRepository;
import com.corebanking.fixeddepositservice.util.SavingsServiceClient;
import feign.FeignException;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

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
        deposit.setPayoutError(truncate(e.getMessage()));
        log.error(
            "Payout {} was rejected by the savings service and will not be retried: {}",
            reference,
            e.getMessage());
      } catch (Exception e) {
        deposit.setPayoutAttempts(deposit.getPayoutAttempts() + 1);
        deposit.setPayoutError(truncate(e.getMessage()));
        log.warn(
            "Payout {} failed (attempt {}), will retry: {}",
            reference,
            deposit.getPayoutAttempts(),
            e.getMessage());
      }
      fixedDepositRepository.save(deposit);
    }
  }

  private String truncate(String message) {
    if (message == null || message.length() <= MAX_ERROR_LENGTH) {
      return message;
    }
    return message.substring(0, MAX_ERROR_LENGTH);
  }
}
