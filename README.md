# OptionsDesk platform

A rule-driven trading platform for NSE index options (Zerodha Kite), written in **Java 21 / Spring Boot 3** as a
**modular monolith** (Spring Modulith) shipped as two apps. Paper trading only until it earns live trading; live
order placement is gated off.

| App | Does | Modules (now → planned) |
|---|---|---|
| `apps/trading` | decides and trades | `ops`, `api` → marketdata, kite, signals, strategy, risk, book, execution, journal, alerts |
| `apps/marketdata` | records and imports | → recorder, ingest, dataquality |
| `libs/shared-kernel` | pure Java used by both | domain (contracts, quotes, legs), pricing (Black–Scholes), calendar (NSE sessions) |

Why this shape: [ADR 0005](docs/adr/0005-java-spring-boot-live-system.md) (Java),
[ADR 0006](docs/adr/0006-modular-monolith.md) (modular monolith, two deployables). Strategy parameters, research
and environment configuration live in private repositories.

## Build and run

```bash
./gradlew build              # format check, compile (warnings are errors), tests, architecture checks, 85% coverage gate
./gradlew spotlessApply      # fix formatting
./gradlew :apps:trading:bootRun
curl localhost:8080/actuator/health
curl localhost:8080/ops/deployable    # 409 during market hours: the CD pipeline asks before every deploy
```

Needs JDK 21 (Gradle downloads one if missing). Images: `ghcr.io/optionsdesk-hq/trading`, `.../marketdata`,
built by CI on `main` with Jib.

## How the code is checked

| Check | Tool | Where |
|---|---|---|
| Formatting | Spotless (google-java-format) | `./gradlew spotlessCheck` |
| Compiler warnings fail the build | `-Xlint:all -Werror` | every module |
| Unit + property tests | JUnit 5, AssertJ, jqwik (e.g. put–call parity over random inputs) | `src/test` |
| Module boundaries | Spring Modulith `verify()` + ArchUnit | `ModularityTest`, `SharedKernelArchitectureTest` |
| Coverage gate | JaCoCo, 85% of lines per module | `./gradlew check` |
| Secrets, vulnerable dependencies | gitleaks, Trivy | CI |

CI is the org's shared pipeline: [optionsdesk-hq/.github](https://github.com/optionsdesk-hq/.github).
Contributing rules: [CONTRIBUTING](https://github.com/optionsdesk-hq/.github/blob/main/CONTRIBUTING.md).
