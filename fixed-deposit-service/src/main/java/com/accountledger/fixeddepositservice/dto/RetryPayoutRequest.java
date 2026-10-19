package com.accountledger.fixeddepositservice.dto;

import lombok.Data;

@Data
public class RetryPayoutRequest {

  /*
   * Optional. Pays into this savings account instead, for when the original
   * one can no longer receive money (for example it has been closed). It must
   * be an active account of the same client.
   */
  private Long payoutAccountId;
}
