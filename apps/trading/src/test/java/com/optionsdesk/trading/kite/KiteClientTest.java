package com.optionsdesk.trading.kite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.optionsdesk.shared.domain.OptionContract;
import com.optionsdesk.shared.domain.Right;
import com.optionsdesk.trading.execution.OrderRequest;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KiteClientTest {

  static final String TOKEN = "t0k3n-secret";
  static final KiteProperties PROPS =
      new KiteProperties("https://kite.test", "key1", null, Duration.ZERO, Duration.ZERO);

  final RestClient.Builder builder = RestClient.builder();
  final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
  final AtomicReference<Optional<String>> token = new AtomicReference<>(Optional.of(TOKEN));
  final KiteClient kite = new KiteClient(builder, PROPS, token::get, Clock.systemUTC());

  static final String TOKEN_ERROR =
      """
      {"status":"error","message":"Incorrect `api_key` or `access_token`.","error_type":"TokenException"}
      """;

  @Test
  void aWorkingTokenIsAlive() {
    server
        .expect(requestTo("https://kite.test/user/profile"))
        .andExpect(header("Authorization", "token key1:" + TOKEN))
        .andExpect(header("X-Kite-Version", "3"))
        .andRespond(
            withSuccess("{\"status\":\"success\",\"data\":{}}", MediaType.APPLICATION_JSON));
    assertThat(kite.tokenState()).isEqualTo(KiteClient.TokenState.ALIVE);
    server.verify();
  }

  @Test
  void aCancelledTokenIsRejectedAndTheMessageHasNoSecrets() {
    server
        .expect(requestTo("https://kite.test/user/profile"))
        .andRespond(
            withStatus(HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_JSON)
                .body(TOKEN_ERROR));
    assertThat(kite.tokenState()).isEqualTo(KiteClient.TokenState.REJECTED);

    server.reset();
    server
        .expect(requestTo(containsString("/quote")))
        .andRespond(
            withStatus(HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_JSON)
                .body(TOKEN_ERROR));
    assertThatThrownBy(() -> kite.quotes(List.of("NFO:X")))
        .isInstanceOfSatisfying(
            KiteException.class, e -> assertThat(e.kind()).isEqualTo(KiteException.Kind.SESSION))
        .hasMessageContaining("TokenException")
        .hasMessageNotContaining(TOKEN)
        .hasMessageNotContaining("key1");
  }

  @Test
  void noTokenFileMeansMissingWithoutAskingKite() {
    token.set(Optional.empty());
    assertThat(kite.tokenState()).isEqualTo(KiteClient.TokenState.MISSING);
    assertThatThrownBy(() -> kite.quotes(List.of("NFO:X")))
        .isInstanceOfSatisfying(
            KiteException.class, e -> assertThat(e.kind()).isEqualTo(KiteException.Kind.SESSION));
    server.verify();
  }

  @Test
  void aPermissionErrorOrOutageIsNotASessionProblem() {
    server
        .expect(requestTo("https://kite.test/user/profile"))
        .andRespond(
            withStatus(HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_JSON)
                .body(
                    "{\"status\":\"error\",\"message\":\"no\",\"error_type\":\"PermissionException\"}"));
    assertThat(kite.tokenState()).isEqualTo(KiteClient.TokenState.UNKNOWN);
    server.reset();
    server
        .expect(requestTo("https://kite.test/user/profile"))
        .andRespond(withException(new IOException("down")));
    assertThat(kite.tokenState()).isEqualTo(KiteClient.TokenState.UNKNOWN);
    server.reset();
    server
        .expect(requestTo("https://kite.test/user/profile"))
        .andRespond(withStatus(HttpStatus.BAD_GATEWAY).body("<html>"));
    assertThat(kite.tokenState()).isEqualTo(KiteClient.TokenState.UNKNOWN); // not JSON: status only
    server.reset();
    server
        .expect(requestTo(containsString("/instruments/historical/")))
        .andRespond(
            withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .contentType(MediaType.APPLICATION_JSON)
                .body(
                    "{\"status\":\"error\",\"message\":\"Too many requests\",\"error_type\":\"NetworkException\"}"));
    assertThatThrownBy(() -> kite.historical(1, "day", LocalDateTime.now(), LocalDateTime.now()))
        .isInstanceOfSatisfying(
            KiteException.class, e -> assertThat(e.kind()).isEqualTo(KiteException.Kind.API))
        .hasMessage("Kite 429 NetworkException: Too many requests");
  }

  @Test
  void quotesKeepBestBidAskVolumeAndKitesTimestamp() {
    server
        .expect(requestTo(allOf(containsString("i=NFO:A"), containsString("i=NFO:B"))))
        .andRespond(
            withSuccess(
                """
                {"status":"success","data":{"NFO:A":{"instrument_token":1,"timestamp":"2026-11-06 15:29:59",
                  "last_price":101.5,"volume":1200,
                  "depth":{"buy":[{"price":101.4,"quantity":65},{"price":101.3}],"sell":[{"price":101.6}]}},
                  "NFO:B":{"timestamp":"2026-11-06 15:29:58","last_price":7,"volume":0,
                  "depth":{"buy":[],"sell":[]}}}}
                """,
                MediaType.APPLICATION_JSON));
    var q = kite.quotes(List.of("NFO:A", "NFO:B", "NFO:GONE"));
    assertThat(q).containsOnlyKeys("NFO:A", "NFO:B");
    var a = q.get("NFO:A");
    assertThat(a.bid()).isEqualTo(101.4);
    assertThat(a.ask()).isEqualTo(101.6);
    assertThat(a.volume()).isEqualTo(1200);
    assertThat(a.at()).isEqualTo(Instant.parse("2026-11-06T09:59:59Z")); // 15:29:59 IST
    assertThat(q.get("NFO:B").bid()).isZero();
  }

  @Test
  void historicalCandles() {
    server
        .expect(
            requestTo(
                allOf(
                    containsString("/instruments/historical/256265/minute"),
                    containsString("from=2026-11-06"))))
        .andRespond(
            withSuccess(
                """
                {"status":"success","data":{"candles":[["2026-11-06T09:15:00+0530",24500,24510.5,24490,24505,0]]}}
                """,
                MediaType.APPLICATION_JSON));
    var c =
        kite.historical(
            256265,
            "minute",
            LocalDateTime.of(2026, 11, 6, 9, 15),
            LocalDateTime.of(2026, 11, 6, 15, 30));
    assertThat(c).hasSize(1);
    assertThat(c.get(0).at().toInstant()).isEqualTo(Instant.parse("2026-11-06T03:45:00Z"));
    assertThat(c.get(0).high()).isEqualTo(24510.5);
  }

  @Test
  void instrumentsKeepTheUnderlyingsOptions() {
    server
        .expect(requestTo("https://kite.test/instruments/NFO"))
        .andRespond(
            withSuccess(
                """
                instrument_token,exchange_token,tradingsymbol,name,last_price,expiry,strike,tick_size,lot_size,instrument_type,segment,exchange
                1,2,NIFTY26N1724600CE,"NIFTY",0,2026-11-17,24600,0.05,65,CE,NFO-OPT,NFO
                3,4,NIFTY26NOVFUT,"NIFTY",0,2026-11-24,0,0.1,65,FUT,NFO-FUT,NFO
                5,6,BANKNIFTY26NOV50000PE,"BANKNIFTY",0,2026-11-24,50000,0.05,30,PE,NFO-OPT,NFO
                """,
                MediaType.TEXT_PLAIN));
    var opts = kite.options("NFO", "NIFTY");
    assertThat(opts)
        .containsExactly(
            new KiteClient.Instrument(
                1,
                new OptionContract(
                    "NIFTY26N1724600CE",
                    "NIFTY",
                    LocalDate.of(2026, 11, 17),
                    24600,
                    Right.CE,
                    65)));
  }

  @Test
  void basketMarginSendsTheOrdersTogether() {
    var c =
        new OptionContract(
            "NIFTY26N1724500PE", "NIFTY", LocalDate.of(2026, 11, 17), 24500, Right.PE, 65);
    server
        .expect(requestTo(containsString("/margins/basket?consider_positions=true")))
        .andExpect(method(HttpMethod.POST))
        .andExpect(
            content()
                .string(
                    allOf(
                        containsString("\"transaction_type\":\"SELL\""),
                        containsString("\"quantity\":130"))))
        .andRespond(
            withSuccess(
                "{\"status\":\"success\",\"data\":{\"initial\":{\"total\":150000.5},\"final\":{\"total\":38000}}}",
                MediaType.APPLICATION_JSON));
    var m =
        kite.basketMargin("NFO", List.of(new OrderRequest(c, OrderRequest.Side.SELL, 130, "x")));
    assertThat(m).isEqualTo(new KiteClient.Margin(150000.5, 38000));
  }

  @Test
  void callsAreSpacedToTheRateLimit() {
    var spacer = new KiteClient.Spacer(Duration.ofMillis(60), Clock.systemUTC());
    long t0 = System.nanoTime();
    spacer.await();
    spacer.await();
    spacer.await();
    assertThat(Duration.ofNanos(System.nanoTime() - t0))
        .isGreaterThanOrEqualTo(Duration.ofMillis(110));
  }

  @Test
  void quoteSourceFallsBackToEmptyOnAnyKiteFailure() {
    var c =
        new OptionContract(
            "NIFTY26N1724600CE", "NIFTY", LocalDate.of(2026, 11, 17), 24600, Right.CE, 65);
    server
        .expect(requestTo(containsString("i=NFO:NIFTY26N1724600CE")))
        .andRespond(
            withSuccess(
                "{\"data\":{\"NFO:NIFTY26N1724600CE\":{\"timestamp\":\"2026-11-06 10:00:00\",\"last_price\":5,"
                    + "\"depth\":{\"buy\":[{\"price\":4.95}],\"sell\":[{\"price\":5.05}]}}}}",
                MediaType.APPLICATION_JSON));
    server
        .expect(requestTo(containsString("/quote")))
        .andRespond(withException(new IOException("down")));
    var source = new KiteQuoteSource(kite, "NFO");
    assertThat(source.latest(c)).hasValueSatisfying(q -> assertThat(q.bid()).isEqualTo(4.95));
    assertThat(source.latest(c)).isEmpty();
  }

  @Test
  void propertiesNeverPrintTheKey() {
    assertThat(PROPS.toString()).doesNotContain("key1").contains("apiKey=set");
    assertThat(new KiteProperties(null, "", null, null, null).toString()).contains("apiKey=unset");
  }
}
