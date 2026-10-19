package com.accountledger.savingsservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

  @Bean
  public OpenAPI savingsServiceOpenAPI() {
    // A relative server URL makes "Try it out" call whichever host served the
    // page: this service when opened directly, the gateway when opened there.
    return new OpenAPI()
        .servers(List.of(new Server().url("/")))
        .info(
            new Info()
                .title("Savings Service API")
                .description("Account Ledger - Savings Management Services")
                .version("1.0"));
  }
}
