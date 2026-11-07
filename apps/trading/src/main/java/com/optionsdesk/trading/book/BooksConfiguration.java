package com.optionsdesk.trading.book;

import com.optionsdesk.shared.calendar.MarketCalendar;
import com.optionsdesk.trading.ops.MarketProperties;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Runs the minute engine, only when {@code optionsdesk.books.enabled=true}: off by default,
 * switched on per environment. Needs a {@link DayFeed.Factory} (the broker adapter provides one)
 * and any number of {@link BookFactory} beans (from strategy plug-ins). Each book gets its own
 * single virtual thread.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "optionsdesk.books.enabled", havingValue = "true")
@EnableScheduling
class BooksConfiguration {

  @Bean
  MinuteEngine minuteEngine(
      MarketProperties market, DayFeed.Factory feeds, List<BookFactory> factories) {
    // one line at startup, so a deploy shows at once whether the engine and the expected books are
    // wired in
    LoggerFactory.getLogger(MinuteEngine.class)
        .info(
            "minute engine ON: {} book factor{} registered",
            factories.size(),
            factories.size() == 1 ? "y" : "ies");
    return new MinuteEngine(
        new MarketCalendar(new HashSet<>(market.holidays())),
        feeds,
        factories,
        BooksConfiguration::executorFor);
  }

  @Bean
  MinuteTicker minuteTicker(MinuteEngine engine, Clock clock) {
    return new MinuteTicker(engine, clock);
  }

  static Executor executorFor(String bookId) {
    return Executors.newSingleThreadExecutor(Thread.ofVirtual().name("book-" + bookId).factory());
  }

  /**
   * Two seconds into every minute of the session hours, IST (the engine itself checks holidays and
   * the close).
   */
  static final class MinuteTicker {
    private final MinuteEngine engine;
    private final Clock clock;

    MinuteTicker(MinuteEngine engine, Clock clock) {
      this.engine = engine;
      this.clock = clock;
    }

    @Scheduled(cron = "2 * 9-15 * * MON-FRI", zone = "Asia/Kolkata")
    void tick() {
      engine.tick(ZonedDateTime.now(clock));
    }
  }
}
