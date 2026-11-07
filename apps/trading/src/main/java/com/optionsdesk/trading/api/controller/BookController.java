package com.optionsdesk.trading.api.controller;

import com.optionsdesk.trading.api.dto.ApiViews.ActionView;
import com.optionsdesk.trading.api.dto.ApiViews.BookView;
import com.optionsdesk.trading.api.dto.ApiViews.EngineView;
import com.optionsdesk.trading.api.dto.ApiViews.PositionView;
import com.optionsdesk.trading.book.service.BookService;
import java.util.List;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Books: see them, and control them. Reads are open (Tailscale only); writes need the API token
 * (see {@code ApiTokenFilter}). A controller only translates HTTP: the rules are in {@link
 * BookService}.
 */
@RestController
@RequestMapping("/api")
class BookController {

  private final BookService service;

  BookController(BookService service) {
    this.service = service;
  }

  @GetMapping("/books")
  List<BookView> books() {
    return service.books().stream().map(BookView::of).toList();
  }

  @GetMapping("/books/{id}/positions")
  ResponseEntity<List<PositionView>> positions(
      @PathVariable String id,
      @RequestParam Optional<String> status,
      @RequestParam(defaultValue = "50") int limit) {
    return service
        .positions(id, status, limit)
        .map(ps -> ResponseEntity.ok(ps.stream().map(PositionView::of).toList()))
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @GetMapping("/engine")
  EngineView engine() {
    return service.engine().map(EngineView::of).orElseGet(EngineView::off);
  }

  @PostMapping("/books/{id}/pause")
  ResponseEntity<ActionView> pause(@PathVariable String id) {
    return answer(service.pause(id));
  }

  @PostMapping("/books/{id}/resume")
  ResponseEntity<ActionView> resume(@PathVariable String id) {
    return answer(service.resume(id));
  }

  /** Exit the whole strategy now: close its open legs, stop it today, pause it. */
  @PostMapping("/books/{id}/exit")
  ResponseEntity<ActionView> exit(
      @PathVariable String id, @RequestParam(defaultValue = "manual exit") String reason) {
    return answer(service.exit(id, reason));
  }

  /** The kill switch: every book exits now and is paused. */
  @PostMapping("/kill")
  ResponseEntity<ActionView> kill(@RequestParam(defaultValue = "kill switch") String reason) {
    return answer(service.killAll(reason));
  }

  private static ResponseEntity<ActionView> answer(BookService.ActionOutcome o) {
    return o.done()
        ? ResponseEntity.ok(ActionView.of(o))
        : ResponseEntity.status(404).body(ActionView.of(o));
  }
}
