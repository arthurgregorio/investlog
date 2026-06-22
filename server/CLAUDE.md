# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
./gradlew bootRun                                      # run the app
./gradlew build                                        # full build (compile + test)
./gradlew test                                         # run all tests
./gradlew test --tests "br.com.investlog.server.ServerApplicationTests"  # single test class
./gradlew jooqCodegen                                  # regenerate jOOQ sources from the Liquibase schema
```

`spring-boot-docker-compose` is a `developmentOnly` dependency wired to `compose.yaml`, so
`bootRun` automatically starts the local Postgres container (`investlog-postgres`, db/user/pass
`sa_investlog`, see `compose.yaml`) — no manual `docker compose up` needed for local dev.

`jooqCodegen` (and therefore `build`/`bootRun`/`test`, which depend on it via
`compileKotlin`) requires Docker running locally: it starts a throwaway `postgres:18-alpine`
Testcontainer, applies `db/changelog/db.changelog-master.xml` to it via Liquibase, generates
jOOQ Kotlin sources from it, then tears the container down.

## Coding Conventions

**No abbreviated names** — use full descriptive names everywhere: variables, parameters, jOOQ
table aliases, and loop iterators. Examples of what to avoid and what to use instead:

| Avoid | Use instead |
|-------|------------|
| `val w = WALLETS.as("w")` | `val wallets = WALLETS.as("wallets")` |
| `val sh = STOCK_HOLDINGS.as("sh")` | `val stockHoldings = STOCK_HOLDINGS.as("stock_holdings")` |
| `val fh = FUND_HOLDINGS.as("fh")` | `val fundHoldings = FUND_HOLDINGS.as("fund_holdings")` |
| `qty`, `pct`, `amt`, `cnt` | `quantity`, `percentage`, `amount`, `count` |
| `for (h in holdings)` | `for (holding in holdings)` |
| `baseCurrency` shorthand `base` | always `baseCurrency` |

This applies to both the Kotlin variable name **and** the SQL alias string passed to `.as()`.

## Architecture

`Application.kt` is the `@SpringBootApplication` bootstrap. Beyond that, the application layer
has:

- `shared/security` — cross-cutting current-user resolution: `CurrentUser` (domain model),
  `UserRepository` (queries/updates `system.users` via the generated jOOQ `USERS` table), and
  `CurrentUserProvider`/`FixedCurrentUserProvider` (resolves the current user; fixed to a dev
  user for now).
- `shared/persistence` — `pagedModelOf(content, pageable, total)`, the single place jOOQ page
  results become `org.springframework.data.web.PagedModel<T>` for collection endpoints.
- `shared/exceptions` — `NotFoundException`, mapped by `GlobalExceptionHandler` to a 404
  `ProblemDetail`.
- `config` — `WebMvcConfig` (path-segment API versioning, prefixes `@RestController`s under
  `/private/{version}`; `@EnableSpringDataWebSupport` activates `Pageable` parameter resolution)
  and `GlobalExceptionHandler` (RFC 7807 `ProblemDetail` error responses: 400 validation errors,
  404 `NotFoundException`, 409 `DataIntegrityViolationException` from unique/FK-restrict
  violations, 500 catch-all).
- `shared/rest/payloads` — shared REST payload types; currently holds `CurrencyCode`, a typed
  enum used as a path variable in the currency-rates controller.
- `profile` — `GET`/`PATCH /private/v1/profile`, following a `rest/{controllers,payloads}` +
  `domain/services` layout.
- `typelists` — `GET`/`POST`/`DELETE /private/v1/stock-types` and `.../fund-types`, paginated,
  sharing one pair of payloads (`TypeResponse`/`TypeCreateRequest`) since both resources are
  `{id, name}`. Extends the `profile` layout with `domain/repositories`.
- `currencyrates` — `GET`/`PUT /private/v1/currency-rates`, addressed by `currencyCode` (not
  `external_id`); `PUT` upserts and, when `isBase: true`, clears the previous base row in the
  same transaction.
- `holdingsoverview` — `GET /private/v1/holdings` with optional `kind` filter and Spring
  `Pageable` for server-side pagination. Returns `HoldingRowResponse` rows from the
  `holdings_overview` VIEW (joined with `wallets`). Computes `gain` and `gainPct` in Kotlin.
- `overview` — `GET /private/v1/overview` (portfolio summary: `baseCurrency`, totals, per-kind
  summaries with currency conversion) and `GET /private/v1/overview/series` (monthly cumulative
  invested amounts for chart display). `OverviewRepository` performs three separate jOOQ queries
  (stock lots, crypto lots, fund contributions) and accumulates a running total in Kotlin.

The persistence schema itself is fully defined (see below).

- Kotlin 2.3.21 / Spring Boot 4.1.0, JVM 25 toolchain. Root package: `br.com.investlog.server`.
- Web: `spring-boot-starter-webmvc` (servlet MVC, not WebFlux).
- Persistence: PostgreSQL via `spring-boot-starter-jooq` + `spring-boot-starter-liquibase`. The
  full schema — `system`/`finances` Postgres schemas, `system.users`, the `finances` portfolio
  tables (wallets, stock/crypto/fund holdings + lots/contributions, type lists, currency rates),
  the `finances.wallet_kind` enum, and the `finances.holdings_overview` regular VIEW
  (so writes are immediately reflected) — is
  defined in `src/main/resources/db/changelog/db.changelog-master.xml` and applied
  automatically by Liquibase on startup. `application.yaml` configures `spring.liquibase.*`
  (tracking tables renamed to `database_changelog`/`database_changelog_lock`);
  `application-prod.yaml` adds `spring.datasource.*` via `DB_HOST`/`DB_PORT`/`DB_NAME`/
  `DB_USER`/`DB_PASSWORD` env vars; `application-dev.yaml` relies on `spring-boot-docker-compose`
  auto-detection.
- jOOQ codegen (`org.jooq.jooq-codegen-gradle` official plugin, configured in `build.gradle.kts`)
  generates Kotlin sources for the `system`/`finances` schemas into
  `build/generated-sources/jooq/main` (package `br.com.investlog.server.jooq`, gitignored) as
  part of `compileKotlin` — see `jooqCodegen`/`startJooqDb`/`stopJooqDb`. `shared/security/UserRepository` is the first consumer, querying
  and updating `system.users` via the generated `USERS` table.
- `jackson-module-kotlin` for Kotlin-aware JSON (de)serialization. `kotlin-reflect` is on the
  classpath for frameworks that need it (jOOQ/Jackson/Spring).
- `kotlin-logging-jvm` (`io.github.oshai:kotlin-logging-jvm`) for structured, Kotlin-idiomatic
  logging via `KotlinLogging.logger {}` (SLF4J-backed).
- Other starters: `actuator`, `mail`, `validation`.
- `spring-data-commons` provides `Pageable`/`Page`/`PagedModel` and the MVC argument-resolver
  auto-configuration for paginated collection endpoints (default page size 20, configured via
  `spring.data.web.pageable.default-page-size`).

### Testing

- JUnit 5 (`useJUnitPlatform()`, parallel forks = available processors) + `kotlin-test-junit5`.
- `TestcontainersConfiguration` (`src/test/.../TestcontainersConfiguration.kt`) registers a
  Postgres `@ServiceConnection` testcontainer; `ServerApplicationTests` imports it for
  `@SpringBootTest`.
- `TestServerApplication` is an alternate `main` that boots the app with
  `TestcontainersConfiguration` applied, for running locally against a throwaway
  Testcontainers-managed Postgres.

### Packaging

`bootJar` is configured with layered jars (`dependencies`, `spring-boot-loader`,
`snapshot-dependencies`, `application`), output as `server.jar`. `bootBuildImage` targets
JVM 25 and produces `investlog/server:v<version>`.
