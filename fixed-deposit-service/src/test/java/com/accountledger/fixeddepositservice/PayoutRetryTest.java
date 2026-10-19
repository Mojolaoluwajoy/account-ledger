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
import com.accountledger.fixeddepositservice.enums.PayoutStatus;
import com.accountledger.fixeddepositservice.exception.InvalidPayoutAccountException;
import com.accountledger.fixeddepositservice.exception.NotRetryableException;
import com.accountledger.fixeddepositservice.repository.FixedDepositRepository;
import com.accountledger.fixeddepositservice.service.FixedDepositPayoutService;
import com.accountledger.fixeddepositservice.util.SavingsServiceClient;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PayoutRetryTest {

  @Mock private FixedDepositRepository fixedDepositRepository;

  @Mock private SavingsServiceClient savingsServiceClient;

  @InjectMocks private FixedDepositPayoutService fixedDepositPayoutService;

  private FixedDeposit deposit;

  @BeforeEach
  void setUp() {
    deposit = new FixedDeposit();
    deposit.setId(1L);
    deposit.setClientId(1L);
    deposit.setPayoutAccountId(5L);
    deposit.setPayoutStatus(PayoutStatus.FAILED);
    when(fixedDepositRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(deposit));
  }

  @Test
  void retry_failedPayout_shouldQueueItAgainIntoSameAccount() {
    when(fixedDepositRepository.save(deposit)).thenReturn(deposit);

    FixedDeposit retried = fixedDepositPayoutService.retry(1L, null);

    assertEquals(PayoutStatus.PENDING, retried.getPayoutStatus());
    assertEquals(5L, retried.getPayoutAccountId());
    verifyNoInteractions(savingsServiceClient);
  }

  @Test
  void retry_withNewPayoutAccount_shouldCheckAndSwitchAccount() {
    when(fixedDepositRepository.save(deposit)).thenReturn(deposit);

    FixedDeposit retried = fixedDepositPayoutService.retry(1L, 6L);

    assertEquals(PayoutStatus.PENDING, retried.getPayoutStatus());
    assertEquals(6L, retried.getPayoutAccountId());
    verify(savingsServiceClient).requirePayoutAccount(6L, 1L);
  }

  @Test
  void retry_withInvalidNewPayoutAccount_shouldLeavePayoutFailed() {
    doThrow(new InvalidPayoutAccountException("Payout account 6 does not belong to client 1"))
        .when(savingsServiceClient)
        .requirePayoutAccount(6L, 1L);

    assertThrows(
        InvalidPayoutAccountException.class, () -> fixedDepositPayoutService.retry(1L, 6L));

    assertEquals(PayoutStatus.FAILED, deposit.getPayoutStatus());
    verify(fixedDepositRepository, never()).save(any());
  }

  @Test
  void retry_payoutThatIsNotFailed_shouldThrowNotRetryable() {
    deposit.setPayoutStatus(PayoutStatus.PAID);

    assertThrows(NotRetryableException.class, () -> fixedDepositPayoutService.retry(1L, null));

    verify(fixedDepositRepository, never()).save(any());
  }
}
