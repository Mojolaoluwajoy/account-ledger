package com.corebanking.savingsservice;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.corebanking.savingsservice.dto.TransactionRequest;
import com.corebanking.savingsservice.entity.SavingsAccount;
import com.corebanking.savingsservice.entity.SavingsProduct;
import com.corebanking.savingsservice.enums.SavingsAccountStatus;
import com.corebanking.savingsservice.exception.InsufficientBalanceException;
import com.corebanking.savingsservice.repository.SavingsAccountRepository;
import com.corebanking.savingsservice.repository.SavingsProductRepository;
import com.corebanking.savingsservice.repository.SavingsTransactionRepository;
import com.corebanking.savingsservice.service.SavingsAccountService;
import com.corebanking.savingsservice.util.LedgerClient;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
    return savingsAccountRepository.findById(accountId).orElseThrow().getBalance();
  }

  private TransactionRequest amount(String value) {
    TransactionRequest request = new TransactionRequest();
    request.setAmount(new BigDecimal(value));
    return request;
  }
}
