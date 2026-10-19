package com.corebanking.savingsservice.controller;

import com.corebanking.savingsservice.dto.*;
import com.corebanking.savingsservice.service.SavingsAccountService;
import com.corebanking.savingsservice.util.Paging;
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
@RequestMapping("/api/v1/savings-accounts")
@RequiredArgsConstructor
@Tag(name = "Savings Accounts", description = "Endpoints for managing client savings accounts")
public class SavingsAccountController {

  private final SavingsAccountService savingsAccountService;

  @Operation(
      summary = "Open a new savings account",
      description = "Opens a new savings account for an existing client under a specified product.")
  @PostMapping
  public ResponseEntity<ApiResponse<SavingsAccountResponse>> openAccount(
      @Valid @RequestBody OpenSavingsAccountRequest request) {
    log.info("Request received to open savings account for client ID: {}", request.getClientId());
    SavingsAccountResponse response = savingsAccountService.openAccount(request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success("Savings account opened successfully", response));
  }

  @Operation(
      summary = "Get savings account by ID",
      description = "Fetches a single savings account by its system ID.")
  @GetMapping("/{accountId}")
  public ResponseEntity<ApiResponse<SavingsAccountResponse>> getAccountById(
      @PathVariable Long accountId) {
    log.info("Request received to fetch savings account with ID: {}", accountId);
    SavingsAccountResponse response = savingsAccountService.getAccountById(accountId);
    return ResponseEntity.ok(
        ApiResponse.success("Savings account retrieved successfully", response));
  }

  @Operation(
      summary = "Get all savings accounts for a client",
      description = "Returns all savings accounts belonging to a specific client.")
  @GetMapping("/client/{clientId}")
  public ResponseEntity<ApiResponse<List<SavingsAccountResponse>>> getAccountsByClientId(
      @PathVariable Long clientId) {
    log.info("Request received to fetch savings accounts for client ID: {}", clientId);
    List<SavingsAccountResponse> response = savingsAccountService.getAccountsByClientId(clientId);
    return ResponseEntity.ok(
        ApiResponse.success("Savings accounts retrieved successfully", response));
  }

  @Operation(
      summary = "Deposit money into a savings account",
      description =
          "Deposits the specified amount into the savings account. "
              + "Balance is updated immediately.")
  @PostMapping("/{accountId}/deposit")
  public ResponseEntity<ApiResponse<TransactionResponse>> deposit(
      @PathVariable Long accountId, @Valid @RequestBody TransactionRequest request) {
    log.info("Request received to deposit {} into account ID: {}", request.getAmount(), accountId);
    TransactionResponse response = savingsAccountService.deposit(accountId, request);
    return ResponseEntity.ok(ApiResponse.success("Deposit successful", response));
  }

  @Operation(
      summary = "Withdraw money from a savings account",
      description =
          "Withdraws the specified amount from the savings account. "
              + "Blocked if withdrawal would breach minimum balance.")
  @PostMapping("/{accountId}/withdraw")
  public ResponseEntity<ApiResponse<TransactionResponse>> withdraw(
      @PathVariable Long accountId, @Valid @RequestBody TransactionRequest request) {
    log.info("Request received to withdraw {} from account ID: {}", request.getAmount(), accountId);
    TransactionResponse response = savingsAccountService.withdraw(accountId, request);
    return ResponseEntity.ok(ApiResponse.success("Withdrawal successful", response));
  }

  @Operation(
      summary = "Transfer money to another savings account",
      description =
          "Moves the specified amount from this savings account to another one. "
              + "Blocked if it would breach the minimum balance of this account.")
  @PostMapping("/{accountId}/transfer")
  public ResponseEntity<ApiResponse<TransferResponse>> transfer(
      @PathVariable Long accountId, @Valid @RequestBody TransferRequest request) {
    log.info(
        "Request received to transfer {} from account ID: {} to account ID: {}",
        request.getAmount(),
        accountId,
        request.getDestinationAccountId());
    TransferResponse response = savingsAccountService.transfer(accountId, request);
    return ResponseEntity.ok(ApiResponse.success("Transfer successful", response));
  }

  @Operation(
      summary = "Pay a matured fixed deposit into a savings account",
      description =
          "Called by the fixed deposit service. Credits principal and interest in one "
              + "transaction. Sending the same reference again changes nothing.")
  @PostMapping("/{accountId}/fixed-deposit-payouts")
  public ResponseEntity<ApiResponse<TransactionResponse>> creditFixedDepositPayout(
      @PathVariable Long accountId, @Valid @RequestBody FixedDepositPayoutRequest request) {
    log.info(
        "Request received to pay fixed deposit {} into account ID: {}",
        request.getReference(),
        accountId);
    TransactionResponse response =
        savingsAccountService.creditFixedDepositPayout(accountId, request);
    return ResponseEntity.ok(ApiResponse.success("Fixed deposit payout credited", response));
  }

  @Operation(
      summary = "Get transaction history for a savings account",
      description =
          "Returns the transaction history, most recent first, one page at a time. "
              + "Same as the transaction history screen in internet banking.")
  @GetMapping("/{accountId}/transactions")
  public ResponseEntity<ApiResponse<PageResponse<SavingsTransactionResponse>>>
      getTransactionHistory(
          @PathVariable Long accountId,
          @RequestParam(defaultValue = "0") int page,
          @RequestParam(defaultValue = "" + Paging.DEFAULT_SIZE) int size) {
    log.info("Request received to fetch transaction history for account ID: {}", accountId);
    PageResponse<SavingsTransactionResponse> transactions =
        savingsAccountService.getTransactionHistory(accountId, page, size);
    return ResponseEntity.ok(
        ApiResponse.success("Transaction history retrieved successfully", transactions));
  }
}
