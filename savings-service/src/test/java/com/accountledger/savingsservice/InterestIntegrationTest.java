package com.accountledger.savingsservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.accountledger.savingsservice.dto.InterestRunResponse;
import com.accountledger.savingsservice.entity.InterestAccrual;
import com.accountledger.savingsservice.entity.LedgerPosting;
import com.accountledger.savingsservice.entity.SavingsAccount;
import com.accountledger.savingsservice.entity.SavingsProduct;
import com.accountledger.savingsservice.entity.SavingsTransaction;
import com.accountledger.savingsservice.enums.SavingsAccountStatus;
import com.accountledger.savingsservice.enums.TransactionType;
import com.accountledger.savingsservice.repository.InterestAccrualRepository;
import com.accountledger.savingsservice.repository.LedgerPostingRepository;
import com.accountledger.savingsservice.repository.SavingsAccountRepository;
import com.accountledger.savingsservice.repository.SavingsProductRepository;
import com.accountledger.savingsservice.repository.SavingsTransactionRepository;
import com.accountledger.savingsservice.service.InterestJob;
import com.accountledger.savingsservice.util.LedgerClient;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class InterestIntegrationTest {

  @Autowired private InterestJob interestJob;
  @Autowired private SavingsAccountRepository savingsAccountRepository;
  @Autowired private SavingsProductRepository savingsProductRepository;
  @Autowired private SavingsTransactionRepository savingsTransactionRepository;
  @Autowired private InterestAccrualRepository interestAccrualRepository;
  @Autowired private LedgerPostingRepository ledgerPostingRepository;

  @MockitoBean private LedgerClient ledgerClient;

  private SavingsProduct product;
  private SavingsAccount account;

  @BeforeEach
  void setUp() {
    // 3.65% a year on 10,000.00 is exactly 1.000000 a day.
    product = new SavingsProduct();
    product.setName("Interest Savings");
    product.setShortName("INT" + System.nanoTime());
    product.setNominalAnnualInterestRate(new BigDecimal("3.65"));
    product.setMinimumOpeningBalance(BigDecimal.ZERO);
    product.setMinimumBalance(new BigDecimal("5000.00"));
    product.setEnforceMinimumBalance(false);
    product = savingsProductRepository.save(product);

    account = new SavingsAccount();
    account.setClientId(1L);
    account.setProduct(product);
    account.setAccountNumber("SAVINT" + System.nanoTime());
    account.setBalance(new BigDecimal("10000.00"));
    account.setAvailableBalance(new BigDecimal("10000.00"));
    account.setStatus(SavingsAccountStatus.ACTIVE);
    account.setCreatedAt(LocalDateTime.of(2026, 9, 28, 9, 0));
    account = savingsAccountRepository.save(account);

    addTransaction(LocalDateTime.of(2026, 9, 28, 9, 0), "0.00", "10000.00");
  }

  @AfterEach
  void tearDown() {
    interestAccrualRepository.deleteAll();
    ledgerPostingRepository.deleteAll();
    savingsTransactionRepository.deleteAll();
    savingsAccountRepository.deleteAll();
    savingsProductRepository.deleteAll();
  }

  @Test
  void run_shouldAccrueEachDayAndPayEndedMonthsOnce() {
    // Dips below the 5,000.00 minimum for one day (29 Sep), then recovers.
    addTransaction(LocalDateTime.of(2026, 9, 29, 12, 0), "10000.00", "4000.00");
    addTransaction(LocalDateTime.of(2026, 9, 30, 8, 0), "4000.00", "10000.00");

    InterestRunResponse first = interestJob.run(LocalDate.of(2026, 10, 3));

    // 28 Sep to 2 Oct: five days, the 29th earning nothing.
    assertEquals(5, first.getDaysAccrued());
    List<InterestAccrual> accruals = accrualsOfAccount();
    assertEquals(LocalDate.of(2026, 9, 28), accruals.get(0).getAccrualDate());
    assertEquals(0, new BigDecimal("1.000000").compareTo(accruals.get(0).getAmount()));
    assertEquals(0, BigDecimal.ZERO.compareTo(accruals.get(1).getAmount()));

    // September (28th and 30th) is paid; October's days wait for November.
    assertEquals(1, first.getAccountsPaid());
    assertEquals(0, new BigDecimal("2.00").compareTo(first.getTotalPaid()));
    SavingsAccount paid = savingsAccountRepository.findById(account.getId()).orElseThrow();
    assertEquals(0, new BigDecimal("10002.00").compareTo(paid.getBalance()));

    String reference = "INT-" + account.getAccountNumber() + "-2026-09";
    SavingsTransaction interest =
        savingsTransactionRepository.findByReference(reference).orElseThrow();
    assertEquals(TransactionType.INTEREST, interest.getTransactionType());
    LedgerPosting posting =
        ledgerPostingRepository.findAll().stream()
            .filter(p -> p.getTransactionReference().equals(reference))
            .findFirst()
            .orElseThrow();
    assertEquals("SAVINGS_INTEREST", posting.getTransactionType());
    assertTrue(
        accruals.stream()
            .filter(a -> a.getAccrualDate().isBefore(LocalDate.of(2026, 10, 1)))
            .allMatch(a -> reference.equals(a.getPostingReference())));

    // Running again for the same day changes nothing.
    InterestRunResponse second = interestJob.run(LocalDate.of(2026, 10, 3));
    assertEquals(0, second.getDaysAccrued());
    assertEquals(0, second.getAccountsPaid());
    assertEquals(
        0,
        new BigDecimal("10002.00")
            .compareTo(
                savingsAccountRepository.findById(account.getId()).orElseThrow().getBalance()));
  }

  @Test
  void run_afterMissedDays_shouldCatchUp() {
    interestJob.run(LocalDate.of(2026, 9, 30));
    assertEquals(2, accrualsOfAccount().size()); // 28th and 29th

    interestJob.run(LocalDate.of(2026, 10, 5));
    assertEquals(7, accrualsOfAccount().size()); // up to 4 Oct
  }

  @Test
  void run_whenMonthsInterestRoundsToZero_shouldCarryItIntoNextMonth() {
    product.setMinimumBalance(BigDecimal.ZERO);
    savingsProductRepository.save(product);
    // 1.00 at 3.65% earns 0.0001 a day: three September days round to 0.00.
    addTransaction(LocalDateTime.of(2026, 9, 28, 10, 0), "10000.00", "1.00");

    InterestRunResponse run = interestJob.run(LocalDate.of(2026, 10, 2));

    assertEquals(0, run.getAccountsPaid());
    assertTrue(accrualsOfAccount().stream().allMatch(a -> a.getPostingReference() == null));
  }

  private List<InterestAccrual> accrualsOfAccount() {
    return interestAccrualRepository.findAll().stream()
        .filter(a -> a.getAccountId().equals(account.getId()))
        .sorted(Comparator.comparing(InterestAccrual::getAccrualDate))
        .toList();
  }

  private void addTransaction(LocalDateTime at, String before, String after) {
    SavingsTransaction transaction = new SavingsTransaction();
    transaction.setAccount(account);
    transaction.setTransactionType(TransactionType.DEPOSIT);
    transaction.setAmount(new BigDecimal(after).subtract(new BigDecimal(before)).abs());
    transaction.setBalanceBefore(new BigDecimal(before));
    transaction.setBalanceAfter(new BigDecimal(after));
    transaction.setTransactionDate(at);
    savingsTransactionRepository.save(transaction);
  }
}
