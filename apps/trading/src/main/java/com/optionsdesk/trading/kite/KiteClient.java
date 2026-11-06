package com.optionsdesk.trading.kite;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.optionsdesk.shared.domain.OptionContract;
import com.optionsdesk.shared.domain.Right;
import com.optionsdesk.trading.execution.OrderRequest;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriBuilder;

/** Kite Connect v3 over REST. Thread-safe; requests are spaced to Kite's rate limits. */
public final class KiteClient {

  /**
   * A quote: last trade, best bid/ask (0 = empty side), cumulative day volume, Kite's timestamp.
   */
  public record KiteQuote(
      String instrument, double ltp, double bid, double ask, long volume, Instant at) {}

  /** A historical candle. */
  public record Candle(
      OffsetDateTime at, double open, double high, double low, double close, long volume) {}

  /** An F&O instrument from the daily instruments dump. */
  public record Instrument(long token, OptionContract contract) {}

  /** Basket margin in ₹: initial (before hedges offset) and final (what is blocked). */
  public record Margin(double initial, double required) {}

  /** Session state, from one profile call. */
  public enum TokenState {
    /** Kite accepted today's token. */
    ALIVE,
    /** No token file for today. */
    MISSING,
    /** Kite rejected the token (cancelled or expired). */
    REJECTED,
    /** Kite couldn't be asked (network). */
    UNKNOWN
  }

  static final DateTimeFormatter KITE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
  private static final DateTimeFormatter CANDLE_TIME =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final int MAX_QUOTES = 500;

  private final RestClient http;
  private final String apiKey;
  private final Supplier<Optional<String>> token;
  private final Spacer quoteSpacer;
  private final Spacer historySpacer;

  public KiteClient(
      RestClient.Builder builder,
      KiteProperties props,
      Supplier<Optional<String>> token,
      Clock clock) {
    this.http = builder.baseUrl(props.baseUrl()).defaultHeader("X-Kite-Version", "3").build();
    this.apiKey = props.apiKey();
    this.token = token;
    this.quoteSpacer = new Spacer(props.quoteMinInterval(), clock);
    this.historySpacer = new Spacer(props.historyMinInterval(), clock);
  }

  /** Ask Kite whether today's token works (one profile call). */
  public TokenState tokenState() {
    if (token.get().isEmpty()) {
      return TokenState.MISSING;
    }
    try {
      get(u -> u.path("/user/profile").build());
      return TokenState.ALIVE;
    } catch (KiteException e) {
      return e.kind() == KiteException.Kind.SESSION ? TokenState.REJECTED : TokenState.UNKNOWN;
    }
  }

  /**
   * Quotes for instruments like {@code NFO:NIFTY26N1724600CE}, in batches of 500. Unknown
   * instruments are absent.
   */
  public Map<String, KiteQuote> quotes(List<String> instruments) {
    Map<String, KiteQuote> out = new LinkedHashMap<>();
    for (int i = 0; i < instruments.size(); i += MAX_QUOTES) {
      var batch = instruments.subList(i, Math.min(instruments.size(), i + MAX_QUOTES));
      quoteSpacer.await();
      JsonNode data =
          get(u -> {
                UriBuilder b = u.path("/quote");
                batch.forEach(x -> b.queryParam("i", x));
                return b.build();
              })
              .path("data");
      for (String id : batch) {
        JsonNode q = data.path(id);
        if (q.isMissingNode()) {
          continue;
        }
        String ts = q.path("timestamp").asText("");
        Instant at =
            ts.isEmpty()
                ? Instant.EPOCH
                : LocalDateTime.parse(ts, KITE_TIME).atZone(TokenFile.IST).toInstant();
        out.put(
            id,
            new KiteQuote(
                id,
                q.path("last_price").asDouble(),
                best(q.path("depth").path("buy")),
                best(q.path("depth").path("sell")),
                q.path("volume").asLong(),
                at));
      }
    }
    return out;
  }

  /**
   * Candles for an instrument token; {@code interval} as Kite names it ("minute", "5minute",
   * "60minute", "day").
   */
  public List<Candle> historical(
      long instrumentToken, String interval, LocalDateTime from, LocalDateTime to) {
    historySpacer.await();
    JsonNode rows =
        get(u ->
                u.path("/instruments/historical/{token}/{interval}")
                    .queryParam("from", from.format(KITE_TIME))
                    .queryParam("to", to.format(KITE_TIME))
                    .build(instrumentToken, interval))
            .path("data")
            .path("candles");
    List<Candle> out = new ArrayList<>();
    for (JsonNode r : rows) {
      out.add(
          new Candle(
              OffsetDateTime.parse(r.get(0).asText(), CANDLE_TIME),
              r.get(1).asDouble(),
              r.get(2).asDouble(),
              r.get(3).asDouble(),
              r.get(4).asDouble(),
              r.get(5).asLong()));
    }
    return out;
  }

