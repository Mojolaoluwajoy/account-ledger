package com.corebanking.savingsservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.corebanking.savingsservice.entity.LedgerPosting;
import com.corebanking.savingsservice.enums.LedgerPostingStatus;
import com.corebanking.savingsservice.repository.LedgerPostingRepository;
import com.corebanking.savingsservice.service.LedgerPostingService;
import com.corebanking.savingsservice.util.LedgerClient;
import feign.FeignException;
import feign.Request;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LedgerPostingServiceTest {

  @Mock private LedgerPostingRepository ledgerPostingRepository;

  @Mock private LedgerClient ledgerClient;

  @InjectMocks private LedgerPostingService ledgerPostingService;

  private LedgerPosting posting;

  @BeforeEach
  void setUp() {
    posting = new LedgerPosting();
    posting.setId(1L);
    posting.setTransactionType("TEST_TYPE");
    posting.setAmount(new BigDecimal("100.00"));
    posting.setTransactionReference("REF-1");

    when(ledgerPostingRepository.findTop50ByStatusOrderByIdAsc(LedgerPostingStatus.PENDING))
        .thenReturn(List.of(posting));
  }

  @Test
  void publishPending_whenLedgerAccepts_shouldMarkPosted() {
    ledgerPostingService.publishPending();

    assertEquals(LedgerPostingStatus.POSTED, posting.getStatus());
    assertNotNull(posting.getPostedAt());
    verify(ledgerPostingRepository).save(posting);
  }

  @Test
  void publishPending_whenLedgerFails_shouldStayPendingForRetry() {
    doThrow(new RuntimeException("ledger down")).when(ledgerClient).post(posting);

    ledgerPostingService.publishPending();

    assertEquals(LedgerPostingStatus.PENDING, posting.getStatus());
    assertEquals(1, posting.getAttempts());
    assertEquals("ledger down", posting.getLastError());
    verify(ledgerPostingRepository).save(posting);
  }

  @Test
  void publishPending_whenLedgerRejects_shouldMarkFailedAndNotRetry() {
    Request request =
        Request.create(
            Request.HttpMethod.POST,
            "http://localhost:8084/api/v1/ledger/entries",
            Map.of(),
            null,
            StandardCharsets.UTF_8,
            null);
    byte[] body =
        "{\"success\":false,\"message\":\"Unknown transaction type: TEST_TYPE\"}"
            .getBytes(StandardCharsets.UTF_8);
    doThrow(new FeignException.BadRequest("Bad Request", request, body, Map.of()))
        .when(ledgerClient)
        .post(posting);

    ledgerPostingService.publishPending();

    assertEquals(LedgerPostingStatus.FAILED, posting.getStatus());
    assertEquals(1, posting.getAttempts());
    assertEquals(
        "Ledger service answered 400: Unknown transaction type: TEST_TYPE", posting.getLastError());
    verify(ledgerPostingRepository).save(posting);
  }
}
