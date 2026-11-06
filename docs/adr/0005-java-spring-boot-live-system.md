# ADR 0005: The platform moves to Java and Spring Boot; one language for live, backtests and research

- Status: accepted (owner, 2026-11-06); revised the same day (owner: "I don't know Python")
- Supersedes: ADR 0004 (Python everywhere)

## Context

The owner wants a proper software-engineering system (CI/CD, environments, monitoring), built **Java and Spring
Boot first**. They are using the project to grow as a software engineer, review the code ("I build, you review"),
and don't know Python. The current system is Python (engine, risk, data pipeline, research). It works in production
and has 522 tests, but its CI has never run (no GitHub remote), nothing collects its metrics, and every change goes
straight to `main`.

## Decision

- **Java 21 + Spring Boot 3 for everything new:**
  - **the live system:** engine, risk, strategies, paper and live brokers, Kite adapter (Zerodha's official Java
    SDK), API, schedulers, recorder, data sync and imports;
  - **backtests:** the **same strategy and risk code** replays recorded data through the engine (replay mode in
    `docs/lld.md`). One implementation, so backtest and live can never drift apart;
  - **research:** **SQL on DuckDB** directly over the Parquet store, plus small Java programs and generated reports
    or charts where SQL isn't enough.
- **No new Python.** The existing Python system:
  - keeps running production unchanged until the Java engine replaces it (shadow run, then cutover);
  - generates **golden test vectors once**, so the Java port can be shown to compute the same results (pricing,
    risk rules, exits, mark sanity, strategies, charges);
  - is archived after the cutover. Its research findings are already recorded (AGENTS.md, Linear, Notion).
- The Parquet market-data store keeps its current layout. The journal moves to **PostgreSQL** (Flyway).
- Architecture: modular monolith (ADR 0006). Repositories: decided separately (monorepo vs split, by plan).

## Consequences

- One language and one toolchain (Gradle) to learn, review and maintain.
- Several weeks before the Java engine trades paper; the 6-week paper clock for the v0.21 books starts at its first
  deploy. Until then, evidence keeps accruing from the Python system (daily STBT signal, straddle replays).
- Architecture rules stay enforced (ArchUnit + Spring Modulith replace `test_architecture.py`): only the Kite
  adapter may use the Kite SDK; the shared kernel depends on nothing.
