package com.accountledger.savingsservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.accountledger.savingsservice.dto.CloseAccountRequest;
import com.accountledger.savingsservice.dto.CloseAccountResponse;
import com.accountledger.savingsservice.dto.TransactionRequest;
import com.accountledger.savingsservice.entity.SavingsAccount;
import com.accountledger.savingsservice.entity.SavingsProduct;
import com.accountledger.savingsservice.entity.SavingsTransaction;
import com.accountledger.savingsservice.enums.SavingsAccountStatus;
import com.accountledger.savingsservice.enums.TransactionType;
import com.accountledger.savingsservice.exception.AccountHasBalanceException;
import com.accountledger.savingsservice.exception.AccountNotActiveException;
import com.accountledger.savingsservice.exception.InvalidTransferException;
import com.accountledger.savingsservice.repository.InterestAccrualRepository;
import com.accountledger.savingsservice.repository.LedgerPostingRepository;
import com.accountledger.savingsservice.repository.SavingsAccountRepository;
import com.accountledger.savingsservice.repository.SavingsProductRepository;
import com.accountledger.savingsservice.repository.SavingsTransactionRepository;
import com.accountledger.savingsservice.service.InterestService;
import com.accountledger.savingsservice.service.SavingsAccountService;
import com.accountledger.savingsservice.util.LedgerClient;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class CloseAccountIntegrationTest {

  @Autowired private SavingsAccountService savingsAccountService;
  @Autowired private InterestService interestService;
  @Autowired private SavingsAccountRepository savingsAccountRepository;
  @Autowired private SavingsProductRepository savingsProductRepository;
  @Autowired private SavingsTransactionRepository savingsTransactionRepository;
  @Autowired private InterestAccrualRepository interestAccrualRepository;
  @Autowired private LedgerPostingRepository ledgerPostingRepository;

  @MockitoBean private LedgerClient ledgerClient;

  private SavingsProduct product;
  private SavingsAccount account;
  private SavingsAccount other;

  @BeforeEach
  void setUp() {
    // 3.65% a year on 10,000.00 is exactly 1.00 a day.
    product = new SavingsProduct();
    product.setName("Closure Savings");
    product.setShortName("CLS" + System.nanoTime());
    product.setNominalAnnualInterestRate(new BigDecimal("3.65"));
    product.setMinimumOpeningBalance(BigDecimal.ZERO);
    product.setMinimumBalance(new BigDecimal("50.00"));
    product.setEnforceMinimumBalance(true);
    product = savingsProductRepository.save(product);

    // Opened 5 days ago, so it has earned 5 days of interest by yesterday.
    account = newAccount("10000.00", LocalDate.now().minusDays(5).atTime(9, 0));
    other = newAccount("100.00", LocalDate.now().atStartOfDay());
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
  void close_withBalance_shouldPayInterestMoveEverythingAndClose() {
    CloseAccountResponse response =
        savingsAccountService.closeAccount(account.getId(), transferTo(other.getId()));

    // Moving the whole balance out is allowed despite the 50.00 minimum.
    assertEquals(0, new BigDecimal("5.00").compareTo(response.getInterestPaid()));
    assertEquals(0, new BigDecimal("10005.00").compareTo(response.getBalanceTransferred()));
    assertEquals(SavingsAccountStatus.CLOSED, response.getAccount().getStatus());
    assertNotNull(response.getAccount().getClosedAt());

    SavingsAccount closed = savingsAccountRepository.findById(account.getId()).orElseThrow();
    assertEquals(0, BigDecimal.ZERO.compareTo(closed.getBalance()));
    SavingsAccount receiving = savingsAccountRepository.findById(other.getId()).orElseThrow();
    assertEquals(0, new BigDecimal("10105.00").compareTo(receiving.getBalance()));

    SavingsTransaction interest =
        savingsTransactionRepository
            .findByReference("INT-" + account.getAccountNumber() + "-CLOSE")
            .orElseThrow();
    assertEquals(TransactionType.INTEREST, interest.getTransactionType());
    assertTrue(
        interestAccrualRepository
            .findByAccountIdAndPostingReferenceIsNull(account.getId())
            .isEmpty());
  }

  @Test
  void close_whileNightlyJobRecordsInterest_shouldRecordEachDayOnceAndSucceed() throws Exception {
    // Repeated with fresh accounts, since the two only collide when they overlap.
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      for (int round = 0; round < 10; round++) {
        SavingsAccount closing = newAccount("10000.00", LocalDate.now().minusDays(5).atTime(9, 0));
        CountDownLatch start = new CountDownLatch(1);

        Future<CloseAccountResponse> closure =
            executor.submit(
                () -> {
                  start.await();
                  return savingsAccountService.closeAccount(
                      closing.getId(), transferTo(other.getId()));
                });
        Future<Integer> nightly =
            executor.submit(
                () -> {
                  start.await();
                  return interestService.accrue(closing.getId(), LocalDate.now().minusDays(1));
                });
        start.countDown();

        CloseAccountResponse response = closure.get();
        nightly.get();

        assertEquals(0, new BigDecimal("5.00").compareTo(response.getInterestPaid()));
        assertEquals(
            5,
            interestAccrualRepository.findAll().stream()
                .filter(accrual -> accrual.getAccountId().equals(closing.getId()))
                .count());
      }
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void close_withBalanceButNowhereToMoveIt_shouldBeRefusedAndChangeNothing() {
    assertThrows(
        AccountHasBalanceException.class,
        () -> savingsAccountService.closeAccount(account.getId(), null));

    SavingsAccount unchanged = savingsAccountRepository.findById(account.getId()).orElseThrow();
    assertEquals(SavingsAccountStatus.ACTIVE, unchanged.getStatus());
    assertNull(unchanged.getClosedAt());
    assertEquals(0, new BigDecimal("10000.00").compareTo(unchanged.getBalance()));
    // The interest worked out along the way was rolled back too.
    assertEquals(0, interestAccrualRepository.count());
  }

  @Test
  void close_emptyAccount_shouldCloseWithoutDestination() {
    SavingsAccount empty = newAccount("0.00", LocalDate.now().atStartOfDay());

    CloseAccountResponse response = savingsAccountService.closeAccount(empty.getId(), null);

    assertEquals(SavingsAccountStatus.CLOSED, response.getAccount().getStatus());
    assertEquals(0, BigDecimal.ZERO.compareTo(response.getBalanceTransferred()));
  }

  @Test
  void closedAccount_shouldRefuseMoneyAndSecondClosure() {
    savingsAccountService.closeAccount(account.getId(), transferTo(other.getId()));

    TransactionRequest deposit = new TransactionRequest();
    deposit.setAmount(new BigDecimal("10.00"));
    assertThrows(
        AccountNotActiveException.class,
        () -> savingsAccountService.deposit(account.getId(), deposit));
    assertThrows(
        AccountNotActiveException.class,
        () -> savingsAccountService.closeAccount(account.getId(), transferTo(other.getId())));
  }

  @Test
  void close_movingBalanceIntoItself_shouldBeRefused() {
    assertThrows(
        InvalidTransferException.class,
        () -> savingsAccountService.closeAccount(account.getId(), transferTo(account.getId())));
  }

  private SavingsAccount newAccount(String balance, LocalDateTime openedAt) {
    SavingsAccount created = new SavingsAccount();
    created.setClientId(1L);
    created.setProduct(product);
    created.setAccountNumber("SAVCLS" + System.nanoTime());
    created.setBalance(new BigDecimal(balance));
    created.setAvailableBalance(new BigDecimal(balance));
    created.setStatus(SavingsAccountStatus.ACTIVE);
    created.setCreatedAt(openedAt);
    created = savingsAccountRepository.save(created);

    SavingsTransaction opening = new SavingsTransaction();
    opening.setAccount(created);
    opening.setTransactionType(TransactionType.DEPOSIT);
    opening.setAmount(new BigDecimal(balance));
    opening.setBalanceBefore(BigDecimal.ZERO);
    opening.setBalanceAfter(new BigDecimal(balance));
    opening.setTransactionDate(openedAt);
    savingsTransactionRepository.save(opening);
    return created;
  }

  private CloseAccountRequest transferTo(Long accountId) {
    CloseAccountRequest request = new CloseAccountRequest();
    request.setTransferToAccountId(accountId);
    return request;
  }
}
