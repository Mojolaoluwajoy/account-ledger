package com.accountledger.clientservice.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FineractRequestInterceptor implements RequestInterceptor {

  private final FineractConfig fineractConfig;

  @Override
  public void apply(RequestTemplate template) {
    template.header("Authorization", fineractConfig.getAuthorizationHeader());
    template.header("Fineract-Platform-TenantId", fineractConfig.getTenantId());
    template.header("Content-Type", "application/json");
  }
}
