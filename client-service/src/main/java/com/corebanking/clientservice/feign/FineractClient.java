package com.corebanking.clientservice.feign;

import com.corebanking.clientservice.dto.FineractClientResponse;
import com.corebanking.clientservice.dto.FineractClientSearchResponse;
import com.corebanking.clientservice.dto.FineractCreateClientRequest;
import com.corebanking.clientservice.dto.FineractUpdateClientRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "fineract-client-service", url = "${fineract.base-url}")
public interface FineractClient {

  @PostMapping("/clients")
  FineractClientResponse createClient(@RequestBody FineractCreateClientRequest request);

  @PutMapping("/clients/{clientId}")
  FineractClientResponse updateClient(
      @PathVariable("clientId") Long clientId, @RequestBody FineractUpdateClientRequest request);

  @GetMapping("/clients")
  FineractClientSearchResponse findByExternalId(@RequestParam("externalId") String externalId);

  @GetMapping("/clients/{clientId}")
  Object getClientById(@PathVariable("clientId") Long clientId);
}
