package com.corebanking.savingsservice.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

public final class Paging {

  public static final int DEFAULT_SIZE = 20;
  public static final int MAX_SIZE = 100;

  /* Out-of-range values are pulled into range rather than refused. */
  public static PageRequest request(int page, int size, Sort sort) {
    return PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_SIZE), sort);
  }

  private Paging() {}
}
