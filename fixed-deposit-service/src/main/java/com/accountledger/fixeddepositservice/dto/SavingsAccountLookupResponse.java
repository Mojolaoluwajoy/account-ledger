package com.accountledger.fixeddepositservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/* The part of the savings service's answer we need to accept a payout account. */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SavingsAccountLookupResponse {

  private Account data;

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Account {
    private Long id;
    private Long clientId;
    private String status;
  }
}
