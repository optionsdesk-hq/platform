# ADR 0006: A modular monolith (Spring Modulith) in two deployables, not microservices

- Status: accepted (owner asked for "a proper LLD and modulith or microservice", 2026-11-06)
- Detail: [docs/lld.md](../lld.md)

## Context

The Java/Spring Boot live system (ADR 0005) has about a dozen responsibilities: market data, Kite access, signals,
strategies, risk, books, execution, journal, alerts, API, scheduling, recording, ingestion, data quality. One
developer runs it on one VPS. Opening a position must check risk against the whole book and write the journal
**atomically**. The engine reacts to ticks within seconds.

## Options

| | Microservices (one service per responsibility) | Modular monolith (Spring Modulith) |
|---|---|---|
| Consistency | Risk check + open + journal across services: sagas, retries, partial failures | One transaction |
| Latency | Network hops on the tick path | In-process calls |
| Operations | 8–12 deployables, service discovery, distributed tracing, versioned APIs, more RAM | 2 deployables |
| Kite session | One token shared by many services: a token service and a fan-out | Owned by one module per app |
| Boundaries | Enforced by the network | Enforced by Spring Modulith verification + ArchUnit in CI |
| Later split | Already split | A module that needs it can become a service (events already async) |

## Decision

**A modular monolith built with Spring Modulith, shipped as two deployables from one Gradle repository:**

- **`trading`**: everything that decides and trades: marketdata (quotes for open positions), kite, signals,
  strategy, risk, book, execution, journal, alerts, api, ops.
- **`marketdata`**: everything that records and imports: recorder, ingest (candles, NSE/BSE EOD, FII, breadth),
  dataquality.

Why two and not one: they have different resource profiles and failure modes. A recorder or import job that runs out
of memory (as on 6 Nov) must never take the trading engine down, and the engine must never slow the recorder. They
share libraries (domain, pricing, calendar, kite) and talk only through **PostgreSQL** (journal, job registry, event
outbox) and the **Parquet store**. They never call each other synchronously.

Inside each app, modules talk through **public interfaces** and **domain events** (Spring Modulith's event
publication registry gives a transactional outbox, so an event is never lost if a listener fails). Module internals
are package-private and checked in CI.

## Consequences

- Module boundaries are as strict as services', without the network.
- A module can become its own service later. Likely candidates: alerts or recorder, if they ever need separate scaling.
- One Postgres instance, with a schema per app.
