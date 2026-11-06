package com.optionsdesk.trading.kite;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Creates the adapter only when an API key is configured; without one the app runs with no broker
 * connection.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnExpression("!'${optionsdesk.kite.api-key:}'.isBlank()")
class KiteConfiguration {

  @Bean
  TokenFile kiteTokenFile(KiteProperties props, Clock clock) {
    return new TokenFile(props.tokenPath(), clock);
  }

  @Bean
  KiteClient kiteClient(
      RestClient.Builder builder, KiteProperties props, TokenFile tokens, Clock clock) {
    return new KiteClient(builder, props, tokens::today, clock);
  }

  @Bean
  KiteQuoteSource kiteQuoteSource(KiteClient kite) {
    return new KiteQuoteSource(kite, "NFO");
  }
}
