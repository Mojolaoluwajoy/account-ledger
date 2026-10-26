package com.accountledger.fixeddepositservice.dto;

import lombok.Data;

@Data
public class RetryFundingRequest {

  /*
   * Optional. Takes the money from this savings account instead, for when the
   * original one cannot pay (for example it has been closed). It must be an
   * active account of the same client.
   */
  private Long fundingAccountId;
}
