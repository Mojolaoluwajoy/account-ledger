package com.accountledger.savingsservice.feign;

import com.accountledger.savingsservice.dto.ClientLookupResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "client-service", url = "${client.service.url:http://localhost:8081}")
public interface ClientServiceApi {

  @GetMapping("/api/v1/clients/{clientId}")
  ClientLookupResponse getClientById(@PathVariable("clientId") Long clientId);
}
