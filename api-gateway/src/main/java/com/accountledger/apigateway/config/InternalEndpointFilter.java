package com.accountledger.apigateway.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

/*
 * Keeps endpoints that only our services call off the public gateway. The
 * services reach each other directly inside Docker, never through here, so
 * nothing that should call these is affected. Without this, anyone could post
 * ledger entries, credit an account with a made-up fixed deposit payout or
 * take money out of someone's account as fixed deposit funding.
 *
 * Blocked requests get a 404, as if the endpoint did not exist.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class InternalEndpointFilter extends OncePerRequestFilter {

  private record Blocked(HttpMethod method, String pattern) {}

  private static final List<Blocked> INTERNAL =
      List.of(
          // Ledger entries are posted by the savings and fixed deposit services.
          new Blocked(HttpMethod.POST, "/api/v1/ledger/entries/**"),
          // Payouts and fundings are sent by the fixed deposit service.
          new Blocked(null, "/api/v1/savings-accounts/*/fixed-deposit-payouts/**"),
          new Blocked(null, "/api/v1/savings-accounts/*/fixed-deposit-fundings/**"));

  private final AntPathMatcher matcher = new AntPathMatcher();
  private final UrlPathHelper pathHelper = new UrlPathHelper();

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    // Decoded, without ;params, with // collapsed and ./ and ../ resolved, so
    // variations of the path cannot slip past.
    String path = StringUtils.cleanPath(pathHelper.getLookupPathForRequest(request));
    if (isInternal(request.getMethod(), path)) {
      log.warn(
          "Refused {} {} from {}: internal endpoint",
          request.getMethod(),
          path,
          request.getRemoteAddr());
      response.setStatus(HttpServletResponse.SC_NOT_FOUND);
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      response.getWriter().write("{\"success\":false,\"message\":\"Not found\"}");
      return;
    }
    chain.doFilter(request, response);
  }

  boolean isInternal(String method, String path) {
    String normalized = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    return INTERNAL.stream()
        .anyMatch(
            blocked ->
                (blocked.method() == null || blocked.method().matches(method.toUpperCase()))
                    && matcher.match(blocked.pattern(), normalized));
  }
}
