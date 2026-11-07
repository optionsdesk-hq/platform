package com.optionsdesk.trading.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.optionsdesk.trading.book.service.BookService;
import com.optionsdesk.trading.book.service.BookService.ActionOutcome;
import com.optionsdesk.trading.book.service.BookService.BookStatus;
import com.optionsdesk.trading.journal.model.Book;
import com.optionsdesk.trading.journal.model.Position;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** HTTP in and out, and the write-token rule; the service is mocked (its own test covers it). */
@WebMvcTest(BookController.class)
@Import(com.optionsdesk.trading.api.config.ApiTokenFilterTestConfig.class)
@TestPropertySource(properties = "optionsdesk.api.token=s3cret")
class BookControllerTest {

  @Autowired MockMvc mvc;
  @MockBean BookService service;

  static final Book B1 = new Book("book-a", "intraday-straddle", "PAPER", 1, "ACTIVE");

  @Test
  void listsBooksWithTheirDay() throws Exception {
    when(service.books()).thenReturn(List.of(new BookStatus(B1, "running", 2, 1234.567)));
    mvc.perform(get("/api/books"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value("book-a"))
        .andExpect(jsonPath("$[0].today").value("running"))
        .andExpect(jsonPath("$[0].openPositions").value(2))
        .andExpect(jsonPath("$[0].realisedToday").value(1234.57));
  }

  @Test
  void positionsOfABookOr404() throws Exception {
    when(service.positions(eq("book-a"), any(), eq(50)))
        .thenReturn(
            Optional.of(
                List.of(
                    new Position(
                        7,
                        "book-a",
                        "CLOSED",
                        List.of(),
                        5200,
                        130,
                        Optional.of(2470.0),
                        Optional.of("exit")))));
    mvc.perform(get("/api/books/book-a/positions"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].realisedPnl").value(2470.0))
        .andExpect(jsonPath("$[0].exitReason").value("exit"));
    when(service.positions(eq("nope"), any(), any(Integer.class))).thenReturn(Optional.empty());
    mvc.perform(get("/api/books/nope/positions")).andExpect(status().isNotFound());
  }

  @Test
  void engineOnOrOff() throws Exception {
    when(service.engine()).thenReturn(Optional.empty());
    mvc.perform(get("/api/engine")).andExpect(jsonPath("$.on").value(false));
    when(service.engine())
        .thenReturn(
            Optional.of(
                new BookService.EngineStatus(
                    Optional.of(LocalDate.of(2026, 11, 9)),
                    false,
                    Map.of("book-a", "running"),
                    Optional.empty())));
    mvc.perform(get("/api/engine"))
        .andExpect(jsonPath("$.on").value(true))
        .andExpect(jsonPath("$.session").value("2026-11-09"))
        .andExpect(jsonPath("$.books.book-a").value("running"));
  }

  @Test
  void writesNeedTheToken() throws Exception {
    mvc.perform(post("/api/books/book-a/pause")).andExpect(status().isUnauthorized());
    mvc.perform(post("/api/kill").header("Authorization", "Bearer wrong"))
        .andExpect(status().isUnauthorized());
    verify(service, never()).killAll(any());

    when(service.pause("book-a")).thenReturn(new ActionOutcome(true, "paused"));
    mvc.perform(post("/api/books/book-a/pause").header("Authorization", "Bearer s3cret"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("paused"));
  }

  @Test
  void controlsMapToTheService() throws Exception {
    when(service.resume("book-a")).thenReturn(new ActionOutcome(true, "active"));
    when(service.exit("book-a", "too volatile")).thenReturn(new ActionOutcome(true, "exiting"));
    when(service.exit("nope", "manual exit")).thenReturn(new ActionOutcome(false, "no book nope"));
    when(service.killAll("kill switch")).thenReturn(new ActionOutcome(true, "all paused"));
    var auth = "Bearer s3cret";
    mvc.perform(post("/api/books/book-a/resume").header("Authorization", auth))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/books/book-a/exit")
                .param("reason", "too volatile")
                .header("Authorization", auth))
        .andExpect(jsonPath("$.message").value("exiting"));
    mvc.perform(post("/api/books/nope/exit").header("Authorization", auth))
        .andExpect(status().isNotFound());
    mvc.perform(post("/api/kill").header("Authorization", auth))
        .andExpect(jsonPath("$.message").value("all paused"));
  }
}
