package com.accountledger.fixeddepositservice.controller;

import com.accountledger.fixeddepositservice.dto.ApiResponse;
import com.accountledger.fixeddepositservice.dto.FixedDepositResponse;
import com.accountledger.fixeddepositservice.dto.RetryFundingRequest;
import com.accountledger.fixeddepositservice.enums.FixedDepositStatus;
import com.accountledger.fixeddepositservice.enums.FundingStatus;
import com.accountledger.fixeddepositservice.service.FixedDepositFundingService;
import com.accountledger.fixeddepositservice.util.FixedDepositMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/fixed-deposits")
@RequiredArgsConstructor
@Tag(
    name = "Fixed Deposit Funding",
    description = "Deposits waiting for their money from savings, or refused by the account")
public class FundingController {

  private final FixedDepositFundingService fixedDepositFundingService;

  @Operation(
      summary = "List fixed deposits by funding status",
      description =
          "FUNDING_FAILED (the default): refused by the funding account, nothing taken. "
              + "PENDING_FUNDING: still being retried; see fundingAttempts and fundingError.")
  @GetMapping("/fundings")
  public ResponseEntity<ApiResponse<List<FixedDepositResponse>>> getFundings(
      @RequestParam(defaultValue = "FUNDING_FAILED") FundingStatus status) {
    List<FixedDepositResponse> response =
        fixedDepositFundingService.findByFundingStatus(status).stream()
            .map(FixedDepositMapper::toResponse)
            .toList();
    return ResponseEntity.ok(ApiResponse.success("Fundings retrieved successfully", response));
  }

  @Operation(
      summary = "Retry a refused funding",
      description =
          "Tries again to take the money for a FUNDING_FAILED deposit. Optionally pass "
              + "fundingAccountId to take it from a different active savings account of the "
              + "same client. 200 once taken, 400 if refused again, or 202 if the savings "
              + "service gave no clear answer (it is retried in the background).")
  @PostMapping("/{depositId}/funding/retry")
  public ResponseEntity<ApiResponse<FixedDepositResponse>> retry(
      @PathVariable Long depositId, @RequestBody(required = false) RetryFundingRequest request) {
    log.info("Request received to retry funding of fixed deposit {}", depositId);
    Long newFundingAccountId = request == null ? null : request.getFundingAccountId();
    fixedDepositFundingService.requeue(depositId, newFundingAccountId);
    FixedDepositResponse response =
        FixedDepositMapper.toResponse(fixedDepositFundingService.fund(depositId));
    if (response.getStatus() == FixedDepositStatus.PENDING_FUNDING) {
      return ResponseEntity.status(HttpStatus.ACCEPTED)
          .body(ApiResponse.success("Funding will be retried shortly", response));
    }
    return ResponseEntity.ok(ApiResponse.success("Fixed deposit funded", response));
  }
}
