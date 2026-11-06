package com.optionsdesk.trading;

import static org.assertj.core.api.Assertions.assertThat;

import com.optionsdesk.shared.calendar.MarketCalendar;
import java.time.Clock;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Starts the whole app on a random port: health, metrics and the deploy guard over real HTTP. */
@AutoConfigureObservability // tests disable metrics export by default; we test the endpoint
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTestConfiguration.class)
@Testcontainers(
    disabledWithoutDocker = true) // the app needs its database; runs wherever Docker is (CI)
class TradingApplicationTest {

  /** Friday 6 Nov 2026, 12:00 IST: inside market hours, so deploys must be refused. */
  @TestConfiguration
  static class MarketHoursClock {
    @Bean
    @Primary
    Clock fixedClock() {
      var noon = LocalDateTime.parse("2026-11-06T12:00").atZone(MarketCalendar.IST).toInstant();
      return Clock.fixed(noon, MarketCalendar.IST);
    }
  }

  @Autowired TestRestTemplate http;

  @Test
  void healthIsUp() {
    assertThat(http.getForObject("/actuator/health", String.class)).contains("\"status\":\"UP\"");
    assertThat(http.getForObject("/actuator/health/liveness", String.class)).contains("UP");
  }

  @Test
  void prometheusMetricsAreExposed() {
    assertThat(http.getForObject("/actuator/prometheus", String.class))
        .contains("jvm_memory_used_bytes");
  }

  @Test
  void deployIsRefusedDuringMarketHours() {
    var response = http.getForEntity("/ops/deployable", String.class);
    assertThat(response.getStatusCode().value()).isEqualTo(409);
    assertThat(response.getBody()).contains("\"deployable\":false").contains("market hours");
  }
}
