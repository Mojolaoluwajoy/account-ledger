package com.accountledger.savingsservice;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.accountledger.savingsservice.dto.TransactionRequest;
import com.accountledger.savingsservice.dto.TransferRequest;
import com.accountledger.savingsservice.entity.SavingsAccount;
import com.accountledger.savingsservice.entity.SavingsProduct;
import com.accountledger.savingsservice.enums.SavingsAccountStatus;
import com.accountledger.savingsservice.exception.InsufficientBalanceException;
import com.accountledger.savingsservice.repository.SavingsAccountRepository;
import com.accountledger.savingsservice.repository.SavingsProductRepository;
import com.accountledger.savingsservice.repository.SavingsTransactionRepository;
import com.accountledger.savingsservice.service.SavingsAccountService;
import com.accountledger.savingsservice.util.LedgerClient;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class SavingsAccountConcurrencyTest {

  private static final int THREADS = 8;

  @Autowired private SavingsAccountService savingsAccountService;
  @Autowired private SavingsAccountRepository savingsAccountRepository;
  @Autowired private SavingsProductRepository savingsProductRepository;
  @Autowired private SavingsTransactionRepository savingsTransactionRepository;

  @MockitoBean private LedgerClient ledgerClient;

  private Long accountId;
  private Long otherAccountId;

  @BeforeEach
  void setUp() {
    SavingsProduct product = new SavingsProduct();
    product.setName("Concurrency Savings");
    product.setShortName("CONC" + System.nanoTime());
    product.setNominalAnnualInterestRate(new BigDecimal("5.00"));
    product.setMinimumOpeningBalance(BigDecimal.ZERO);
    product.setMinimumBalance(BigDecimal.ZERO);
    product.setEnforceMinimumBalance(false);
    product = savingsProductRepository.save(product);

    SavingsAccount account = new SavingsAccount();
    account.setClientId(1L);
    account.setProduct(product);
    account.setAccountNumber("SAVCONC" + System.nanoTime());
    account.setBalance(new BigDecimal("100.00"));
    account.setAvailableBalance(new BigDecimal("100.00"));
    account.setStatus(SavingsAccountStatus.ACTIVE);
    accountId = savingsAccountRepository.save(account).getId();

    SavingsAccount other = new SavingsAccount();
    other.setClientId(2L);
    other.setProduct(product);
    other.setAccountNumber("SAVCONC" + System.nanoTime());
    other.setBalance(new BigDecimal("100.00"));
    other.setAvailableBalance(new BigDecimal("100.00"));
    other.setStatus(SavingsAccountStatus.ACTIVE);
    otherAccountId = savingsAccountRepository.save(other).getId();
  }

  @AfterEach
  void tearDown() {
    savingsTransactionRepository.deleteAll();
    savingsAccountRepository.deleteAll();
    savingsProductRepository.deleteAll();
  }

  @Test
  void concurrentDeposits_shouldAllBeApplied() throws Exception {
    runConcurrently(() -> savingsAccountService.deposit(accountId, amount("10.00")));

    assertEquals(0, new BigDecimal("180.00").compareTo(currentBalance()));
  }

  @Test
  void concurrentWithdrawals_shouldNeverOverdrawAccount() throws Exception {
    int rejected =
        runConcurrently(() -> savingsAccountService.withdraw(accountId, amount("30.00")));

    // 100.00 covers exactly three withdrawals of 30.00; the other five must be rejected.
    assertEquals(THREADS - 3, rejected);
    assertEquals(0, new BigDecimal("10.00").compareTo(currentBalance()));
  }

  @Test
  void concurrentTransfersInBothDirections_shouldNotDeadlockOrLoseMoney() throws Exception {
    AtomicInteger calls = new AtomicInteger();

    // Half the transfers go one way and half the other, all at once.
    int rejected =
        runConcurrently(
            () ->
                calls.getAndIncrement() % 2 == 0
                    ? savingsAccountService.transfer(accountId, transferTo(otherAccountId, "10.00"))
                    : savingsAccountService.transfer(
                        otherAccountId, transferTo(accountId, "10.00")));

    assertEquals(0, rejected);
    assertEquals(0, new BigDecimal("100.00").compareTo(currentBalance()));
    assertEquals(0, new BigDecimal("100.00").compareTo(balanceOf(otherAccountId)));
    assertEquals(THREADS * 2L, savingsTransactionRepository.count());
  }

  /* Starts all calls at the same moment and returns how many were rejected for low balance. */
  private int runConcurrently(Callable<?> call) throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(THREADS);
    CountDownLatch start = new CountDownLatch(1);
    try {
      List<Future<Boolean>> results = new ArrayList<>();
      for (int i = 0; i < THREADS; i++) {
        results.add(
            executor.submit(
                () -> {
                  start.await();
                  try {
                    call.call();
                    return false;
                  } catch (InsufficientBalanceException e) {
                    return true;
                  }
                }));
      }
      start.countDown();

      int rejected = 0;
      for (Future<Boolean> result : results) {
        if (result.get(30, TimeUnit.SECONDS)) {
          rejected++;
        }
      }
      return rejected;
    } finally {
      executor.shutdownNow();
    }
  }

  private BigDecimal currentBalance() {
    return balanceOf(accountId);
  }

  private BigDecimal balanceOf(Long id) {
    return savingsAccountRepository.findById(id).orElseThrow().getBalance();
  }

  private TransferRequest transferTo(Long destinationAccountId, String value) {
    TransferRequest request = new TransferRequest();
    request.setDestinationAccountId(destinationAccountId);
    request.setAmount(new BigDecimal(value));
    return request;
  }

  private TransactionRequest amount(String value) {
    TransactionRequest request = new TransactionRequest();
    request.setAmount(new BigDecimal(value));
    return request;
  }
}
