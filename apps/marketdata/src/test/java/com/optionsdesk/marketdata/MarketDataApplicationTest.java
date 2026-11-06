package com.optionsdesk.marketdata;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;

@AutoConfigureObservability // tests disable metrics export by default; we test the endpoint
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MarketDataApplicationTest {

  @Autowired TestRestTemplate http;

  @Test
  void healthAndMetrics() {
    assertThat(http.getForObject("/actuator/health", String.class)).contains("\"status\":\"UP\"");
    assertThat(http.getForObject("/actuator/prometheus", String.class))
        .contains("app=\"marketdata\"");
  }
}
