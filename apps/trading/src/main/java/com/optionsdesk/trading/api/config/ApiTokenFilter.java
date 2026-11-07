package com.optionsdesk.trading.api.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Writes to the API ({@code POST/PUT/PATCH/DELETE /api/**}) need {@code Authorization: Bearer
 * <token>}. Fails closed: with no token configured, every write is refused. Reads pass (the API is
 * only reachable on the private network). The token is compared in constant time and never logged.
 */
final class ApiTokenFilter extends OncePerRequestFilter {

  private final byte[] token;

  ApiTokenFilter(String token) {
    this.token = token == null ? new byte[0] : token.getBytes(StandardCharsets.UTF_8);
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String m = request.getMethod();
    return m.equals("GET") || m.equals("HEAD") || m.equals("OPTIONS");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (token.length == 0) {
      deny(response, 403, "writes are disabled: no API token is configured");
      return;
    }
    String h = request.getHeader("Authorization");
    byte[] given =
        h != null && h.startsWith("Bearer ")
            ? h.substring(7).trim().getBytes(StandardCharsets.UTF_8)
            : new byte[0];
    if (!MessageDigest.isEqual(token, given)) {
      deny(response, 401, "a valid bearer token is required");
      return;
    }
    chain.doFilter(request, response);
  }

  private static void deny(HttpServletResponse response, int status, String message)
      throws IOException {
    response.setStatus(status);
    response.setContentType("application/json");
    response.getWriter().write("{\"done\":false,\"message\":\"" + message + "\"}");
  }
}
