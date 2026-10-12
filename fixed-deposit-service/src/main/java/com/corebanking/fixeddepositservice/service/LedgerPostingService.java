package com.corebanking.fixeddepositservice.service;

import com.corebanking.fixeddepositservice.entity.LedgerPosting;
import com.corebanking.fixeddepositservice.enums.LedgerPostingStatus;
import com.corebanking.fixeddepositservice.repository.LedgerPostingRepository;
import com.corebanking.fixeddepositservice.util.LedgerClient;
import feign.FeignException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerPostingService {

  private static final int MAX_ERROR_LENGTH = 255;

  private final LedgerPostingRepository ledgerPostingRepository;
  private final LedgerClient ledgerClient;

  /*
   * Records a posting as part of the caller's transaction: it commits or
   * rolls back together with the balance change it describes.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public void record(
      String transactionType, BigDecimal amount, String reference, String description) {
    LedgerPosting posting = new LedgerPosting();
    posting.setTransactionType(transactionType);
    posting.setAmount(amount);
    posting.setTransactionReference(reference);
    posting.setDescription(description);
    ledgerPostingRepository.save(posting);
  }

  /*
   * Sends pending postings to the ledger service. A posting stays PENDING and
   * is retried on the next run if the ledger service fails; the ledger ignores
   * a reference it has already posted, so retrying is safe.
   *
   * A posting the ledger service rejects as invalid (a 4xx answer) would be
   * rejected again every time, so it is marked FAILED and left for someone to
   * look at instead of being retried forever ahead of newer postings.
   */
  @Scheduled(fixedDelayString = "${ledger.posting.retry-delay-ms:5000}")
  public void publishPending() {
    for (LedgerPosting posting :
        ledgerPostingRepository.findTop50ByStatusOrderByIdAsc(LedgerPostingStatus.PENDING)) {
      try {
        ledgerClient.post(posting);
        posting.setStatus(LedgerPostingStatus.POSTED);
        posting.setPostedAt(LocalDateTime.now());
        posting.setLastError(null);
        log.info("Ledger entry posted for reference: {}", posting.getTransactionReference());
      } catch (FeignException.FeignClientException e) {
        posting.setStatus(LedgerPostingStatus.FAILED);
        posting.setAttempts(posting.getAttempts() + 1);
        posting.setLastError(truncate(e.getMessage()));
        log.error(
            "Ledger posting {} was rejected by the ledger service and will not be retried: {}",
            posting.getTransactionReference(),
            e.getMessage());
      } catch (Exception e) {
        posting.setAttempts(posting.getAttempts() + 1);
        posting.setLastError(truncate(e.getMessage()));
        log.warn(
            "Ledger posting {} failed (attempt {}), will retry: {}",
            posting.getTransactionReference(),
            posting.getAttempts(),
            e.getMessage());
      }
      ledgerPostingRepository.save(posting);
    }
  }

  private String truncate(String message) {
    if (message == null || message.length() <= MAX_ERROR_LENGTH) {
      return message;
    }
    return message.substring(0, MAX_ERROR_LENGTH);
  }
}
