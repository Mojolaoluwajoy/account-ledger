package com.accountledger.fixeddepositservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.accountledger.fixeddepositservice.entity.FixedDeposit;
import com.accountledger.fixeddepositservice.enums.FixedDepositStatus;
import com.accountledger.fixeddepositservice.exception.FundingRefusedException;
import com.accountledger.fixeddepositservice.repository.FixedDepositRepository;
import com.accountledger.fixeddepositservice.service.FixedDepositFundingService;
import com.accountledger.fixeddepositservice.util.SavingsServiceClient;
import feign.FeignException;
import feign.Request;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FixedDepositFundingServiceTest {

  private static final Request REQUEST =
      Request.create(
          Request.HttpMethod.POST,
          "http://localhost:8082/api/v1/savings-accounts/6/fixed-deposit-fundings",
          Map.of(),
          null,
          StandardCharsets.UTF_8,
          null);

  @Mock private FixedDepositRepository fixedDepositRepository;

  @Mock private SavingsServiceClient savingsServiceClient;

  @InjectMocks private FixedDepositFundingService fixedDepositFundingService;

  private FixedDeposit deposit;

  @BeforeEach
  void setUp() {
    deposit = new FixedDeposit();
    deposit.setId(1L);
    deposit.setClientId(1L);
    deposit.setFundingAccountId(6L);
    deposit.setPrincipalAmount(new BigDecimal("10000.00"));
    deposit.setTenureInDays(90);
    deposit.setDepositDate(LocalDate.now().minusDays(2));
    deposit.setMaturityDate(LocalDate.now().plusDays(88));
    deposit.setStatus(FixedDepositStatus.PENDING_FUNDING);
    lenient().when(fixedDepositRepository.findById(1L)).thenReturn(Optional.of(deposit));
    lenient()
        .when(fixedDepositRepository.save(any(FixedDeposit.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void fund_whenSavingsTakesTheMoney_shouldActivateWithTermStartingToday() {
    FixedDeposit funded = fixedDepositFundingService.fund(1L);

    assertEquals(FixedDepositStatus.ACTIVE, funded.getStatus());
    assertEquals(LocalDate.now(), funded.getDepositDate());
    assertEquals(LocalDate.now().plusDays(90), funded.getMaturityDate());
    assertEquals(1, funded.getFundingAttempts());
    assertNull(funded.getFundingError());
    verify(savingsServiceClient).fund(deposit);
  }

  @Test
  void fund_whenSavingsRefuses_shouldMarkFailedAndSaySo() {
    byte[] body =
        "{\"success\":false,\"message\":\"Fixed deposit funding of 10000.00 exceeds the available balance of 500.00\"}"
            .getBytes(StandardCharsets.UTF_8);
    doThrow(new FeignException.BadRequest("Bad Request", REQUEST, body, Map.of()))
        .when(savingsServiceClient)
        .fund(deposit);

    FundingRefusedException e =
        assertThrows(FundingRefusedException.class, () -> fixedDepositFundingService.fund(1L));

    assertEquals(FixedDepositStatus.FUNDING_FAILED, deposit.getStatus());
    assertEquals(
        "Savings service answered 400: Fixed deposit funding of 10000.00 exceeds the available"
            + " balance of 500.00",
        deposit.getFundingError());
    assertEquals(
        "Fixed deposit 1 could not be paid for from savings account 6. "
            + deposit.getFundingError(),
        e.getMessage());
  }

  @Test
  void fund_withNoClearAnswer_shouldStayPendingForRetry() {
    doThrow(new RuntimeException("savings down")).when(savingsServiceClient).fund(deposit);

    FixedDeposit pending = fixedDepositFundingService.fund(1L);

    assertEquals(FixedDepositStatus.PENDING_FUNDING, pending.getStatus());
    assertEquals(1, pending.getFundingAttempts());
    assertEquals("savings down", pending.getFundingError());
  }

  @Test
  void fund_whenNotWaitingForFunding_shouldNotTakeMoney() {
    deposit.setStatus(FixedDepositStatus.ACTIVE);

    fixedDepositFundingService.fund(1L);

    verifyNoInteractions(savingsServiceClient);
  }

  @Test
  void fundPending_shouldRetryWaitingDepositsAndCarryOnPastRefusals() {
    FixedDeposit other = new FixedDeposit();
    other.setId(2L);
    other.setFundingAccountId(7L);
    other.setPrincipalAmount(new BigDecimal("500.00"));
    other.setTenureInDays(30);
    other.setStatus(FixedDepositStatus.PENDING_FUNDING);
    when(fixedDepositRepository.findById(2L)).thenReturn(Optional.of(other));
    when(fixedDepositRepository.findTop50ByStatusAndCreatedAtBeforeOrderByIdAsc(
            eq(FixedDepositStatus.PENDING_FUNDING), any(LocalDateTime.class)))
        .thenReturn(List.of(deposit, other));
    doThrow(new FeignException.Conflict("Conflict", REQUEST, null, Map.of()))
        .when(savingsServiceClient)
        .fund(deposit);

    fixedDepositFundingService.fundPending();

    assertEquals(FixedDepositStatus.FUNDING_FAILED, deposit.getStatus());
    assertEquals(FixedDepositStatus.ACTIVE, other.getStatus());
  }
}
