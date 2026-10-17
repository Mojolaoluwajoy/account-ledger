package com.corebanking.fixeddepositservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.corebanking.fixeddepositservice.entity.FixedDeposit;
import com.corebanking.fixeddepositservice.enums.FixedDepositStatus;
import com.corebanking.fixeddepositservice.enums.PayoutStatus;
import com.corebanking.fixeddepositservice.repository.FixedDepositRepository;
import com.corebanking.fixeddepositservice.service.FixedDepositPayoutService;
import com.corebanking.fixeddepositservice.util.SavingsServiceClient;
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
class FixedDepositPayoutServiceTest {

  private static final Request REQUEST =
      Request.create(
          Request.HttpMethod.POST,
          "http://localhost:8082/api/v1/savings-accounts/5/fixed-deposit-payouts",
          Map.of(),
          null,
          StandardCharsets.UTF_8,
          null);

  @Mock private FixedDepositRepository fixedDepositRepository;

  @Mock private SavingsServiceClient savingsServiceClient;

  @InjectMocks private FixedDepositPayoutService fixedDepositPayoutService;

  private FixedDeposit deposit;

  @BeforeEach
  void setUp() {
    deposit = new FixedDeposit();
    deposit.setId(1L);
    deposit.setClientId(1L);
    deposit.setPrincipalAmount(new BigDecimal("10000.00"));
    deposit.setMaturityAmount(new BigDecimal("10246.58"));
    deposit.setStatus(FixedDepositStatus.MATURED);
    deposit.setPayoutAccountId(5L);
    deposit.setPayoutStatus(PayoutStatus.PENDING);

    when(fixedDepositRepository.findTop50ByPayoutStatusOrderByIdAsc(PayoutStatus.PENDING))
        .thenReturn(List.of(deposit));
  }

  @Test
  void payOutPending_whenSavingsAccepts_shouldMarkPaid() {
    fixedDepositPayoutService.payOutPending();

    assertEquals(PayoutStatus.PAID, deposit.getPayoutStatus());
    assertNotNull(deposit.getPaidOutAt());
    verify(savingsServiceClient).payOut(deposit);
    verify(fixedDepositRepository).save(deposit);
  }

  @Test
  void payOutPending_whenSavingsFails_shouldStayPendingForRetry() {
    doThrow(new RuntimeException("savings down")).when(savingsServiceClient).payOut(deposit);

    fixedDepositPayoutService.payOutPending();

    assertEquals(PayoutStatus.PENDING, deposit.getPayoutStatus());
    assertEquals(1, deposit.getPayoutAttempts());
    assertEquals("savings down", deposit.getPayoutError());
    verify(fixedDepositRepository).save(deposit);
  }

  @Test
  void payOutPending_whenSavingsRejects_shouldMarkFailedAndNotRetry() {
    byte[] body =
        "{\"success\":false,\"message\":\"Account SAV1 is not active. Current status: CLOSED\"}"
            .getBytes(StandardCharsets.UTF_8);
    doThrow(new FeignException.Conflict("Conflict", REQUEST, body, Map.of()))
        .when(savingsServiceClient)
        .payOut(deposit);

    fixedDepositPayoutService.payOutPending();

    assertEquals(PayoutStatus.FAILED, deposit.getPayoutStatus());
    assertEquals(
        "Savings service answered 409: Account SAV1 is not active. Current status: CLOSED",
        deposit.getPayoutError());
    verify(fixedDepositRepository).save(deposit);
  }
}
