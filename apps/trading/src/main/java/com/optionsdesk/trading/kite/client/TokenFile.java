package com.optionsdesk.trading.kite.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * Reads the day's Kite access token from the file the login flow writes. Kite ends every session at
 * about 06:00 IST, so a login counts for the trading day of (now − 6 h) in IST, the same rule as
 * the legacy {@code session_day()}. A token for another day is not returned.
 */
public final class TokenFile {

  static final ZoneId IST = ZoneId.of("Asia/Kolkata");
  private static final ObjectMapper JSON = new ObjectMapper();

  private final Path path;
  private final Clock clock;

  public TokenFile(Path path, Clock clock) {
    this.path = path;
    this.clock = clock;
  }

  /** The trading day a login made at {@code now} is good for. */
  public static LocalDate sessionDay(ZonedDateTime now) {
    return now.withZoneSameInstant(IST).minusHours(6).toLocalDate();
  }

  /** Today's token, or empty when there is no file, it is unreadable, or it is from another day. */
  public Optional<String> today() {
    if (!Files.isRegularFile(path)) {
      return Optional.empty();
    }
    try {
      JsonNode n = JSON.readTree(path.toFile());
      String day = n.path("date").asText("");
      String token = n.path("access_token").asText("");
      LocalDate expected = sessionDay(ZonedDateTime.now(clock));
      return day.equals(expected.toString()) && !token.isBlank()
          ? Optional.of(token)
          : Optional.empty();
    } catch (IOException e) {
      return Optional.empty();
    }
  }
}
