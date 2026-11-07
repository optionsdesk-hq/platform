package com.optionsdesk.cli;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The {@code od} commands. Exit codes: 0 ok, 1 usage, 2 API unreachable or failed, 3 refused
 * (token), 4 not found.
 */
final class Od {

  static final String USAGE =
      """
      od - the OptionsDesk console (OD_URL = API address, OD_TOKEN = API token for writes)

        od books                                   every book: state, today, open positions, Rs today
        od positions <book> [--status S] [--limit N]
        od engine                                  today's session and each book's status
        od pause <book>                            no new session from tomorrow
        od resume <book>                           trades from the next session
        od exit <book> [--reason R] --yes          close the strategy now and pause it
        od kill [--reason R] --yes                 every book exits now and is paused
      """;

  private final ApiClient api;
  private final PrintStream out;

  Od(ApiClient api, PrintStream out) {
    this.api = api;
    this.out = out;
  }

  int run(String... args) {
    if (args.length == 0 || args[0].equals("help") || args[0].equals("--help")) {
      out.print(USAGE);
      return args.length == 0 ? 1 : 0;
    }
    Map<String, String> flags = new HashMap<>();
    List<String> words = new ArrayList<>();
    for (int i = 1; i < args.length; i++) {
      if (args[i].equals("--yes")) {
        flags.put("yes", "true");
      } else if (args[i].startsWith("--") && i + 1 < args.length) {
        flags.put(args[i].substring(2), args[++i]);
      } else {
        words.add(args[i]);
      }
    }
    try {
      return switch (args[0]) {
        case "books" -> books();
        case "positions" -> needBook(words) ? positions(words.get(0), flags) : 1;
        case "engine" -> engine();
        case "pause", "resume" ->
            needBook(words)
                ? action(api.post("/api/books/" + words.get(0) + "/" + args[0], Map.of()))
                : 1;
        case "exit" -> needBook(words) && confirmed(flags) ? exit(words.get(0), flags) : 1;
        case "kill" ->
            confirmed(flags) ? action(api.post("/api/kill", reason(flags, "kill switch"))) : 1;
        default -> {
          out.println("unknown command: " + args[0]);
          out.print(USAGE);
          yield 1;
        }
      };
    } catch (ApiClient.Failure f) {
      out.println("error: " + f.getMessage());
      return f.exitCode;
    }
  }

  private int books() {
    JsonNode books = api.get("/api/books", Map.of());
    if (books.isEmpty()) {
      out.println("no books yet (a book appears at its first session)");
      return 0;
    }
    List<List<String>> rows = new ArrayList<>();
    rows.add(List.of("BOOK", "STATE", "TODAY", "OPEN", "Rs TODAY"));
    for (JsonNode b : books) {
      rows.add(
          List.of(
              b.path("id").asText(),
              b.path("state").asText(),
              b.path("today").asText(),
              b.path("openPositions").asText(),
              money(b.path("realisedToday").asDouble())));
    }
    table(rows);
    return 0;
  }

  private int positions(String book, Map<String, String> flags) {
    Map<String, String> q = new LinkedHashMap<>();
    if (flags.containsKey("status")) {
      q.put("status", flags.get("status"));
    }
    q.put("limit", flags.getOrDefault("limit", "20"));
    JsonNode ps = api.get("/api/books/" + book + "/positions", q);
    if (ps.isEmpty()) {
      out.println("no positions");
      return 0;
    }
    List<List<String>> rows = new ArrayList<>();
    rows.add(List.of("ID", "STATUS", "LEGS", "CREDIT", "COSTS", "REALISED", "EXIT"));
    for (JsonNode p : ps) {
      List<String> legs = new ArrayList<>();
      for (JsonNode l : p.path("legs")) {
        legs.add(
            "%+d %s@%.2f"
                .formatted(
                    l.path("lots").asInt(), l.path("symbol").asText(), l.path("entry").asDouble()));
      }
      rows.add(
          List.of(
              p.path("id").asText(),
              p.path("status").asText(),
              String.join(" ", legs),
              money(p.path("credit").asDouble()),
              money(p.path("costs").asDouble()),
              p.path("realisedPnl").isNull() ? "-" : money(p.path("realisedPnl").asDouble()),
              p.path("exitReason").isNull() ? "" : p.path("exitReason").asText()));
    }
    table(rows);
    return 0;
  }

  private int engine() {
    JsonNode e = api.get("/api/engine", Map.of());
    if (!e.path("on").asBoolean()) {
      out.println("engine: OFF");
      return 0;
    }
    out.println(
        "engine: ON, session "
            + (e.path("session").isNull() ? "not open yet" : e.path("session").asText())
            + (e.path("finished").asBoolean() ? " (finished)" : ""));
    e.path("books")
        .fields()
        .forEachRemaining(b -> out.println("  " + b.getKey() + ": " + b.getValue().asText()));
    if (!e.path("problem").isNull()) {
      out.println("last problem: " + e.path("problem").asText());
    }
    return 0;
  }

  private int exit(String book, Map<String, String> flags) {
    return action(api.post("/api/books/" + book + "/exit", reason(flags, "manual exit (od)")));
  }

  private int action(JsonNode r) {
    out.println(r.path("message").asText("done"));
    return r.path("done").asBoolean(true) ? 0 : 4;
  }

  private boolean needBook(List<String> words) {
    if (words.isEmpty()) {
      out.println("which book? e.g. intraday-b1 (see: od books)");
      return false;
    }
    return true;
  }

  private boolean confirmed(Map<String, String> flags) {
    if (!flags.containsKey("yes")) {
      out.println("this closes positions now: add --yes to confirm");
      return false;
    }
    return true;
  }

  private static Map<String, String> reason(Map<String, String> flags, String fallback) {
    return Map.of("reason", flags.getOrDefault("reason", fallback));
  }

  private static String money(double rupees) {
    return "%,.2f".formatted(rupees);
  }

  private void table(List<List<String>> rows) {
    int cols = rows.get(0).size();
    int[] w = new int[cols];
    for (List<String> r : rows) {
      for (int i = 0; i < cols; i++) {
        w[i] = Math.max(w[i], r.get(i).length());
      }
    }
    for (List<String> r : rows) {
      StringBuilder line = new StringBuilder();
      for (int i = 0; i < cols; i++) {
        line.append(String.format("%-" + w[i] + "s", r.get(i)));
        if (i < cols - 1) {
          line.append("  ");
        }
      }
      out.println(line.toString().stripTrailing());
    }
  }
}
