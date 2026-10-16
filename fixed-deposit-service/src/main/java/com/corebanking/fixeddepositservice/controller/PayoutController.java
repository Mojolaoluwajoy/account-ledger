package com.corebanking.fixeddepositservice.controller;

import com.corebanking.fixeddepositservice.dto.ApiResponse;
import com.corebanking.fixeddepositservice.dto.FixedDepositResponse;
import com.corebanking.fixeddepositservice.dto.RetryPayoutRequest;
import com.corebanking.fixeddepositservice.enums.PayoutStatus;
import com.corebanking.fixeddepositservice.service.FixedDepositPayoutService;
import com.corebanking.fixeddepositservice.util.FixedDepositMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    name = "Fixed Deposit Payouts",
    description = "Matured deposits on their way into savings, or rejected by the savings service")
public class PayoutController {

  private final FixedDepositPayoutService fixedDepositPayoutService;

  @Operation(
      summary = "List fixed deposits by payout status",
      description = "Defaults to FAILED: the payouts the savings service rejected.")
  @GetMapping("/payouts")
  public ResponseEntity<ApiResponse<List<FixedDepositResponse>>> getPayouts(
      @RequestParam(defaultValue = "FAILED") PayoutStatus status) {
    List<FixedDepositResponse> response =
        fixedDepositPayoutService.findByPayoutStatus(status).stream()
            .map(FixedDepositMapper::toResponse)
            .toList();
    return ResponseEntity.ok(ApiResponse.success("Payouts retrieved successfully", response));
  }

  @Operation(
      summary = "Retry a failed payout",
      description =
          "Puts a FAILED payout back in the queue. Optionally pass payoutAccountId to pay "
              + "into a different active savings account of the same client.")
  @PostMapping("/{depositId}/payout/retry")
  public ResponseEntity<ApiResponse<FixedDepositResponse>> retry(
      @PathVariable Long depositId, @RequestBody(required = false) RetryPayoutRequest request) {
    log.info("Request received to retry payout of fixed deposit {}", depositId);
    Long newPayoutAccountId = request == null ? null : request.getPayoutAccountId();
    FixedDepositResponse response =
        FixedDepositMapper.toResponse(
            fixedDepositPayoutService.retry(depositId, newPayoutAccountId));
    return ResponseEntity.ok(ApiResponse.success("Payout queued for retry", response));
  }
}
