package com.corebanking.fixeddepositservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/* The part of the client service's answer we need: the client's status. */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ClientLookupResponse {

  private Client data;

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Client {
    private Long id;
    private String status;
  }
}
