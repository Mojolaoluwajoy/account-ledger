package com.accountledger.apigateway;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.accountledger.apigateway.config.InternalEndpointFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class InternalEndpointFilterTest {

  private final InternalEndpointFilter filter = new InternalEndpointFilter();

  @Test
  void postingLedgerEntries_shouldBeRefused() throws Exception {
    assertEquals(404, statusFor("POST", "/api/v1/ledger/entries"));
    assertEquals(404, statusFor("POST", "/api/v1/ledger/entries/"));
    assertEquals(404, statusFor("POST", "/api/v1//ledger/entries"));
    assertEquals(404, statusFor("POST", "/api/v1/ledger/entries;x=1"));
    assertEquals(404, statusFor("post", "/api/v1/ledger/entries"));
    assertEquals(404, statusFor("POST", "/api/v1/ledger/./entries"));
    assertEquals(404, statusFor("POST", "/api/v1/ledger/x/../entries"));
    assertEquals(404, statusFor("POST", "/api/v1/ledger/%65ntries"));
  }

  @Test
  void creditingFixedDepositPayouts_shouldBeRefused() throws Exception {
    assertEquals(404, statusFor("POST", "/api/v1/savings-accounts/1/fixed-deposit-payouts"));
    assertEquals(404, statusFor("POST", "/api/v1/savings-accounts/1/fixed-deposit-payouts/"));
  }

  @Test
  void takingFixedDepositFundings_shouldBeRefused() throws Exception {
    assertEquals(404, statusFor("POST", "/api/v1/savings-accounts/1/fixed-deposit-fundings"));
    assertEquals(404, statusFor("POST", "/api/v1/savings-accounts/1/fixed-deposit-fundings/"));
  }

  @Test
  void publicEndpoints_shouldPassThrough() throws Exception {
    assertEquals(200, statusFor("GET", "/api/v1/ledger/entries"));
    assertEquals(200, statusFor("GET", "/api/v1/ledger/entries/reference/FD-1"));
    assertEquals(200, statusFor("POST", "/api/v1/savings-accounts/1/deposit"));
    assertEquals(200, statusFor("POST", "/api/v1/savings-accounts/1/transfer"));
  }

  private int statusFor(String method, String uri) throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(request, response, new MockFilterChain());
    return response.getStatus();
  }
}
