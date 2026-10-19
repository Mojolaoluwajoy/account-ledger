package com.corebanking.clientservice;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.corebanking.clientservice.util.Paging;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

class PagingTest {

  @Test
  void request_withValuesInRange_shouldKeepThem() {
    PageRequest request = Paging.request(2, 50, Sort.by("id"));

    assertEquals(2, request.getPageNumber());
    assertEquals(50, request.getPageSize());
  }

  @Test
  void request_withValuesOutOfRange_shouldPullThemIntoRange() {
    assertEquals(0, Paging.request(-1, 20, Sort.by("id")).getPageNumber());
    assertEquals(1, Paging.request(0, 0, Sort.by("id")).getPageSize());
    assertEquals(Paging.MAX_SIZE, Paging.request(0, 5000, Sort.by("id")).getPageSize());
  }
}
