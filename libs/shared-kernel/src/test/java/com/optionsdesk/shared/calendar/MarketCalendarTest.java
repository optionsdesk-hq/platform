package com.optionsdesk.shared.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MarketCalendarTest {

  // NSE 2026: Tue 10 Nov is a trading holiday (Diwali Balipratipada)
  final MarketCalendar calendar = new MarketCalendar(Set.of(LocalDate.of(2026, 11, 10)));

  @Test
  void weekendsAndHolidaysAreClosed() {
    assertThat(calendar.isTradingDay(LocalDate.of(2026, 11, 6))).isTrue(); // Friday
    assertThat(calendar.isTradingDay(LocalDate.of(2026, 11, 7))).isFalse(); // Saturday
    assertThat(calendar.isTradingDay(LocalDate.of(2026, 11, 10))).isFalse(); // holiday
  }

  @Test
  void nextSessionSkipsWeekendsAndHolidays() {
    assertThat(calendar.nextSession(LocalDate.of(2026, 11, 6)))
        .isEqualTo(LocalDate.of(2026, 11, 9));
    assertThat(calendar.nextSession(LocalDate.of(2026, 11, 9)))
        .isEqualTo(LocalDate.of(2026, 11, 11));
  }

  @Test
  void sessionHoursInIst() {
    assertThat(calendar.isOpen(ist("2026-11-06T09:15"))).isTrue();
    assertThat(calendar.isOpen(ist("2026-11-06T15:29"))).isTrue();
    assertThat(calendar.isOpen(ist("2026-11-06T15:30"))).isFalse();
    assertThat(calendar.isOpen(ist("2026-11-06T09:14"))).isFalse();
    assertThat(calendar.isOpen(ist("2026-11-10T11:00"))).isFalse(); // holiday
  }

  private static ZonedDateTime ist(String localDateTime) {
    return java.time.LocalDateTime.parse(localDateTime).atZone(MarketCalendar.IST);
  }
}
