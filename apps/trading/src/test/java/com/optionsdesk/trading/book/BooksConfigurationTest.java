package com.optionsdesk.trading.book;

import static org.assertj.core.api.Assertions.assertThat;

import com.optionsdesk.trading.ops.MarketProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The engine exists only when switched on. */
class BooksConfigurationTest {

  @Configuration(proxyBeanMethods = false)
  static class Base {
    @Bean
    MarketProperties market() {
      return new MarketProperties(List.of(), null, null);
    }

    @Bean
    Clock clock() {
      return Clock.fixed(Instant.parse("2026-11-14T05:00:00Z"), ZoneOffset.UTC); // a Saturday
    }

    @Bean
    DayFeed.Factory feeds() {
      return d -> {
        throw new IllegalStateException("not on a Saturday");
      };
    }
  }

  final ApplicationContextRunner runner =
      new ApplicationContextRunner().withUserConfiguration(Base.class, BooksConfiguration.class);

  @Test
  void offByDefault() {
    runner.run(ctx -> assertThat(ctx).doesNotHaveBean(MinuteEngine.class));
  }

  @Test
  void onWhenEnabledAndTheTickerDrivesTheEngine() {
    runner
        .withPropertyValues("optionsdesk.books.enabled=true")
        .run(
            ctx -> {
              assertThat(ctx).hasSingleBean(MinuteEngine.class);
              ctx.getBean(BooksConfiguration.MinuteTicker.class).tick(); // Saturday: nothing to do
              assertThat(ctx.getBean(MinuteEngine.class).day()).isEmpty();
            });
  }
}
