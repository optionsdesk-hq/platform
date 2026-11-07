package com.optionsdesk.trading.alerts.config;

import com.optionsdesk.trading.alerts.client.TelegramClient;
import com.optionsdesk.trading.alerts.port.Alerts;
import com.optionsdesk.trading.alerts.service.QueuedAlerts;
import java.time.Duration;
import java.util.List;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Telegram when {@code TELEGRAM_BOT_TOKEN} and {@code TELEGRAM_CHAT_ID} are set; otherwise alerts
 * are only logged. Retries after 2 s, 10 s and 60 s; {@code optionsdesk.alerts.label} (e.g.
 * "staging") prefixes every message.
 */
@Configuration(proxyBeanMethods = false)
class AlertsConfiguration {

  @Bean(destroyMethod = "close")
  QueuedAlerts alerts(
      RestClient.Builder builder,
      @Value("${optionsdesk.alerts.telegram-url:https://api.telegram.org}") String url,
      @Value("${optionsdesk.alerts.telegram-token:}") String token,
      @Value("${optionsdesk.alerts.telegram-chat:}") String chat,
      @Value("${optionsdesk.alerts.label:}") String label) {
    List<Duration> backoff =
        List.of(Duration.ofSeconds(2), Duration.ofSeconds(10), Duration.ofSeconds(60));
    if (token.isBlank() || chat.isBlank()) {
      LoggerFactory.getLogger(Alerts.class).info("alerts: Telegram not configured, logging only");
      return new QueuedAlerts(text -> {}, backoff, label, 500);
    }
    var telegram = new TelegramClient(builder, url, token, chat);
    return new QueuedAlerts(telegram::send, backoff, label, 500);
  }
}
