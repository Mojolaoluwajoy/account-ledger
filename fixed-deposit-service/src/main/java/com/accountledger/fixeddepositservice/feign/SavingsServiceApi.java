package com.accountledger.fixeddepositservice.feign;

import com.accountledger.fixeddepositservice.dto.SavingsAccountLookupResponse;
import java.util.Map;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "savings-service", url = "${savings.service.url:http://localhost:8082}")
public interface SavingsServiceApi {

  @GetMapping("/api/v1/savings-accounts/{accountId}")
  SavingsAccountLookupResponse getAccountById(@PathVariable("accountId") Long accountId);

  @PostMapping("/api/v1/savings-accounts/{accountId}/fixed-deposit-payouts")
  void creditFixedDepositPayout(
      @PathVariable("accountId") Long accountId, @RequestBody Map<String, Object> request);
}
