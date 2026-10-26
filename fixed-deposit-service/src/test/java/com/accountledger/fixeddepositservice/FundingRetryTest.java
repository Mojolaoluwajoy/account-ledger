package com.accountledger.fixeddepositservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.accountledger.fixeddepositservice.entity.FixedDeposit;
import com.accountledger.fixeddepositservice.enums.FixedDepositStatus;
import com.accountledger.fixeddepositservice.exception.InvalidFundingAccountException;
import com.accountledger.fixeddepositservice.exception.NotRetryableException;
import com.accountledger.fixeddepositservice.repository.FixedDepositRepository;
import com.accountledger.fixeddepositservice.service.FixedDepositFundingService;
import com.accountledger.fixeddepositservice.util.SavingsServiceClient;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FundingRetryTest {

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
    deposit.setStatus(FixedDepositStatus.FUNDING_FAILED);
    when(fixedDepositRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(deposit));
  }

  @Test
  void requeue_refusedFunding_shouldPutItBackFromSameAccount() {
    when(fixedDepositRepository.save(deposit)).thenReturn(deposit);

    FixedDeposit requeued = fixedDepositFundingService.requeue(1L, null);

    assertEquals(FixedDepositStatus.PENDING_FUNDING, requeued.getStatus());
    assertEquals(6L, requeued.getFundingAccountId());
    verifyNoInteractions(savingsServiceClient);
  }

  @Test
  void requeue_withNewFundingAccount_shouldCheckAndSwitchAccount() {
    when(fixedDepositRepository.save(deposit)).thenReturn(deposit);

    FixedDeposit requeued = fixedDepositFundingService.requeue(1L, 7L);

    assertEquals(FixedDepositStatus.PENDING_FUNDING, requeued.getStatus());
    assertEquals(7L, requeued.getFundingAccountId());
    verify(savingsServiceClient).requireFundingAccount(7L, 1L);
  }

  @Test
  void requeue_withInvalidNewFundingAccount_shouldLeaveFundingFailed() {
    doThrow(new InvalidFundingAccountException("Funding account 7 does not belong to client 1"))
        .when(savingsServiceClient)
        .requireFundingAccount(7L, 1L);

    assertThrows(
        InvalidFundingAccountException.class, () -> fixedDepositFundingService.requeue(1L, 7L));

    assertEquals(FixedDepositStatus.FUNDING_FAILED, deposit.getStatus());
    assertEquals(6L, deposit.getFundingAccountId());
    verify(fixedDepositRepository, never()).save(any());
  }

  @Test
  void requeue_pendingFunding_shouldBeRefusedSoTheAccountCannotChange() {
    // Its money may already have been taken from the current account.
    deposit.setStatus(FixedDepositStatus.PENDING_FUNDING);

    assertThrows(NotRetryableException.class, () -> fixedDepositFundingService.requeue(1L, 7L));

    assertEquals(6L, deposit.getFundingAccountId());
    verifyNoInteractions(savingsServiceClient);
    verify(fixedDepositRepository, never()).save(any());
  }

  @Test
  void requeue_activeDeposit_shouldBeRefused() {
    deposit.setStatus(FixedDepositStatus.ACTIVE);

    assertThrows(NotRetryableException.class, () -> fixedDepositFundingService.requeue(1L, null));

    verify(fixedDepositRepository, never()).save(any());
  }
}
