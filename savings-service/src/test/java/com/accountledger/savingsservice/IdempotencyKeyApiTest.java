package com.accountledger.savingsservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.accountledger.savingsservice.dto.TransactionRequest;
import com.accountledger.savingsservice.entity.SavingsAccount;
import com.accountledger.savingsservice.entity.SavingsProduct;
import com.accountledger.savingsservice.enums.SavingsAccountStatus;
import com.accountledger.savingsservice.repository.LedgerPostingRepository;
import com.accountledger.savingsservice.repository.SavingsAccountRepository;
import com.accountledger.savingsservice.repository.SavingsProductRepository;
import com.accountledger.savingsservice.repository.SavingsTransactionRepository;
import com.accountledger.savingsservice.service.SavingsAccountService;
import com.accountledger.savingsservice.util.ClientServiceClient;
import com.accountledger.savingsservice.util.LedgerClient;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class IdempotencyKeyApiTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private SavingsAccountService savingsAccountService;
  @Autowired private SavingsAccountRepository savingsAccountRepository;
  @Autowired private SavingsProductRepository savingsProductRepository;
  @Autowired private SavingsTransactionRepository savingsTransactionRepository;
  @Autowired private LedgerPostingRepository ledgerPostingRepository;

  @MockitoBean private LedgerClient ledgerClient;
  @MockitoBean private ClientServiceClient clientServiceClient;

  private Long accountId;
  private Long otherAccountId;

  @BeforeEach
  void setUp() {
    SavingsProduct product = new SavingsProduct();
    product.setName("Idempotency Savings");
    product.setShortName("IDEM" + System.nanoTime());
    product.setNominalAnnualInterestRate(new BigDecimal("5.00"));
    product.setMinimumOpeningBalance(BigDecimal.ZERO);
    product.setMinimumBalance(BigDecimal.ZERO);
    product.setEnforceMinimumBalance(false);
    product = savingsProductRepository.save(product);

    accountId = account(product).getId();
    otherAccountId = account(product).getId();
  }

  @AfterEach
  void tearDown() {
    ledgerPostingRepository.deleteAll();
    savingsTransactionRepository.deleteAll();
    savingsAccountRepository.deleteAll();
    savingsProductRepository.deleteAll();
  }

  @Test
  void depositSentTwiceWithSameKey_shouldBeAppliedOnceAndAnswerTheSame() throws Exception {
    send("deposit", accountId, "key-1", "{\"amount\": 50.00}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.newBalance").value(150.00));
    send("deposit", accountId, "key-1", "{\"amount\": 50.00}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.balanceBefore").value(100.00))
        .andExpect(jsonPath("$.data.newBalance").value(150.00));

    assertBalance(accountId, "150.00");
    assertEquals(1, savingsTransactionRepository.count());
    assertEquals(1, ledgerPostingRepository.count());
  }

  @Test
  void withdrawalSentTwiceWithSameKey_shouldBeAppliedOnce() throws Exception {
    send("withdraw", accountId, "key-1", "{\"amount\": 30.00}").andExpect(status().isOk());
    send("withdraw", accountId, "key-1", "{\"amount\": 30.00}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.newBalance").value(70.00));

    assertBalance(accountId, "70.00");
    assertEquals(1, savingsTransactionRepository.count());
  }

  @Test
  void transferSentTwiceWithSameKey_shouldBeAppliedOnce() throws Exception {
    String transfer = "{\"destinationAccountId\": " + otherAccountId + ", \"amount\": 40.00}";
    send("transfer", accountId, "key-1", transfer).andExpect(status().isOk());
    send("transfer", accountId, "key-1", transfer)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.destinationAccountId").value(otherAccountId))
        .andExpect(jsonPath("$.data.newBalance").value(60.00));

    assertBalance(accountId, "60.00");
    assertBalance(otherAccountId, "140.00");
    assertEquals(2, savingsTransactionRepository.count());
  }

  @Test
  void sameKeyWithDifferentAmount_shouldBeRefusedWith409() throws Exception {
    send("deposit", accountId, "key-1", "{\"amount\": 50.00}").andExpect(status().isOk());
    send("deposit", accountId, "key-1", "{\"amount\": 60.00}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.success").value(false));

    assertBalance(accountId, "150.00");
  }

  @Test
  void sameKeyForADifferentKindOfRequest_shouldBeRefusedWith409() throws Exception {
    send("deposit", accountId, "key-1", "{\"amount\": 50.00}").andExpect(status().isOk());
    send("withdraw", accountId, "key-1", "{\"amount\": 50.00}").andExpect(status().isConflict());

    assertBalance(accountId, "150.00");
  }

  @Test
  void sameKeyForTransferToADifferentAccount_shouldBeRefusedWith409() throws Exception {
    Long thirdAccountId = account(savingsProductRepository.findAll().get(0)).getId();
    send(
            "transfer",
            accountId,
            "key-1",
            "{\"destinationAccountId\": " + otherAccountId + ", \"amount\": 40.00}")
        .andExpect(status().isOk());
    send(
            "transfer",
            accountId,
            "key-1",
            "{\"destinationAccountId\": " + thirdAccountId + ", \"amount\": 40.00}")
        .andExpect(status().isConflict());

    assertBalance(thirdAccountId, "100.00");
  }

  @Test
  void depositKeyEndingLikeATransferKey_shouldNotClashWithTheTransfer() throws Exception {
    send("deposit", accountId, "key-1-IN", "{\"amount\": 50.00}").andExpect(status().isOk());
    send(
            "transfer",
            accountId,
            "key-1",
            "{\"destinationAccountId\": " + otherAccountId + ", \"amount\": 40.00}")
        .andExpect(status().isOk());

    assertBalance(accountId, "110.00");
    assertBalance(otherAccountId, "140.00");
  }

  @Test
  void sameKeyOnTwoAccounts_shouldApplyToEach() throws Exception {
    send("deposit", accountId, "key-1", "{\"amount\": 50.00}").andExpect(status().isOk());
    send("deposit", otherAccountId, "key-1", "{\"amount\": 50.00}").andExpect(status().isOk());

    assertBalance(accountId, "150.00");
    assertBalance(otherAccountId, "150.00");
  }

  @Test
  void withoutKey_shouldApplyEveryRequestAsBefore() throws Exception {
    send("deposit", accountId, null, "{\"amount\": 50.00}").andExpect(status().isOk());
    send("deposit", accountId, null, "{\"amount\": 50.00}").andExpect(status().isOk());

    assertBalance(accountId, "200.00");
  }

  @Test
  void invalidKey_shouldBeRefusedWith400() throws Exception {
    send("deposit", accountId, "has space", "{\"amount\": 50.00}")
        .andExpect(status().isBadRequest());
    send("deposit", accountId, "k".repeat(101), "{\"amount\": 50.00}")
        .andExpect(status().isBadRequest());

    assertBalance(accountId, "100.00");
  }

  @Test
  void sameKeySentConcurrently_shouldBeAppliedOnce() throws Exception {
    int threads = 8;
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    CountDownLatch start = new CountDownLatch(1);
    TransactionRequest request = new TransactionRequest();
    request.setAmount(new BigDecimal("50.00"));
    List<Future<?>> results = new ArrayList<>();
    for (int i = 0; i < threads; i++) {
      results.add(
          pool.submit(
              () -> {
                start.await();
                return savingsAccountService.deposit(accountId, request, "key-1");
              }));
    }
    start.countDown();
    for (Future<?> result : results) {
      result.get(30, TimeUnit.SECONDS);
    }
    pool.shutdown();

    assertBalance(accountId, "150.00");
    assertEquals(1, savingsTransactionRepository.count());
  }

  private ResultActions send(String operation, Long account, String key, String body)
      throws Exception {
    var request =
        post("/api/v1/savings-accounts/" + account + "/" + operation)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body);
    if (key != null) {
      request.header("Idempotency-Key", key);
    }
    return mockMvc.perform(request);
  }

  private void assertBalance(Long id, String expected) {
    assertEquals(
        0,
        new BigDecimal(expected)
            .compareTo(savingsAccountRepository.findById(id).get().getBalance()),
        "balance of account " + id);
  }

  private SavingsAccount account(SavingsProduct product) {
    SavingsAccount account = new SavingsAccount();
    account.setClientId(1L);
    account.setProduct(product);
    account.setAccountNumber("SAVIDEM" + System.nanoTime());
    account.setBalance(new BigDecimal("100.00"));
    account.setAvailableBalance(new BigDecimal("100.00"));
    account.setStatus(SavingsAccountStatus.ACTIVE);
    return savingsAccountRepository.save(account);
  }
}
