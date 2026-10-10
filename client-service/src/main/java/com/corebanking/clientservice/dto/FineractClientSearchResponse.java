package com.corebanking.clientservice.dto;

import java.util.List;
import lombok.Data;

/* The part of Fineract's "list clients" response that we use. */
@Data
public class FineractClientSearchResponse {

  private List<Item> pageItems;

  @Data
  public static class Item {
    private Long id;
    private String firstname;
    private String lastname;
    private String externalId;
  }
}
