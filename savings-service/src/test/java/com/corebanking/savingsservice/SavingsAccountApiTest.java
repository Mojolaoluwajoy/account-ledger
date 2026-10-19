package com.corebanking.savingsservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.corebanking.savingsservice.entity.LedgerPosting;
import com.corebanking.savingsservice.entity.SavingsAccount;
import com.corebanking.savingsservice.entity.SavingsProduct;
import com.corebanking.savingsservice.enums.LedgerPostingStatus;
import com.corebanking.savingsservice.repository.LedgerPostingRepository;
import com.corebanking.savingsservice.repository.SavingsAccountRepository;
import com.corebanking.savingsservice.repository.SavingsProductRepository;
import com.corebanking.savingsservice.repository.SavingsTransactionRepository;
import com.corebanking.savingsservice.service.LedgerPostingService;
import com.corebanking.savingsservice.util.ClientServiceClient;
import com.corebanking.savingsservice.util.LedgerClient;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SavingsAccountApiTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private SavingsAccountRepository savingsAccountRepository;
  @Autowired private SavingsProductRepository savingsProductRepository;
  @Autowired private SavingsTransactionRepository savingsTransactionRepository;
  @Autowired private LedgerPostingRepository ledgerPostingRepository;
  @Autowired private LedgerPostingService ledgerPostingService;

  @MockitoBean private LedgerClient ledgerClient;
  @MockitoBean private ClientServiceClient clientServiceClient;

  private Long productId;

  @BeforeEach
  void setUp() {
    SavingsProduct product = new SavingsProduct();
    product.setName("API Savings");
    product.setShortName("API" + System.nanoTime());
    product.setNominalAnnualInterestRate(new BigDecimal("5.00"));
    product.setMinimumOpeningBalance(new BigDecimal("100.00"));
    product.setMinimumBalance(BigDecimal.ZERO);
    product.setEnforceMinimumBalance(false);
    productId = savingsProductRepository.save(product).getId();
  }

  @AfterEach
  void tearDown() {
    ledgerPostingRepository.deleteAll();
    savingsTransactionRepository.deleteAll();
    savingsAccountRepository.deleteAll();
    savingsProductRepository.deleteAll();
  }

  @Test
  void openAccount_shouldRecordOpeningDepositAndPostItToLedger() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/savings-accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"clientId\": 1, \"productId\": "
                        + productId
                        + ", \"initialDeposit\": 250.00}"))
        .andExpect(status().isCreated());

    SavingsAccount account = savingsAccountRepository.findAll().get(0);

    mockMvc
        .perform(get("/api/v1/savings-accounts/" + account.getId() + "/transactions"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content[0].accountId").value(account.getId()))
        .andExpect(jsonPath("$.data.content[0].transactionType").value("DEPOSIT"))
        .andExpect(jsonPath("$.data.content[0].balanceAfter").value(250.00));

    ledgerPostingService.publishPending();

    List<LedgerPosting> postings = ledgerPostingRepository.findAll();
    assertEquals(1, postings.size());
    assertEquals("SAVINGS_DEPOSIT", postings.get(0).getTransactionType());
    assertEquals(LedgerPostingStatus.POSTED, postings.get(0).getStatus());
    verify(ledgerClient, times(1)).post(any(LedgerPosting.class));
  }

  @Test
  void malformedJson_shouldReturn400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/savings-accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{not json"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false));
  }

  @Test
  void nonNumericId_shouldReturn400() throws Exception {
    mockMvc.perform(get("/api/v1/savings-accounts/abc")).andExpect(status().isBadRequest());
  }

  @Test
  void unknownUrl_shouldReturn404() throws Exception {
    mockMvc.perform(get("/api/v1/does-not-exist")).andExpect(status().isNotFound());
  }

  @Test
  void unknownAccount_shouldReturn404() throws Exception {
    mockMvc.perform(get("/api/v1/savings-accounts/999999")).andExpect(status().isNotFound());
  }
}
