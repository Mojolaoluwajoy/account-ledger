package com.accountledger.savingsservice.controller;

import com.accountledger.savingsservice.dto.ApiResponse;
import com.accountledger.savingsservice.dto.LedgerPostingResponse;
import com.accountledger.savingsservice.enums.LedgerPostingStatus;
import com.accountledger.savingsservice.service.LedgerPostingService;
import com.accountledger.savingsservice.util.LedgerPostingMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/savings/ledger-postings")
@RequiredArgsConstructor
@Tag(
    name = "Savings Ledger Postings",
    description = "Ledger entries waiting to be sent, or rejected by the ledger service")
public class LedgerPostingController {

  private final LedgerPostingService ledgerPostingService;

  @Operation(
      summary = "List ledger postings by status",
      description = "Defaults to FAILED: the postings the ledger service rejected.")
  @GetMapping
  public ResponseEntity<ApiResponse<List<LedgerPostingResponse>>> getPostings(
      @RequestParam(defaultValue = "FAILED") LedgerPostingStatus status) {
    List<LedgerPostingResponse> response =
        ledgerPostingService.findByStatus(status).stream()
            .map(LedgerPostingMapper::toResponse)
            .toList();
    return ResponseEntity.ok(
        ApiResponse.success("Ledger postings retrieved successfully", response));
  }

  @Operation(
      summary = "Retry a failed ledger posting",
      description =
          "Puts a FAILED posting back in the queue. Fix whatever made the ledger service "
              + "reject it first, or it will fail again.")
  @PostMapping("/{postingId}/retry")
  public ResponseEntity<ApiResponse<LedgerPostingResponse>> retry(@PathVariable Long postingId) {
    log.info("Request received to retry ledger posting {}", postingId);
    LedgerPostingResponse response =
        LedgerPostingMapper.toResponse(ledgerPostingService.retry(postingId));
    return ResponseEntity.ok(ApiResponse.success("Ledger posting queued for retry", response));
  }
}
