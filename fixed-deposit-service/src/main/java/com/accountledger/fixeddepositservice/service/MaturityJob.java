package com.accountledger.fixeddepositservice.service;

import com.accountledger.fixeddepositservice.entity.FixedDeposit;
import com.accountledger.fixeddepositservice.enums.FixedDepositStatus;
import com.accountledger.fixeddepositservice.exception.FixedDepositNotActiveException;
import com.accountledger.fixeddepositservice.repository.FixedDepositRepository;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/*
 * Matures every active deposit whose maturity date has come, each night. Each
 * deposit goes through the same maturity as POST /fixed-deposits/{id}/mature,
 * so it is locked, checked and paid out exactly as if someone had asked. A
 * deposit that fails is left ACTIVE and tried again on the next run.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MaturityJob {

  private final FixedDepositService fixedDepositService;
  private final FixedDepositRepository fixedDepositRepository;

  @Scheduled(cron = "${fixed-deposit.maturity.cron:0 30 0 * * *}")
  public void runNightly() {
    run(LocalDate.now());
  }

  /* Returns how many deposits were matured. */
  public int run(LocalDate today) {
    int matured = 0;
    for (FixedDeposit due :
        fixedDepositRepository.findByStatusAndMaturityDateLessThanEqual(
            FixedDepositStatus.ACTIVE, today)) {
      try {
        fixedDepositService.matureDeposit(due.getId());
        matured++;
      } catch (FixedDepositNotActiveException e) {
        // Matured or withdrawn by hand since we listed it.
        log.info("Fixed deposit {} no longer active, skipped", due.getId());
      } catch (Exception e) {
        log.error("Could not mature fixed deposit {}, will retry next run", due.getId(), e);
      }
    }
    log.info("Maturity run as of {}: {} deposits matured", today, matured);
    return matured;
  }
}
