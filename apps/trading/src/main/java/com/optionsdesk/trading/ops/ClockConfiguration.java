package com.optionsdesk.trading.ops;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * One injectable clock for the whole app (IST), so every time-based rule can be tested with a fixed
 * clock.
 */
@Configuration
class ClockConfiguration {

  @Bean
  Clock clock() {
    return Clock.system(com.optionsdesk.shared.calendar.MarketCalendar.IST);
  }
}
