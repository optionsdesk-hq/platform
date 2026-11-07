package com.optionsdesk.trading.ops.service;

import com.optionsdesk.shared.calendar.MarketCalendar;
import com.optionsdesk.trading.ops.config.MarketProperties;
import java.time.Clock;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Answers "may production be deployed right now?". The CD pipeline calls it before every production
 * deploy: never during market hours (plus margins) on a trading day. Data-job locks join this check
 * when the jobs move to Java.
 *
 * <p>The clock is injected, so tests can ask about any moment without waiting for it.
 */
@Service
public class DeployGuard {

  private final MarketCalendar calendar;
  private final MarketProperties market;
  private final Clock clock;

  public DeployGuard(MarketProperties market, Clock clock) {
    this.calendar = new MarketCalendar(new HashSet<>(market.holidays()));
    this.market = market;
    this.clock = clock;
  }

  /** Why a deploy is blocked now, or empty when it is allowed. */
  public Optional<String> blockedReason() {
    ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(MarketCalendar.IST);
    LocalTime t = now.toLocalTime();
    boolean inWindow =
        !t.isBefore(market.deployBlackoutStart()) && t.isBefore(market.deployBlackoutEnd());
    if (calendar.isTradingDay(now.toLocalDate()) && inWindow) {
      return Optional.of(
          "market hours: no deploys between %s and %s IST on trading days"
              .formatted(market.deployBlackoutStart(), market.deployBlackoutEnd()));
    }
    return Optional.empty();
  }
}
