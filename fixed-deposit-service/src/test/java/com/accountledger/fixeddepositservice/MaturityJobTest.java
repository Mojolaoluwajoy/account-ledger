package com.accountledger.fixeddepositservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.accountledger.fixeddepositservice.entity.FixedDeposit;
import com.accountledger.fixeddepositservice.enums.FixedDepositStatus;
import com.accountledger.fixeddepositservice.exception.FixedDepositNotActiveException;
import com.accountledger.fixeddepositservice.repository.FixedDepositRepository;
import com.accountledger.fixeddepositservice.service.FixedDepositService;
import com.accountledger.fixeddepositservice.service.MaturityJob;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MaturityJobTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 11, 1);

  @Mock private FixedDepositService fixedDepositService;

  @Mock private FixedDepositRepository fixedDepositRepository;

  @InjectMocks private MaturityJob maturityJob;

  @Test
  void run_shouldMatureEveryDueDepositAndCarryOnPastFailures() {
    when(fixedDepositRepository.findByStatusAndMaturityDateLessThanEqual(
            FixedDepositStatus.ACTIVE, TODAY))
        .thenReturn(List.of(deposit(1L), deposit(2L), deposit(3L)));
    when(fixedDepositService.matureDeposit(1L)).thenThrow(new RuntimeException("database down"));
    when(fixedDepositService.matureDeposit(2L))
        .thenThrow(new FixedDepositNotActiveException("already matured"));

    int matured = maturityJob.run(TODAY);

    assertEquals(1, matured);
    verify(fixedDepositService).matureDeposit(3L);
  }

  @Test
  void run_withNothingDue_shouldMatureNothing() {
    when(fixedDepositRepository.findByStatusAndMaturityDateLessThanEqual(
            FixedDepositStatus.ACTIVE, TODAY))
        .thenReturn(List.of());

    assertEquals(0, maturityJob.run(TODAY));
  }

  private FixedDeposit deposit(Long id) {
    FixedDeposit deposit = new FixedDeposit();
    deposit.setId(id);
    return deposit;
  }
}
