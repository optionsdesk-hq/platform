package com.optionsdesk.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The commands against a fake API (sample data). */
class OdTest {

  HttpServer server;
  String url;
  final Map<String, String> responses = new ConcurrentHashMap<>();
  final Map<String, Integer> statuses = new ConcurrentHashMap<>();
  final List<String> requests = new CopyOnWriteArrayList<>();
  final ByteArrayOutputStream buf = new ByteArrayOutputStream();

  @BeforeEach
  void start() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        ex -> {
          String path = ex.getRequestURI().getPath();
          String auth = ex.getRequestHeaders().getFirst("Authorization");
          requests.add(
              ex.getRequestMethod()
                  + " "
                  + ex.getRequestURI()
                  + (auth == null ? "" : " [" + auth + "]"));
          byte[] body = responses.getOrDefault(path, "{}").getBytes(StandardCharsets.UTF_8);
          ex.sendResponseHeaders(statuses.getOrDefault(path, 200), body.length);
          ex.getResponseBody().write(body);
          ex.close();
        });
    server.start();
    url = "http://127.0.0.1:" + server.getAddress().getPort();
  }

  @AfterEach
  void stop() {
    server.stop(0);
  }

  int od(Optional<String> token, String... args) {
    return new Od(
            new ApiClient(url + "/", token), new PrintStream(buf, true, StandardCharsets.UTF_8))
        .run(args);
  }

  String out() {
    return buf.toString(StandardCharsets.UTF_8);
  }

  @Test
  void booksAsATable() {
    responses.put(
        "/api/books",
        """
        [{"id":"book-a","state":"ACTIVE","today":"running","openPositions":2,"realisedToday":1234.5},
         {"id":"book-b","state":"PAUSED","today":"not running","openPositions":0,"realisedToday":-80}]
        """);
    assertThat(od(Optional.empty(), "books")).isZero();
    assertThat(out())
        .contains("BOOK    STATE   TODAY        OPEN  Rs TODAY")
        .contains("book-a  ACTIVE  running      2     1,234.50")
        .contains("book-b  PAUSED  not running  0     -80.00");
  }

  @Test
  void noBooksYet() {
    responses.put("/api/books", "[]");
    assertThat(od(Optional.empty(), "books")).isZero();
    assertThat(out()).contains("no books yet");
  }

  @Test
  void positionsWithFilters() {
    responses.put(
        "/api/books/book-a/positions",
        """
        [{"id":7,"status":"CLOSED","credit":5200,"costs":130,"realisedPnl":2470,"exitReason":"exit",
          "legs":[{"symbol":"N24000CE","lots":-1,"entry":100.0}]},
         {"id":8,"status":"OPEN","credit":-650,"costs":20,"realisedPnl":null,"exitReason":null,
          "legs":[{"symbol":"N24500CE","lots":1,"entry":10.0}]}]
        """);
    assertThat(od(Optional.empty(), "positions", "book-a", "--status", "open", "--limit", "5"))
        .isZero();
    assertThat(requests).contains("GET /api/books/book-a/positions?status=open&limit=5");
    assertThat(out())
        .contains("-1 N24000CE@100.00")
        .contains("2,470.00")
        .contains("+1 N24500CE@10.00");
    responses.put("/api/books/book-b/positions", "[]");
    assertThat(od(Optional.empty(), "positions", "book-b")).isZero();
    assertThat(out()).contains("no positions");
  }

  @Test
  void engineOnOrOff() {
    responses.put("/api/engine", "{\"on\":false}");
    od(Optional.empty(), "engine");
    assertThat(out()).contains("engine: OFF");
    responses.put(
        "/api/engine",
        "{\"on\":true,\"session\":\"2026-11-09\",\"finished\":true,\"books\":{\"book-a\":\"running\"},\"problem\":\"09:16 capture failed\"}");
    od(Optional.empty(), "engine");
    assertThat(out())
        .contains("engine: ON, session 2026-11-09 (finished)")
        .contains("book-a: running")
        .contains("last problem: 09:16 capture failed");
    responses.put("/api/engine", "{\"on\":true,\"session\":null,\"books\":{},\"problem\":null}");
    od(Optional.empty(), "engine");
    assertThat(out()).contains("session not open yet");
  }

  @Test
  void writesNeedTheTokenAndSendItOnlyThere() {
    assertThat(od(Optional.empty(), "pause", "book-a")).isEqualTo(3);
    assertThat(out()).contains("set OD_TOKEN");
    assertThat(requests).isEmpty();

    responses.put("/api/books/book-a/pause", "{\"done\":true,\"message\":\"paused\"}");
    assertThat(od(Optional.of("s3cr3t-xyz"), "pause", "book-a")).isZero();
    assertThat(requests).containsExactly("POST /api/books/book-a/pause [Bearer s3cr3t-xyz]");
    assertThat(out()).contains("paused").doesNotContain("s3cr3t-xyz");

    responses.put("/api/books", "[]");
    od(Optional.of("s3cr3t-xyz"), "books");
    assertThat(requests.get(1)).isEqualTo("GET /api/books"); // no token on reads
  }

  @Test
  void exitAndKillNeedYes() {
    assertThat(od(Optional.of("tok"), "exit", "book-a")).isEqualTo(1);
    assertThat(od(Optional.of("tok"), "kill")).isEqualTo(1);
    assertThat(out()).contains("add --yes");
    assertThat(requests).isEmpty();

    responses.put("/api/books/book-a/exit", "{\"done\":true,\"message\":\"exiting now\"}");
    assertThat(od(Optional.of("tok"), "exit", "book-a", "--reason", "too volatile", "--yes"))
        .isZero();
    responses.put("/api/kill", "{\"done\":true,\"message\":\"all paused\"}");
    assertThat(od(Optional.of("tok"), "kill", "--yes")).isZero();
    assertThat(requests)
        .containsExactly(
            "POST /api/books/book-a/exit?reason=too+volatile [Bearer tok]",
            "POST /api/kill?reason=kill+switch [Bearer tok]");
  }

  @Test
  void failuresHaveTheirOwnExitCodes() {
    statuses.put("/api/books/nope/resume", 404);
    responses.put("/api/books/nope/resume", "{\"done\":false,\"message\":\"no book nope\"}");
    assertThat(od(Optional.of("tok"), "resume", "nope")).isEqualTo(4);
    assertThat(out()).contains("no book nope");

    statuses.put("/api/kill", 401);
    responses.put("/api/kill", "{\"message\":\"a valid bearer token is required\"}");
    assertThat(od(Optional.of("wrong"), "kill", "--yes")).isEqualTo(3);

    statuses.put("/api/books", 500);
    assertThat(od(Optional.empty(), "books")).isEqualTo(2);

    statuses.put("/api/books/book-a/pause", 500);
    assertThat(od(Optional.of("tok"), "pause", "book-a")).isEqualTo(2);
    assertThat(out()).contains("nothing may have changed");

    server.stop(0);
    assertThat(od(Optional.empty(), "engine")).isEqualTo(2);
    assertThat(out()).contains("can't reach the API");
  }

  @Test
  void usage() {
    assertThat(od(Optional.empty())).isEqualTo(1);
    assertThat(od(Optional.empty(), "help")).isZero();
    assertThat(od(Optional.empty(), "frobnicate")).isEqualTo(1);
    assertThat(od(Optional.empty(), "positions")).isEqualTo(1);
    assertThat(out())
        .contains("od books")
        .contains("unknown command: frobnicate")
        .contains("which book?");
  }
}
