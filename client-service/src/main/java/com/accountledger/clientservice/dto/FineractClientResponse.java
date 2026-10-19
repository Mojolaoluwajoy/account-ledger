package com.accountledger.clientservice.dto;

import lombok.Data;

@Data
public class FineractClientResponse {
  private Long clientId;
  private Long officeId;
  private Long resourceId;
}
