package com.accountledger.fixeddepositservice.controller;

import com.accountledger.fixeddepositservice.dto.ApiResponse;
import com.accountledger.fixeddepositservice.dto.CreateFixedDepositRequest;
import com.accountledger.fixeddepositservice.dto.FixedDepositResponse;
import com.accountledger.fixeddepositservice.enums.FixedDepositStatus;
import com.accountledger.fixeddepositservice.service.FixedDepositFundingService;
import com.accountledger.fixeddepositservice.service.FixedDepositService;
import com.accountledger.fixeddepositservice.util.FixedDepositMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/fixed-deposits")
@RequiredArgsConstructor
@Tag(name = "Fixed Deposits", description = "Endpoints for managing client fixed deposits")
public class FixedDepositController {

  private final FixedDepositService fixedDepositService;
  private final FixedDepositFundingService fixedDepositFundingService;

  @Operation(
      summary = "Create a new fixed deposit",
      description =
          "Creates a fixed deposit for a client. "
              + "Funds are locked until the maturity date. "
              + "Maturity amount is calculated automatically. "
              + "With fundingAccountId the principal is taken from that savings account: "
              + "201 once taken, 400 if the account refuses, or 202 if the savings service "
              + "gave no clear answer (the deposit stays PENDING_FUNDING and is retried). "
              + "Without it the principal is treated as paid in cash.")
  @PostMapping
  public ResponseEntity<ApiResponse<FixedDepositResponse>> createFixedDeposit(
      @Valid @RequestBody CreateFixedDepositRequest request) {
    log.info("Request received to create fixed deposit for client ID: {}", request.getClientId());
    FixedDepositResponse response = fixedDepositService.createFixedDeposit(request);
    // The deposit is saved before the money is taken, so it is never lost
    // once the savings account has paid for it.
    if (response.getStatus() == FixedDepositStatus.PENDING_FUNDING) {
      response = FixedDepositMapper.toResponse(fixedDepositFundingService.fund(response.getId()));
      if (response.getStatus() == FixedDepositStatus.PENDING_FUNDING) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
            .body(
                ApiResponse.success(
                    "Fixed deposit created; its funding will be retried shortly", response));
      }
    }
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success("Fixed deposit created successfully", response));
  }

  @Operation(
      summary = "Get fixed deposit by ID",
      description = "Fetches a single fixed deposit by its system ID.")
  @GetMapping("/{depositId}")
  public ResponseEntity<ApiResponse<FixedDepositResponse>> getDepositById(
      @PathVariable Long depositId) {
    log.info("Request received to fetch fixed deposit with ID: {}", depositId);
    FixedDepositResponse response = fixedDepositService.getDepositById(depositId);
    return ResponseEntity.ok(ApiResponse.success("Fixed deposit retrieved successfully", response));
  }

  @Operation(
      summary = "Get all fixed deposits for a client",
      description = "Returns all fixed deposits belonging to a specific client.")
  @GetMapping("/client/{clientId}")
  public ResponseEntity<ApiResponse<List<FixedDepositResponse>>> getDepositsByClientId(
      @PathVariable Long clientId) {
    log.info("Request received to fetch fixed deposits for client ID: {}", clientId);
    List<FixedDepositResponse> response = fixedDepositService.getDepositsByClientId(clientId);
    return ResponseEntity.ok(
        ApiResponse.success("Fixed deposits retrieved successfully", response));
  }

  @Operation(
      summary = "Withdraw a fixed deposit early",
      description =
          "Ends the deposit before its maturity date. Interest is paid for the days held, "
              + "at the agreed rate minus the early withdrawal penalty (2 points by default), "
              + "into the deposit's payout account.")
  @PostMapping("/{depositId}/withdraw-early")
  public ResponseEntity<ApiResponse<FixedDepositResponse>> withdrawEarly(
      @PathVariable Long depositId) {
    log.info("Request received to withdraw fixed deposit {} early", depositId);
    FixedDepositResponse response = fixedDepositService.withdrawEarly(depositId);
    return ResponseEntity.ok(ApiResponse.success("Fixed deposit withdrawn early", response));
  }

  @Operation(
      summary = "Mature a fixed deposit",
      description =
          "Processes the maturity of a fixed deposit. "
              + "Only allowed on or after the maturity date. "
              + "Attempting before maturity date throws an error.")
  @PostMapping("/{depositId}/mature")
  public ResponseEntity<ApiResponse<FixedDepositResponse>> matureDeposit(
      @PathVariable Long depositId) {
    log.info("Request received to mature fixed deposit with ID: {}", depositId);
    FixedDepositResponse response = fixedDepositService.matureDeposit(depositId);
    return ResponseEntity.ok(ApiResponse.success("Fixed deposit matured successfully", response));
  }
}
