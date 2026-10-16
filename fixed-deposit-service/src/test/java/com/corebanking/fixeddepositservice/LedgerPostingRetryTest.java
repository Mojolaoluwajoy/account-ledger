package com.corebanking.fixeddepositservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.corebanking.fixeddepositservice.entity.LedgerPosting;
import com.corebanking.fixeddepositservice.enums.LedgerPostingStatus;
import com.corebanking.fixeddepositservice.exception.LedgerPostingNotFoundException;
import com.corebanking.fixeddepositservice.exception.NotRetryableException;
import com.corebanking.fixeddepositservice.repository.LedgerPostingRepository;
import com.corebanking.fixeddepositservice.service.LedgerPostingService;
import com.corebanking.fixeddepositservice.util.LedgerClient;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LedgerPostingRetryTest {

  @Mock private LedgerPostingRepository ledgerPostingRepository;

  @Mock private LedgerClient ledgerClient;

  @InjectMocks private LedgerPostingService ledgerPostingService;

  @Test
  void retry_failedPosting_shouldQueueItAgain() {
    LedgerPosting posting = postingWithStatus(LedgerPostingStatus.FAILED);
    when(ledgerPostingRepository.findById(1L)).thenReturn(Optional.of(posting));
    when(ledgerPostingRepository.save(posting)).thenReturn(posting);

    LedgerPosting retried = ledgerPostingService.retry(1L);

    assertEquals(LedgerPostingStatus.PENDING, retried.getStatus());
  }

  @Test
  void retry_postedPosting_shouldThrowNotRetryable() {
    when(ledgerPostingRepository.findById(1L))
        .thenReturn(Optional.of(postingWithStatus(LedgerPostingStatus.POSTED)));

    assertThrows(NotRetryableException.class, () -> ledgerPostingService.retry(1L));

    verify(ledgerPostingRepository, never()).save(any());
  }

  @Test
  void retry_unknownPosting_shouldThrowNotFound() {
    when(ledgerPostingRepository.findById(99L)).thenReturn(Optional.empty());

    assertThrows(LedgerPostingNotFoundException.class, () -> ledgerPostingService.retry(99L));
  }

  private LedgerPosting postingWithStatus(LedgerPostingStatus status) {
    LedgerPosting posting = new LedgerPosting();
    posting.setId(1L);
    posting.setTransactionReference("REF-1");
    posting.setStatus(status);
    return posting;
  }
}
