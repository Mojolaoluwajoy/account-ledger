package com.corebanking.savingsservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SavingsServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(SavingsServiceApplication.class, args);
  }
}
