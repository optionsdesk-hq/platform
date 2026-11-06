package com.optionsdesk.trading.kite;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TokenFileTest {

  @TempDir Path dir;

  static Clock at(String isoInstant) {
    return Clock.fixed(Instant.parse(isoInstant), ZoneOffset.UTC);
  }

  @Test
  void aLoginBefore0600IstCountsForThePreviousDay() {
    assertThat(TokenFile.sessionDay(ZonedDateTime.parse("2026-11-09T05:59+05:30")))
        .isEqualTo(LocalDate.of(2026, 11, 8));
    assertThat(TokenFile.sessionDay(ZonedDateTime.parse("2026-11-09T06:00+05:30")))
        .isEqualTo(LocalDate.of(2026, 11, 9));
    assertThat(TokenFile.sessionDay(ZonedDateTime.parse("2026-11-09T01:00Z")))
        .isEqualTo(LocalDate.of(2026, 11, 9));
  }

  @Test
  void returnsOnlyTodaysToken() throws Exception {
    Path f = dir.resolve("kite_token.json");
    var file = new TokenFile(f, at("2026-11-09T04:00:00Z")); // 09:30 IST
    assertThat(file.today()).isEmpty(); // no file
    Files.writeString(f, "{\"access_token\":\"abc\",\"date\":\"2026-11-09\"}");
    assertThat(file.today()).contains("abc");
    Files.writeString(f, "{\"access_token\":\"abc\",\"date\":\"2026-11-06\"}");
    assertThat(file.today()).isEmpty(); // yesterday's
    Files.writeString(f, "{\"access_token\":\"\",\"date\":\"2026-11-09\"}");
    assertThat(file.today()).isEmpty();
    Files.writeString(f, "not json");
    assertThat(file.today()).isEmpty();
  }
}
