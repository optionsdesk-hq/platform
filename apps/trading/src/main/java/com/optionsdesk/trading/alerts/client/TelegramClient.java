package com.optionsdesk.trading.alerts.client;

import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Sends one Telegram message (Bot API {@code sendMessage}). The bot token is part of the URL, so
 * errors are reported without it.
 */
public class TelegramClient {

  private final RestClient http;
  private final String token;
  private final String chatId;

  public TelegramClient(RestClient.Builder builder, String baseUrl, String token, String chatId) {
    this.http = builder.baseUrl(baseUrl).build();
    this.token = token;
    this.chatId = chatId;
  }

  /** Sends {@code text}; throws {@link SendFailed} (without the token) when Telegram refuses. */
  public void send(String text) {
    try {
      http.post()
          // literal path: a URI template would encode the token's ":" as %3A
          .uri("/bot" + token + "/sendMessage")
          .contentType(MediaType.APPLICATION_JSON)
          .body(Map.of("chat_id", chatId, "text", text, "disable_web_page_preview", true))
          .retrieve()
          .toBodilessEntity();
    } catch (RestClientException e) {
      throw new SendFailed(e.getClass().getSimpleName() + ": " + scrub(e.getMessage()));
    }
  }

  private String scrub(String s) {
    return s == null ? "" : s.replace(token, "<token>");
  }

  /** Telegram didn't take the message. */
  public static final class SendFailed extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public SendFailed(String message) {
      super(message);
    }
  }
}
