package com.accountledger.savingsservice.feign;

import java.util.Map;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "ledger-service", url = "${ledger.service.url:http://localhost:8084}")
public interface LedgerServiceApi {

  @PostMapping("/api/v1/ledger/entries")
  void postEntry(@RequestBody Map<String, Object> request);
}
