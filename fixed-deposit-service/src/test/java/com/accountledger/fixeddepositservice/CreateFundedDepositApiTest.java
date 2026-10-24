package com.accountledger.fixeddepositservice;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.accountledger.fixeddepositservice.dto.CreateFixedDepositRequest;
import com.accountledger.fixeddepositservice.dto.FixedDepositResponse;
import com.accountledger.fixeddepositservice.entity.FixedDeposit;
import com.accountledger.fixeddepositservice.enums.FixedDepositStatus;
import com.accountledger.fixeddepositservice.exception.FundingRefusedException;
import com.accountledger.fixeddepositservice.service.FixedDepositFundingService;
import com.accountledger.fixeddepositservice.service.FixedDepositService;
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
class CreateFundedDepositApiTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private FixedDepositService fixedDepositService;
  @MockitoBean private FixedDepositFundingService fixedDepositFundingService;

  @BeforeEach
  void setUp() {
    when(fixedDepositService.createFixedDeposit(any(CreateFixedDepositRequest.class)))
        .thenReturn(
            FixedDepositResponse.builder()
                .id(1L)
                .status(FixedDepositStatus.PENDING_FUNDING)
                .fundingAccountId(6L)
                .build());
  }

  @Test
  void funded_shouldAnswer201WithActiveDeposit() throws Exception {
    when(fixedDepositFundingService.fund(1L)).thenReturn(deposit(FixedDepositStatus.ACTIVE));

    create().andExpect(status().isCreated()).andExpect(jsonPath("$.data.status").value("ACTIVE"));
  }

  @Test
  void noClearAnswer_shouldAnswer202WithPendingDeposit() throws Exception {
    when(fixedDepositFundingService.fund(1L))
        .thenReturn(deposit(FixedDepositStatus.PENDING_FUNDING));

    create()
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.data.status").value("PENDING_FUNDING"));
  }

  @Test
  void refused_shouldAnswer400WithReason() throws Exception {
    when(fixedDepositFundingService.fund(1L))
        .thenThrow(new FundingRefusedException("Fixed deposit 1 could not be paid for"));

    create()
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Fixed deposit 1 could not be paid for"));
  }

  private ResultActions create() throws Exception {
    return mockMvc.perform(
        post("/api/v1/fixed-deposits")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                "{\"clientId\": 1, \"principalAmount\": 10000.00, \"interestRate\": 10,"
                    + " \"tenureInDays\": 90, \"payoutAccountId\": 5, \"fundingAccountId\": 6}"));
  }

  private FixedDeposit deposit(FixedDepositStatus status) {
    FixedDeposit deposit = new FixedDeposit();
    deposit.setId(1L);
    deposit.setFundingAccountId(6L);
    deposit.setStatus(status);
    return deposit;
  }
}
