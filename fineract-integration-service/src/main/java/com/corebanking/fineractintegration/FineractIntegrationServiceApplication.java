package com.corebanking.fineractintegration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class FineractIntegrationServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(FineractIntegrationServiceApplication.class, args);
  }
}
