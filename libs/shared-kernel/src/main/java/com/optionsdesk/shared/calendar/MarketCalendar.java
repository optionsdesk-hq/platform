package com.optionsdesk.shared.calendar;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Set;

/**
 * NSE trading days and session hours. Holidays come from configuration (the exchange's yearly
 * circular); weekends are never trading days.
 */
public final class MarketCalendar {

  public static final ZoneId IST = ZoneId.of("Asia/Kolkata");
  public static final LocalTime OPEN = LocalTime.of(9, 15);
  public static final LocalTime CLOSE = LocalTime.of(15, 30);

  private final Set<LocalDate> holidays;

  public MarketCalendar(Set<LocalDate> holidays) {
    this.holidays = Set.copyOf(holidays);
  }

  public boolean isTradingDay(LocalDate day) {
    DayOfWeek dow = day.getDayOfWeek();
    return dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY && !holidays.contains(day);
  }

  /** The first trading day strictly after {@code day}. */
  public LocalDate nextSession(LocalDate day) {
    LocalDate next = day.plusDays(1);
    while (!isTradingDay(next)) {
      next = next.plusDays(1);
    }
    return next;
  }

  /** True during the regular session (09:15–15:30 IST) on a trading day. */
  public boolean isOpen(ZonedDateTime at) {
    ZonedDateTime ist = at.withZoneSameInstant(IST);
    LocalTime t = ist.toLocalTime();
    return isTradingDay(ist.toLocalDate()) && !t.isBefore(OPEN) && t.isBefore(CLOSE);
  }
}
