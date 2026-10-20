package com.accountledger.savingsservice.dto;

import lombok.Data;

@Data
public class CloseAccountRequest {

  /*
   * Where to move whatever is left in the account, including interest paid
   * on closure. Required if anything is left; leave it out if the account is
   * empty.
   */
  private Long transferToAccountId;
}
