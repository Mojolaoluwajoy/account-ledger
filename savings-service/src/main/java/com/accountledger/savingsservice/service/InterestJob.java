package com.accountledger.savingsservice.service;

import com.accountledger.savingsservice.dto.InterestRunResponse;
import com.accountledger.savingsservice.enums.SavingsAccountStatus;
import com.accountledger.savingsservice.exception.InterestRunInProgressException;
import com.accountledger.savingsservice.repository.InterestAccrualRepository;
import com.accountledger.savingsservice.repository.SavingsAccountRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/*
 * Runs savings interest every night: records yesterday's interest for every
 * active account (catching up any days missed), then pays out every month
 * that has ended. Each account is handled on its own, so one failing account
 * does not hold up the others; it is picked up again on the next run.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InterestJob {

  private final InterestService interestService;
  private final SavingsAccountRepository savingsAccountRepository;
  private final InterestAccrualRepository interestAccrualRepository;

  private final AtomicBoolean running = new AtomicBoolean();

  @Scheduled(cron = "${savings.interest.cron:0 15 0 * * *}")
  public void runNightly() {
    run(LocalDate.now());
  }

  /* Behaves as if today were the given date: interest up to yesterday, paid for ended months. */
  public InterestRunResponse run(LocalDate today) {
    if (!running.compareAndSet(false, true)) {
      throw new InterestRunInProgressException("An interest run is already in progress");
    }
    try {
      LocalDate yesterday = today.minusDays(1);
      int daysAccrued = 0;
      for (Long accountId : savingsAccountRepository.findIdsByStatus(SavingsAccountStatus.ACTIVE)) {
        try {
          daysAccrued += interestService.accrue(accountId, yesterday);
        } catch (Exception e) {
          log.error("Could not record interest for account {}, will retry next run", accountId, e);
        }
      }

      LocalDate monthStart = today.withDayOfMonth(1);
      int accountsPaid = 0;
      BigDecimal totalPaid = BigDecimal.ZERO;
      for (Long accountId :
          interestAccrualRepository.findAccountIdsWithUnpostedAccrualsBefore(monthStart)) {
        try {
          BigDecimal paid = interestService.post(accountId, monthStart);
          if (paid.signum() > 0) {
            accountsPaid++;
            totalPaid = totalPaid.add(paid);
          }
        } catch (Exception e) {
          log.error("Could not pay interest into account {}, will retry next run", accountId, e);
        }
      }

      log.info(
          "Interest run as of {}: {} account-days recorded, {} accounts paid {}",
          today,
          daysAccrued,
          accountsPaid,
          totalPaid);
      return InterestRunResponse.builder()
          .asOf(today)
          .daysAccrued(daysAccrued)
          .accountsPaid(accountsPaid)
          .totalPaid(totalPaid)
          .build();
    } finally {
      running.set(false);
    }
  }
}
