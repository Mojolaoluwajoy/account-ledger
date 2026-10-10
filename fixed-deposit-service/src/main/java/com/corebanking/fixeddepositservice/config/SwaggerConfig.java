package com.corebanking.fixeddepositservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

  @Bean
  public OpenAPI fixedDepositServiceOpenAPI() {
    // A relative server URL makes "Try it out" call whichever host served the
    // page: this service when opened directly, the gateway when opened there.
    return new OpenAPI()
        .servers(List.of(new Server().url("/")))
        .info(
            new Info()
                .title("Fixed Deposit Service API")
                .description("Core Banking - Fixed Deposit Management Service")
                .version("1.0"));
  }
}
