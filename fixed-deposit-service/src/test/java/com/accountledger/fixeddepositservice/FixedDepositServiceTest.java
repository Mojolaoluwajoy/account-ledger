package com.accountledger.fixeddepositservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.accountledger.fixeddepositservice.dto.CreateFixedDepositRequest;
import com.accountledger.fixeddepositservice.dto.FixedDepositResponse;
import com.accountledger.fixeddepositservice.entity.FixedDeposit;
import com.accountledger.fixeddepositservice.enums.FixedDepositStatus;
import com.accountledger.fixeddepositservice.enums.PayoutStatus;
import com.accountledger.fixeddepositservice.exception.FixedDepositNotActiveException;
import com.accountledger.fixeddepositservice.exception.FixedDepositNotFoundException;
import com.accountledger.fixeddepositservice.exception.InvalidPayoutAccountException;
import com.accountledger.fixeddepositservice.exception.PrematureWithdrawalException;
import com.accountledger.fixeddepositservice.repository.FixedDepositRepository;
import com.accountledger.fixeddepositservice.service.FixedDepositService;
import com.accountledger.fixeddepositservice.service.LedgerPostingService;
import com.accountledger.fixeddepositservice.util.ClientServiceClient;
import com.accountledger.fixeddepositservice.util.SavingsServiceClient;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FixedDepositServiceTest {

  @Mock private FixedDepositRepository fixedDepositRepository;

  @Mock private LedgerPostingService ledgerPostingService;

  @Mock private ClientServiceClient clientServiceClient;

  @Mock private SavingsServiceClient savingsServiceClient;

  @InjectMocks private FixedDepositService fixedDepositService;

  private CreateFixedDepositRequest validRequest;
  private FixedDeposit savedDeposit;

  @BeforeEach
  void setUp() {
    validRequest = new CreateFixedDepositRequest();
    validRequest.setClientId(1L);
    validRequest.setPrincipalAmount(new BigDecimal("10000.00"));
    validRequest.setInterestRate(new BigDecimal("12.00"));
    validRequest.setTenureInDays(180);
    validRequest.setPayoutAccountId(5L);

    savedDeposit = new FixedDeposit();
    savedDeposit.setId(1L);
    savedDeposit.setClientId(1L);
    savedDeposit.setPrincipalAmount(new BigDecimal("10000.00"));
    savedDeposit.setInterestRate(new BigDecimal("12.00"));
    savedDeposit.setTenureInDays(180);
    savedDeposit.setMaturityDate(LocalDate.now().plusDays(180));
    savedDeposit.setMaturityAmount(new BigDecimal("10591.78"));
    savedDeposit.setStatus(FixedDepositStatus.ACTIVE);
  }

  @Test
  void createFixedDeposit_withValidRequest_shouldReturnFixedDepositResponse() {
    when(fixedDepositRepository.save(any(FixedDeposit.class))).thenReturn(savedDeposit);

    FixedDepositResponse response = fixedDepositService.createFixedDeposit(validRequest);

    assertNotNull(response);
    assertEquals(new BigDecimal("10000.00"), response.getPrincipalAmount());
    assertEquals(FixedDepositStatus.ACTIVE, response.getStatus());
    assertNotNull(response.getMaturityDate());
    assertNotNull(response.getMaturityAmount());
    verify(fixedDepositRepository, times(1)).save(any(FixedDeposit.class));
    verify(savingsServiceClient).requirePayoutAccount(5L, 1L);
    verify(ledgerPostingService)
        .record(
            eq("FIXED_DEPOSIT_CREATION"), eq(new BigDecimal("10000.00")), eq("FD-1"), anyString());
  }

  @Test
  void createFixedDeposit_withFundingAccount_shouldWaitForFundingAndLeaveLedgerToSavings() {
    validRequest.setFundingAccountId(6L);
    when(fixedDepositRepository.save(any(FixedDeposit.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    FixedDepositResponse response = fixedDepositService.createFixedDeposit(validRequest);

    assertEquals(FixedDepositStatus.PENDING_FUNDING, response.getStatus());
    assertEquals(6L, response.getFundingAccountId());
    verify(savingsServiceClient).requireFundingAccount(6L, 1L);
    verifyNoInteractions(ledgerPostingService);
  }

  @Test
  void createFixedDeposit_withInvalidPayoutAccount_shouldThrowExceptionAndSaveNothing() {
    doThrow(new InvalidPayoutAccountException("Payout account 5 does not belong to client 1"))
        .when(savingsServiceClient)
        .requirePayoutAccount(5L, 1L);

    assertThrows(
        InvalidPayoutAccountException.class,
        () -> fixedDepositService.createFixedDeposit(validRequest));

    verify(fixedDepositRepository, never()).save(any(FixedDeposit.class));
    verify(ledgerPostingService, never()).record(anyString(), any(), anyString(), anyString());
  }

  @Test
  void matureDeposit_withPayoutAccount_shouldQueuePayoutAndLeaveLedgerToSavings() {
    savedDeposit.setMaturityDate(LocalDate.now().minusDays(1));
    savedDeposit.setPayoutAccountId(5L);
    when(fixedDepositRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(savedDeposit));
    when(fixedDepositRepository.save(any(FixedDeposit.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    FixedDepositResponse response = fixedDepositService.matureDeposit(1L);

    assertEquals(FixedDepositStatus.MATURED, response.getStatus());
    assertEquals(PayoutStatus.PENDING, response.getPayoutStatus());
    verify(ledgerPostingService, never()).record(anyString(), any(), anyString(), anyString());
  }

  @Test
  void matureDeposit_beforeMaturityDate_shouldThrowException() {
    savedDeposit.setMaturityDate(LocalDate.now().plusDays(90));
    when(fixedDepositRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(savedDeposit));

    assertThrows(PrematureWithdrawalException.class, () -> fixedDepositService.matureDeposit(1L));
  }

  @Test
  void getDepositById_withInvalidId_shouldThrowException() {
    when(fixedDepositRepository.findById(99L)).thenReturn(Optional.empty());

    assertThrows(
        FixedDepositNotFoundException.class, () -> fixedDepositService.getDepositById(99L));
  }

  @Test
  void matureDeposit_withoutPayoutAccount_shouldPayOutInCash() {
    savedDeposit.setMaturityDate(LocalDate.now().minusDays(1));
    savedDeposit.setStatus(FixedDepositStatus.ACTIVE);

    FixedDeposit maturedDeposit = new FixedDeposit();
    maturedDeposit.setId(1L);
    maturedDeposit.setClientId(1L);
    maturedDeposit.setPrincipalAmount(new BigDecimal("10000.00"));
    maturedDeposit.setInterestRate(new BigDecimal("12.00"));
    maturedDeposit.setTenureInDays(180);
    maturedDeposit.setMaturityDate(LocalDate.now().minusDays(1));
    maturedDeposit.setMaturityAmount(new BigDecimal("10591.78"));
    maturedDeposit.setStatus(FixedDepositStatus.MATURED);

    when(fixedDepositRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(savedDeposit));
    when(fixedDepositRepository.save(any(FixedDeposit.class))).thenReturn(maturedDeposit);

    FixedDepositResponse response = fixedDepositService.matureDeposit(1L);

    assertNotNull(response);
    assertEquals(FixedDepositStatus.MATURED, response.getStatus());
    verify(ledgerPostingService)
        .record(
            eq("FIXED_DEPOSIT_MATURITY"),
            eq(new BigDecimal("10000.00")),
            eq("FD-MAT-1"),
            anyString());
    verify(ledgerPostingService)
        .record(
            eq("FIXED_DEPOSIT_INTEREST"),
            eq(new BigDecimal("591.78")),
            eq("FD-INT-1"),
            anyString());
  }

  @Test
  void matureDeposit_alreadyMatured_shouldThrowExceptionAndPostNothing() {
    savedDeposit.setMaturityDate(LocalDate.now().minusDays(1));
    savedDeposit.setStatus(FixedDepositStatus.MATURED);
    when(fixedDepositRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(savedDeposit));

    assertThrows(FixedDepositNotActiveException.class, () -> fixedDepositService.matureDeposit(1L));

    verify(fixedDepositRepository, never()).save(any(FixedDeposit.class));
    verify(ledgerPostingService, never()).record(anyString(), any(), anyString(), anyString());
  }

  @Test
  void withdrawEarly_withPayoutAccount_shouldPayReducedInterestIntoAccount() {
    // 12% minus the 2-point penalty = 10%, for 73 of 180 days: 10,000 x 10% x 73/365 = 200.00
    heldFor(73);
    savedDeposit.setPayoutAccountId(5L);
    when(fixedDepositRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(savedDeposit));
    when(fixedDepositRepository.save(any(FixedDeposit.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    FixedDepositResponse response = fixedDepositService.withdrawEarly(1L);

    assertEquals(FixedDepositStatus.CLOSED_EARLY, response.getStatus());
    assertEquals(LocalDate.now(), response.getClosedOn());
    assertEquals(new BigDecimal("200.00"), response.getPayoutInterest());
    assertEquals(PayoutStatus.PENDING, response.getPayoutStatus());
    verify(ledgerPostingService, never()).record(anyString(), any(), anyString(), anyString());
  }

  @Test
  void withdrawEarly_withRateBelowPenalty_shouldPayNoInterest() {
    heldFor(73);
    savedDeposit.setInterestRate(new BigDecimal("1.50"));
    savedDeposit.setPayoutAccountId(5L);
    when(fixedDepositRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(savedDeposit));
    when(fixedDepositRepository.save(any(FixedDeposit.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    FixedDepositResponse response = fixedDepositService.withdrawEarly(1L);

    assertEquals(0, BigDecimal.ZERO.compareTo(response.getPayoutInterest()));
  }

  @Test
  void withdrawEarly_withoutPayoutAccount_shouldPayOutInCash() {
    heldFor(73);
    when(fixedDepositRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(savedDeposit));
    when(fixedDepositRepository.save(any(FixedDeposit.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    fixedDepositService.withdrawEarly(1L);

    verify(ledgerPostingService)
        .record(
            eq("FIXED_DEPOSIT_MATURITY"),
            eq(new BigDecimal("10000.00")),
            eq("FD-MAT-1"),
            anyString());
    verify(ledgerPostingService)
        .record(
            eq("FIXED_DEPOSIT_INTEREST"),
            eq(new BigDecimal("200.00")),
            eq("FD-INT-1"),
            anyString());
  }

  @Test
  void withdrawEarly_onMaturityDate_shouldBeRefused() {
    savedDeposit.setDepositDate(LocalDate.now().minusDays(180));
    savedDeposit.setMaturityDate(LocalDate.now());
    when(fixedDepositRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(savedDeposit));

    assertThrows(FixedDepositNotActiveException.class, () -> fixedDepositService.withdrawEarly(1L));

    verify(fixedDepositRepository, never()).save(any(FixedDeposit.class));
  }

  @Test
  void withdrawEarly_alreadyEnded_shouldBeRefused() {
    heldFor(73);
    savedDeposit.setStatus(FixedDepositStatus.MATURED);
    when(fixedDepositRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(savedDeposit));

    assertThrows(FixedDepositNotActiveException.class, () -> fixedDepositService.withdrawEarly(1L));
  }

  private void heldFor(int days) {
    savedDeposit.setDepositDate(LocalDate.now().minusDays(days));
    savedDeposit.setMaturityDate(LocalDate.now().minusDays(days).plusDays(180));
  }
}
