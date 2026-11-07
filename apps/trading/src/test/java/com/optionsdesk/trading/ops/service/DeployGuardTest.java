package com.optionsdesk.trading.ops.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.optionsdesk.shared.calendar.MarketCalendar;
import com.optionsdesk.trading.ops.config.MarketProperties;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeployGuardTest {

  static final MarketProperties MARKET =
      new MarketProperties(List.of(LocalDate.of(2026, 11, 10)), null, null); // defaults 09:00–15:45

  static DeployGuard at(String istDateTime) {
    var instant = LocalDateTime.parse(istDateTime).atZone(MarketCalendar.IST).toInstant();
    return new DeployGuard(MARKET, Clock.fixed(instant, MarketCalendar.IST));
  }

  @Test
  void blockedDuringTheSessionWindowOnTradingDays() {
    assertThat(at("2026-11-06T09:00").blockedReason())
        .hasValueSatisfying(r -> assertThat(r).contains("market hours"));
    assertThat(at("2026-11-06T12:30").blockedReason()).isPresent();
    assertThat(at("2026-11-06T15:44").blockedReason()).isPresent();
  }

  @Test
  void allowedOutsideTheWindowOnWeekendsAndHolidays() {
    assertThat(at("2026-11-06T08:59").blockedReason()).isEmpty();
    assertThat(at("2026-11-06T15:45").blockedReason()).isEmpty();
    assertThat(at("2026-11-07T12:00").blockedReason()).isEmpty(); // Saturday
    assertThat(at("2026-11-10T12:00").blockedReason()).isEmpty(); // NSE holiday
  }

  @Test
  void defaultsApplyWhenNotConfigured() {
    var empty = new MarketProperties(null, null, null);
    assertThat(empty.holidays()).isEmpty();
    assertThat(empty.deployBlackoutStart()).hasToString("09:00");
    assertThat(empty.deployBlackoutEnd()).hasToString("15:45");
  }
}
