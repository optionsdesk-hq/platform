package com.optionsdesk.trading.alerts.client;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TelegramClientTest {

  final RestClient.Builder builder = RestClient.builder();
  final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
  final TelegramClient client = new TelegramClient(builder, "https://tg.test", "123:SECRET", "42");

  @Test
  void sendsTheMessageToTheChat() {
    server
        .expect(requestTo("https://tg.test/bot123:SECRET/sendMessage"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().string(containsString("\"chat_id\":\"42\"")))
        .andExpect(content().string(containsString("\"text\":\"stop hit\"")))
        .andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));
    client.send("stop hit");
    server.verify();
  }

  @Test
  void aRefusalIsReportedWithoutTheToken() {
    server
        .expect(requestTo("https://tg.test/bot123:SECRET/sendMessage"))
        .andRespond(withBadRequest().body("{\"ok\":false,\"description\":\"chat not found\"}"));
    assertThatThrownBy(() -> client.send("x"))
        .isInstanceOf(TelegramClient.SendFailed.class)
        .hasMessageNotContaining("SECRET");
  }
}
