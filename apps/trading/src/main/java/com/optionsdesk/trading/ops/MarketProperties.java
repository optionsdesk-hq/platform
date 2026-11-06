package com.optionsdesk.trading.ops;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Market configuration ({@code optionsdesk.market.*}). The holiday list comes from the exchange's
 * yearly circular; the deploy blackout window covers the session plus a margin on both sides.
 *
 * @param holidays NSE trading holidays (weekdays)
 * @param deployBlackoutStart no production deploys from this time on trading days
 * @param deployBlackoutEnd until this time
 */
@Validated
@ConfigurationProperties("optionsdesk.market")
public record MarketProperties(
    List<LocalDate> holidays, LocalTime deployBlackoutStart, LocalTime deployBlackoutEnd) {

  public MarketProperties {
    holidays = holidays == null ? List.of() : List.copyOf(holidays);
    deployBlackoutStart = deployBlackoutStart == null ? LocalTime.of(9, 0) : deployBlackoutStart;
    deployBlackoutEnd = deployBlackoutEnd == null ? LocalTime.of(15, 45) : deployBlackoutEnd;
  }
}
