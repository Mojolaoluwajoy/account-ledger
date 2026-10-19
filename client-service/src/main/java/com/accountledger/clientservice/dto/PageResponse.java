package com.accountledger.clientservice.dto;

import java.util.List;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.domain.Page;

/* One page of a list, with what is needed to ask for the next one. */
@Data
@Builder
public class PageResponse<T> {

  private List<T> content;

  /* Zero-based. */
  private int page;

  private int size;
  private long totalElements;
  private int totalPages;

  public static <T> PageResponse<T> of(Page<?> page, List<T> content) {
    return PageResponse.<T>builder()
        .content(content)
        .page(page.getNumber())
        .size(page.getSize())
        .totalElements(page.getTotalElements())
        .totalPages(page.getTotalPages())
        .build();
  }
}
