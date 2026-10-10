package com.corebanking.fixeddepositservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FixedDepositServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(FixedDepositServiceApplication.class, args);
  }
}
