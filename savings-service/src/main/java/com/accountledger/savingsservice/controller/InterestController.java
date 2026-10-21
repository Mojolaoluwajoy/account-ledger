package com.accountledger.savingsservice.controller;

import com.accountledger.savingsservice.dto.ApiResponse;
import com.accountledger.savingsservice.dto.InterestRunResponse;
import com.accountledger.savingsservice.exception.InvalidInterestRunException;
import com.accountledger.savingsservice.service.InterestJob;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/savings/interest")
@RequiredArgsConstructor
@Tag(name = "Savings Interest", description = "Daily interest, paid into accounts monthly")
public class InterestController {

  private final InterestJob interestJob;

  @Operation(
      summary = "Run savings interest now",
      description =
          "Does what the nightly job does: records interest up to the day before asOf "
              + "(default today) and pays out every month that ended before it. Safe to run "
              + "more than once. asOf cannot be in the future.")
  @PostMapping("/run")
  public ResponseEntity<ApiResponse<InterestRunResponse>> run(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate asOf) {
    LocalDate today = LocalDate.now();
    LocalDate runDate = asOf == null ? today : asOf;
    if (runDate.isAfter(today)) {
      throw new InvalidInterestRunException(
          "asOf cannot be in the future: interest is only earned on days that have ended");
    }
    log.info("Request received to run savings interest as of {}", runDate);
    return ResponseEntity.ok(
        ApiResponse.success("Interest run finished", interestJob.run(runDate)));
  }
}
