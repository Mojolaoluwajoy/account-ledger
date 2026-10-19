package com.accountledger.clientservice.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FineractUpdateClientRequest {
  private String firstname;
  private String lastname;
  private String mobileNo;
  private String emailAddress;
}
