package com.optionsdesk.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** The trading API over HTTP. The token is sent only on writes and never printed. */
final class ApiClient {

  /** The API refused or couldn't be reached; {@code exitCode} is what the CLI returns. */
  static final class Failure extends RuntimeException {
    private static final long serialVersionUID = 1L;
    final int exitCode;

    Failure(String message, int exitCode) {
      super(message);
      this.exitCode = exitCode;
    }
  }

  private static final ObjectMapper JSON = new ObjectMapper();

  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  private final String base;
  private final Optional<String> token;

  ApiClient(String base, Optional<String> token) {
    this.base = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    this.token = token.filter(t -> !t.isBlank());
  }

  JsonNode get(String path, Map<String, String> query) {
    return send(HttpRequest.newBuilder(uri(path, query)).GET(), false);
  }

  JsonNode post(String path, Map<String, String> query) {
    if (token.isEmpty()) {
      throw new Failure("writes need the API token: set OD_TOKEN", 3);
    }
    var req =
        HttpRequest.newBuilder(uri(path, query))
            .header("Authorization", "Bearer " + token.get())
            .POST(HttpRequest.BodyPublishers.noBody());
    return send(req, true);
  }

  private URI uri(String path, Map<String, String> query) {
    String q =
        query.entrySet().stream()
            .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
            .collect(Collectors.joining("&"));
    return URI.create(base + path + (q.isEmpty() ? "" : "?" + q));
  }

  private JsonNode send(HttpRequest.Builder req, boolean write) {
    HttpResponse<String> res;
    try {
      res =
          http.send(
              req.timeout(Duration.ofSeconds(15)).build(), HttpResponse.BodyHandlers.ofString());
    } catch (IOException e) {
      throw new Failure(
          "can't reach the API at " + base + " (" + e.getClass().getSimpleName() + ")", 2);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new Failure("interrupted", 2);
    }
    JsonNode body = parse(res.body());
    int s = res.statusCode();
    if (s == 401 || s == 403) {
      throw new Failure("refused (" + s + "): " + body.path("message").asText("check OD_TOKEN"), 3);
    }
    if (s == 404) {
      throw new Failure(body.path("message").asText("not found"), 4);
    }
    if (s >= 400) {
      throw new Failure("API error " + s + (write ? " (nothing may have changed)" : ""), 2);
    }
    return body;
  }

  private static JsonNode parse(String s) {
    try {
      return JSON.readTree(s == null || s.isBlank() ? "{}" : s);
    } catch (IOException e) {
      return JSON.createObjectNode();
    }
  }
}
