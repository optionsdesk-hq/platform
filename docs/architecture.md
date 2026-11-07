# Code structure

Two levels: **modules by feature**, then **layers inside each module**.

## 1. Modules (by feature)

Each package directly under `com.optionsdesk.trading` is a Spring Modulith module: a feature with a boundary.
Modules use each other only through the sub-packages listed below (they are declared `@NamedInterface`), never
through anything else, and they may not form cycles. `ModularityTest` checks this on every build.

| Module | Feature |
|---|---|
| `api` | the HTTP API (REST + later SSE) |
| `book` | running books: the minute engine and the interfaces strategy plug-ins implement |
| `execution` | how orders fill (paper broker, leg ordering, unwinds) |
| `journal` | the record: books, positions, legs, orders, fills (Postgres) |
| `kite` | the broker adapter (Zerodha Kite Connect) |
| `ops` | operations: clock, market calendar settings, deploy guard |
| `risk` | pre-trade rules, exits, mark sanity |
| `strategy` | what a strategy reads (market snapshots, option chains) |

Why by feature first: a change to "how orders fill" stays inside `execution`; nothing else can reach into its
internals, and the build fails if something tries. A flat `controllers/ services/ repositories/` split across the
whole app gives no such boundary.

## 2. Layers (inside a module)

| Layer | What goes there | Examples |
|---|---|---|
| `controller` | HTTP endpoints only: parse the request, call a service, map to a DTO | `api.controller.BookController` |
| `dto` | request/response shapes of the API (never journal rows or domain objects directly) | `api.dto.BookView` |
| `service` | the logic: use cases, engines, rules | `book.service.MinuteEngine`, `risk.service.PreTradeRiskEngine` |
| `repository` | database access (SQL through `JdbcClient`) | `journal.repository.PositionJournal` |
| `model` | domain objects and value types (records, no framework) | `execution.model.OrderRequest`, `journal.model.Position` |
| `port` / `spi` | interfaces this module needs (port) or offers to plug-ins (spi) | `execution.port.Broker`, `book.spi.MinuteBook` |
| `adapter` | implementations of another module's port, talking to the outside | `kite.adapter.KiteDayFeed` |
| `client` | low-level clients of external systems | `kite.client.KiteClient` |
| `config` | Spring wiring (`@Configuration`, properties) | `book.config.BooksConfiguration` |

A module only has the layers it needs.

## Rules the build enforces (`ModularityTest`)
- Module boundaries and no cycles (Spring Modulith).
- `@RestController` classes live in `api.controller`.
- `@Repository` classes live in a `repository` layer.
- `@Configuration` classes live in a `config` layer.
- **Controllers never use repositories directly**: controller → service → repository.
- Only the `kite` module talks to the broker.

## What lives elsewhere
Strategy implementations are private (`optionsdesk-hq/strategies`, package `com.optionsdesk.strategies.*`, one
package per strategy). They implement `book.spi` and use `execution`, `journal.port` and `strategy.model`.
