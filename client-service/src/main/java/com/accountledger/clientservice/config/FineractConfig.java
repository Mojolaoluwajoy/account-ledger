package com.accountledger.clientservice.config;

import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FineractConfig {

  @Value("${fineract.base-url}")
  private String baseUrl;

  @Value("${fineract.tenant-id}")
  private String tenantId;

  @Value("${fineract.username}")
  private String username;

  @Value("${fineract.password}")
  private String password;

  public String getBaseUrl() {
    return baseUrl;
  }

  public String getTenantId() {
    return tenantId;
  }

  public String getAuthorizationHeader() {
    String credentials = username + ":" + password;
    return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes());
  }
}
