package com.optionsdesk.trading.kite;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Kite configuration ({@code optionsdesk.kite.*}). The adapter is only created when {@code api-key}
 * is set.
 *
 * @param baseUrl Kite's REST API
 * @param apiKey the app's API key (from the environment, never committed)
 * @param tokenPath the token file written by the daily login ({@code {"access_token", "date"}})
 * @param quoteMinInterval Kite allows 1 quote request a second
 * @param historyMinInterval and 3 historical requests a second
 */
@ConfigurationProperties("optionsdesk.kite")
public record KiteProperties(
    String baseUrl,
    String apiKey,
    Path tokenPath,
    Duration quoteMinInterval,
    Duration historyMinInterval) {

  public KiteProperties {
    baseUrl = baseUrl == null ? "https://api.kite.trade" : baseUrl;
    tokenPath = tokenPath == null ? Path.of("/app/data/kite_token.json") : tokenPath;
    quoteMinInterval = quoteMinInterval == null ? Duration.ofSeconds(1) : quoteMinInterval;
    historyMinInterval = historyMinInterval == null ? Duration.ofMillis(350) : historyMinInterval;
  }

  /** Never print the key. */
  @Override
  public String toString() {
    return "KiteProperties[baseUrl=%s, apiKey=%s, tokenPath=%s]"
        .formatted(baseUrl, apiKey == null || apiKey.isBlank() ? "unset" : "set", tokenPath);
  }
}
