package com.optionsdesk.trading.kite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.optionsdesk.shared.domain.Right;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Sample instruments and prices (made up). */
class KiteDayFeedTest {

  static final String INDEX = "NSE:NIFTY 50";
  static final KiteProperties PROPS =
      new KiteProperties("https://kite.test", "k", null, Duration.ZERO, Duration.ZERO);

  final RestClient.Builder builder = RestClient.builder();
  final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
  final KiteClient kite = new KiteClient(builder, PROPS, () -> Optional.of("t"), Clock.systemUTC());

  static final String INSTRUMENTS =
      """
      instrument_token,exchange_token,tradingsymbol,name,last_price,expiry,strike,tick_size,lot_size,instrument_type,segment,exchange
      1,1,N9_20000CE,"NIFTY",0,2026-11-09,20000,0.05,65,CE,NFO-OPT,NFO
      2,2,N9_20000PE,"NIFTY",0,2026-11-09,20000,0.05,65,PE,NFO-OPT,NFO
      3,3,N9_22000CE,"NIFTY",0,2026-11-09,22000,0.05,65,CE,NFO-OPT,NFO
      4,4,N17_20000CE,"NIFTY",0,2026-11-17,20000,0.05,65,CE,NFO-OPT,NFO
      5,5,N3_20000CE,"NIFTY",0,2026-11-03,20000,0.05,65,CE,NFO-OPT,NFO
      """;

  static String quote(String id, double ltp, double bid, double ask) {
    return "\"%s\":{\"timestamp\":\"2026-11-09 09:50:00\",\"last_price\":%s,\"volume\":10,\"depth\":{\"buy\":[{\"price\":%s}],\"sell\":[{\"price\":%s}]}}"
        .formatted(id, ltp, bid, ask);
  }

  /** The two requests of opening a feed (expectations must all be declared before any request). */
  void expectOpen() {
    server
        .expect(requestTo(containsString("/quote")))
        .andRespond(
            withSuccess(
                "{\"data\":{" + quote(INDEX, 20_010, 0, 0) + "}}", MediaType.APPLICATION_JSON));
    server
        .expect(requestTo("https://kite.test/instruments/NFO"))
        .andRespond(withSuccess(INSTRUMENTS, MediaType.TEXT_PLAIN));
  }

  KiteDayFeed newFeed() {
    return KiteDayFeed.open(kite, LocalDate.of(2026, 11, 9), INDEX, "NFO", "NIFTY", 1_000);
  }

  KiteDayFeed open() {
    expectOpen();
    return newFeed();
  }

  @Test
  void quotesTheNearestExpirysStrikesNearTheIndex() {
    var feed = open();
    assertThat(feed.chainSize())
        .isEqualTo(2); // 22000 is too far; 17 Nov is later; 3 Nov has expired
    assertThat(feed.exchange()).isEqualTo("NFO");
    assertThat(feed.contracts().find(20_000, Right.CE))
        .hasValueSatisfying(c -> assertThat(c.tradingSymbol()).isEqualTo("N9_20000CE"));
    assertThat(feed.contracts().find(22_000, Right.CE)).isEmpty();
  }

  @Test
  void eachCaptureIsOneRequestAndBuildsTheMinuteHistory() {
    expectOpen();
    server
        .expect(
            requestTo(
                allOf(
                    containsString("NFO:N9_20000CE"),
                    containsString("NFO:N9_20000PE"),
                    not(containsString("22000")))))
        .andRespond(
            withSuccess(
                "{\"data\":{"
                    + quote(INDEX, 20_020, 0, 0)
                    + ","
                    + quote("NFO:N9_20000CE", 101, 100.5, 101.5)
                    + "}}",
                MediaType.APPLICATION_JSON));
    var feed = newFeed();
    feed.capture(LocalTime.of(9, 50));
    assertThat(feed.minutes()).containsExactly(LocalTime.of(9, 50));
    assertThat(feed.spot(LocalTime.of(9, 50))).hasValue(20_020);
    assertThat(feed.quote(20_000, Right.CE, LocalTime.of(9, 55)))
        .hasValueSatisfying(q -> assertThat(q.bid()).isEqualTo(100.5));
    assertThat(feed.quote(20_000, Right.PE, LocalTime.of(9, 55))).isEmpty(); // no quote came for it
    assertThat(feed.spot(LocalTime.of(9, 51))).isEmpty();
  }

  @Test
  void aMinuteWithoutTheIndexIsNotRecorded() {
    expectOpen();
    server
        .expect(requestTo(containsString("/quote")))
        .andRespond(
            withSuccess(
                "{\"data\":{" + quote("NFO:N9_20000CE", 101, 100.5, 101.5) + "}}",
                MediaType.APPLICATION_JSON));
    var feed = newFeed();
    assertThatThrownBy(() -> feed.capture(LocalTime.of(9, 50)))
        .hasMessageContaining("no index level");
    assertThat(feed.minutes()).isEmpty();
  }

  @Test
  void noIndexOrNoExpiryMeansNoFeed() {
    server
        .expect(requestTo(containsString("/quote")))
        .andRespond(withSuccess("{\"data\":{}}", MediaType.APPLICATION_JSON));
    assertThatThrownBy(
            () -> KiteDayFeed.open(kite, LocalDate.of(2026, 11, 9), INDEX, "NFO", "NIFTY", 1_000))
        .hasMessageContaining("no level");
    server.reset();
    server
        .expect(requestTo(containsString("/quote")))
        .andRespond(
            withSuccess(
                "{\"data\":{" + quote(INDEX, 20_010, 0, 0) + "}}", MediaType.APPLICATION_JSON));
    server
        .expect(requestTo("https://kite.test/instruments/NFO"))
        .andRespond(withSuccess(INSTRUMENTS, MediaType.TEXT_PLAIN));
    assertThatThrownBy(
            () -> KiteDayFeed.open(kite, LocalDate.of(2026, 12, 1), INDEX, "NFO", "NIFTY", 1_000))
        .hasMessageContaining("no NIFTY expiry");
  }
}