  /** NFO/BFO index options of {@code underlying} from the instruments dump (CSV). */
  public List<Instrument> options(String exchange, String underlying) {
    String csv =
        call(
            () ->
                http.get()
                    .uri("/instruments/{exchange}", exchange)
                    .headers(h -> h.set("Authorization", auth()))
                    .retrieve()
                    .body(String.class));
    List<Instrument> out = new ArrayList<>();
    String[] lines = csv == null ? new String[0] : csv.split("\n");
    for (int i = 1; i < lines.length; i++) {
      String[] f = lines[i].trim().replace("\"", "").split(",", -1);
      // instrument_token,exchange_token,tradingsymbol,name,last_price,expiry,strike,tick_size,lot_size,instrument_type,segment,exchange
      if (f.length < 12 || !f[3].equals(underlying) || !(f[9].equals("CE") || f[9].equals("PE"))) {
        continue;
      }
      out.add(
          new Instrument(
              Long.parseLong(f[0]),
              new OptionContract(
                  f[2],
                  f[3],
                  LocalDate.parse(f[5]),
                  Double.parseDouble(f[6]),
                  Right.valueOf(f[9]),
                  Integer.parseInt(f[8]))));
    }
    return out;
  }

  /**
   * Margin Kite would block for these orders together (hedges offset), given the account's
   * positions.
   */
  public Margin basketMargin(String exchange, List<OrderRequest> orders) {
    List<Map<String, Object>> body = new ArrayList<>();
    for (OrderRequest o : orders) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("exchange", exchange);
      m.put("tradingsymbol", o.contract().tradingSymbol());
      m.put("transaction_type", o.side().name());
      m.put("variety", "regular");
      m.put("product", "NRML");
      m.put("order_type", "MARKET");
      m.put("quantity", o.quantity());
      m.put("price", 0);
      m.put("trigger_price", 0);
      body.add(m);
    }
    String raw =
        call(
            () ->
                http.post()
                    .uri(
                        u ->
                            u.path("/margins/basket")
                                .queryParam("consider_positions", "true")
                                .queryParam("mode", "compact")
                                .build())
                    .headers(h -> h.set("Authorization", auth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class));
    JsonNode data = read(raw).path("data");
    return new Margin(
        data.path("initial").path("total").asDouble(), data.path("final").path("total").asDouble());
  }

  private static double best(JsonNode side) {
    return side.isArray() && !side.isEmpty() ? side.get(0).path("price").asDouble() : 0;
  }

  private JsonNode get(java.util.function.Function<UriBuilder, java.net.URI> uri) {
    String raw =
        call(
            () ->
                http.get()
                    .uri(uri)
                    .headers(h -> h.set("Authorization", auth()))
                    .retrieve()
                    .body(String.class));
    return read(raw);
  }

  private String auth() {
    String t =
        token
            .get()
            .orElseThrow(
                () -> new KiteException(KiteException.Kind.SESSION, "no Kite token for today"));
    return "token " + apiKey + ":" + t;
  }

  private static String call(Supplier<String> request) {
    try {
      return request.get();
    } catch (RestClientResponseException e) {
      throw fromError(e.getStatusCode(), e.getResponseBodyAsString());
    } catch (ResourceAccessException e) {
      throw new KiteException(
          KiteException.Kind.NETWORK,
          "Kite unreachable: " + e.getMostSpecificCause().getClass().getSimpleName(),
          e);
    }
  }

  static KiteException fromError(HttpStatusCode status, String body) {
    String type = "";
    String message = "";
    try {
      JsonNode n = JSON.readTree(body);
      type = n.path("error_type").asText("");
      message = n.path("message").asText("");
    } catch (IOException e) {
      // not JSON: keep the status only
    }
    // Kite answers 403 for TokenException (session) and PermissionException (not a session problem)
    boolean session = type.equals("TokenException") || (type.isEmpty() && status.value() == 403);
    var kind = session ? KiteException.Kind.SESSION : KiteException.Kind.API;
    return new KiteException(
        kind,
        "Kite "
            + status.value()
            + (type.isEmpty() ? "" : " " + type)
            + (message.isEmpty() ? "" : ": " + message));
  }

  private static JsonNode read(String raw) {
    try {
      return JSON.readTree(raw == null ? "{}" : raw);
    } catch (IOException e) {
      throw new KiteException(KiteException.Kind.NETWORK, "unreadable answer from Kite", e);
    }
  }

  /** Spaces calls at least {@code interval} apart (Kite's per-second limits). */
  static final class Spacer {
    private final Duration interval;
    private final Clock clock;
    private Instant next = Instant.MIN;

    Spacer(Duration interval, Clock clock) {
      this.interval = interval;
      this.clock = clock;
    }

    synchronized void await() {
      if (interval.isZero()) {
        return;
      }
      Instant now = clock.instant();
      if (now.isBefore(next)) {
        try {
          Thread.sleep(Duration.between(now, next));
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new KiteException(
              KiteException.Kind.NETWORK, "interrupted while waiting for the rate limit", e);
        }
        now = next;
      }
      next = now.plus(interval);
    }
  }
}
