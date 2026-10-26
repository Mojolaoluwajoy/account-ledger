package com.accountledger.fixeddepositservice;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.accountledger.fixeddepositservice.entity.FixedDeposit;
import com.accountledger.fixeddepositservice.enums.FixedDepositStatus;
import com.accountledger.fixeddepositservice.enums.FundingStatus;
import com.accountledger.fixeddepositservice.exception.FundingRefusedException;
import com.accountledger.fixeddepositservice.exception.NotRetryableException;
import com.accountledger.fixeddepositservice.service.FixedDepositFundingService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class FundingApiTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private FixedDepositFundingService fixedDepositFundingService;

  @Test
  void list_shouldDefaultToRefusedFundings() throws Exception {
    FixedDeposit refused = deposit(FixedDepositStatus.FUNDING_FAILED);
    refused.setFundingAttempts(1);
    refused.setFundingError("Savings service: insufficient balance");
    when(fixedDepositFundingService.findByFundingStatus(FundingStatus.FUNDING_FAILED))
        .thenReturn(List.of(refused));

    mockMvc
        .perform(get("/api/v1/admin/fixed-deposits/fundings"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].status").value("FUNDING_FAILED"))
        .andExpect(jsonPath("$.data[0].fundingAttempts").value(1))
        .andExpect(
            jsonPath("$.data[0].fundingError").value("Savings service: insufficient balance"));
  }

  @Test
  void list_withStatusThatIsNotAboutFunding_shouldAnswer400() throws Exception {
    mockMvc
        .perform(get("/api/v1/admin/fixed-deposits/fundings?status=ACTIVE"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void retry_funded_shouldAnswer200WithActiveDeposit() throws Exception {
    when(fixedDepositFundingService.fund(1L)).thenReturn(deposit(FixedDepositStatus.ACTIVE));

    mockMvc
        .perform(
            post("/api/v1/admin/fixed-deposits/1/funding/retry")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fundingAccountId\": 7}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("ACTIVE"));

    verify(fixedDepositFundingService).requeue(1L, 7L);
  }

  @Test
  void retry_withoutBody_shouldKeepTheAccount() throws Exception {
    when(fixedDepositFundingService.fund(1L)).thenReturn(deposit(FixedDepositStatus.ACTIVE));

    mockMvc
        .perform(post("/api/v1/admin/fixed-deposits/1/funding/retry"))
        .andExpect(status().isOk());

    verify(fixedDepositFundingService).requeue(1L, null);
  }

  @Test
  void retry_noClearAnswer_shouldAnswer202() throws Exception {
    when(fixedDepositFundingService.fund(1L))
        .thenReturn(deposit(FixedDepositStatus.PENDING_FUNDING));

    mockMvc
        .perform(post("/api/v1/admin/fixed-deposits/1/funding/retry"))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.data.status").value("PENDING_FUNDING"));
  }

  @Test
  void retry_refusedAgain_shouldAnswer400WithReason() throws Exception {
    when(fixedDepositFundingService.fund(1L))
        .thenThrow(new FundingRefusedException("Fixed deposit 1 could not be paid for"));

    mockMvc
        .perform(post("/api/v1/admin/fixed-deposits/1/funding/retry"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Fixed deposit 1 could not be paid for"));
  }

  @Test
  void retry_depositNotRefused_shouldAnswer409() throws Exception {
    when(fixedDepositFundingService.requeue(1L, null))
        .thenThrow(new NotRetryableException("Fixed deposit 1 is ACTIVE"));

    mockMvc
        .perform(post("/api/v1/admin/fixed-deposits/1/funding/retry"))
        .andExpect(status().isConflict());
  }

  private FixedDeposit deposit(FixedDepositStatus status) {
    FixedDeposit deposit = new FixedDeposit();
    deposit.setId(1L);
    deposit.setFundingAccountId(7L);
    deposit.setStatus(status);
    return deposit;
  }
}
