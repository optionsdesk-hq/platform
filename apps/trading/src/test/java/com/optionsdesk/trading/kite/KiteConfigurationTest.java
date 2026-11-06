package com.optionsdesk.trading.kite;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The adapter exists only when an API key is configured. */
class KiteConfigurationTest {

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(KiteProperties.class)
  static class Base {
    @Bean
    Clock clock() {
      return Clock.systemUTC();
    }
  }

  final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(RestClientAutoConfiguration.class))
          .withUserConfiguration(Base.class, KiteConfiguration.class);

  @Test
  void noKeyNoAdapter() {
    runner.run(ctx -> assertThat(ctx).doesNotHaveBean(KiteClient.class));
    runner
        .withPropertyValues("optionsdesk.kite.api-key=")
        .run(ctx -> assertThat(ctx).doesNotHaveBean(KiteClient.class));
  }

  @Test
  void aKeyWiresTheClientAndTheQuoteSource() {
    runner
        .withPropertyValues(
            "optionsdesk.kite.api-key=k", "optionsdesk.kite.token-path=/nonexistent/token.json")
        .run(
            ctx -> {
              assertThat(ctx).hasSingleBean(KiteClient.class).hasSingleBean(KiteQuoteSource.class);
              assertThat(ctx.getBean(KiteClient.class).tokenState())
                  .isEqualTo(KiteClient.TokenState.MISSING);
            });
  }
}
