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

**Constructor parameters never carry an inline annotation** — `@Value(...)` (or any other
annotation) goes on its own line above the `private val`/`val`. Function-signature parameters
(not constructors) may keep the annotation inline, e.g. `fun foo(@PathVariable id: UUID)`.

**`@Value` uses Kotlin 2.3's `$$` multi-dollar interpolation prefix**, not the old `"\${...}"`
escape: `@Value($$"${investlog.brapi.token:}")`.

**REST controllers always return `ResponseEntity<T>`, never `@ResponseStatus`** — use
`ResponseEntity.ok(...)`, `.status(HttpStatus.CREATED).body(...)`, or `.noContent().build()`
(typed `ResponseEntity<Void>` for delete/no-body responses).

**Every `@Service` class carries `@Transactional(readOnly = true)`**; each write method adds its
own explicit `@Transactional`. `StockPriceSyncService` is the reference example. Gotcha: a class
that extends a Spring base type with its own protected `logger`/`log` field (e.g.
`ResponseEntityExceptionHandler`) will have that member shadow a file-level `KotlinLogging`
property of the same name — calls silently resolve to the wrong overload and fail with a
confusing `Argument type mismatch: () -> String vs Throwable!` compile error. Fully qualify the
file-level property at the call site (e.g. `br.com.investlog.server.config.logger.error(ex) { }`)
when this happens.

**`KotlinLogging` instances are named `logger`, not `log`**: `private val logger =
KotlinLogging.logger {}`.

See the root `CLAUDE.md` for the repo-wide comment convention and branch-naming rule — both
apply here too.

## Migrations

**Never edit an existing Liquibase changelog file** under
`src/main/resources/db/changelog/changes/**`, even to add "just one more changeset" to a
recent-looking file. Liquibase tracks applied changesets by id+author+checksum in
`database_changelog`; editing one that already ran breaks startup (checksum mismatch) or
silently skips the new SQL. Always create a new file under
`db/changelog/changes/<year>/<month>/<DD-HHMM>-<description>.xml` and add an `<include>` for
it in `db.changelog-master.xml`.

## Architecture

`Application.kt` is the `@SpringBootApplication` bootstrap. Beyond that, the application layer
has:

- `shared/security` — cross-cutting current-user resolution: `CurrentUser` (domain model, with a
  nested `CurrentUser.Status` enum — `PENDING`/`APPROVED`/`REJECTED`), `UserRole`, `AuthProvider`,
  `UserRepository` (queries/updates `system.users` via the generated jOOQ `USERS` table),
  `AdminBootstrapRunner` (seeds the admin account from `ADMIN_DEFAULT_PASSWORD` on first boot),
  and `CurrentUserProvider`/`SecurityContextCurrentUserProvider` — see **Authentication &
  Authorization** below, this is more load-bearing than it looks.
- `auth` — `POST /auth/login`, `POST /auth/register`, `GET /auth/session`, `POST /auth/logout`,
  `POST /auth/totp/enroll`, `POST /auth/totp/verify`. `AuthService` owns session establishment
  (the *only* code path that issues a session — see below); `TotpService` wraps
  `dev.samstevens.totp` (QR generation, code verification). `LoginResult` (sealed interface:
  `Authenticated`/`EnrollmentRequired`) is `auth`'s own return type, kept in its own file rather
  than co-located in `AuthService.kt` — one type per file, even for small/tightly-coupled types.
- `usersadmin` — admin-only user management: `GET /users` (paginated), `PATCH
  /users/{id}/approve|block|unblock|role|totp-reset|password`, `DELETE /users/{id}`. Gated to
  `ROLE_ADMIN` in `SecurityConfig`. `block`/`changeRole`/`delete`/`resetPassword` all guard
  against the caller targeting their own account (`SelfActionNotAllowedException`, 400) to
  prevent a self-lockout; `approve`, `unblock`, and `resetTotp` don't need the guard since
  self-targeting them is a genuine no-op, not a lockout.
- `shared/persistence` — `pagedModelOf(content, pageable, total)`, the single place jOOQ page
  results become `org.springframework.data.web.PagedModel<T>` for collection endpoints.
- `shared/exceptions` — domain exceptions (`NotFoundException`, `InvalidCredentialsException`,
  `InvalidTotpCodeException`, `TotpAlreadyEnabledException`, `TotpRequiredException`,
  `UserNotApprovedException`, `SelfActionNotAllowedException`), each mapped by
  `GlobalExceptionHandler` to a `ProblemDetail`.
- `config` — `WebMvcConfig` (path-segment API versioning, prefixes `@RestController`s under
  `/private/{version}`; `@EnableSpringDataWebSupport` activates `Pageable` parameter resolution),
  `SecurityConfig` (the filter chain — see below; its `AccessDeniedHandler` writes a nested
  `SecurityConfig.AccessDeniedResponse` data class, not a shared payload type), and
  `GlobalExceptionHandler` (RFC 7807 `ProblemDetail` error responses: 400 validation errors, 404
  `NotFoundException`, 409 `DataIntegrityViolationException` from unique/FK-restrict violations,
  500 catch-all).
- `profile` — `GET`/`PATCH /private/v1/profile`, `PATCH /private/v1/profile/password`, following
  the flat `<feature>/<Controller>.kt` + `services/` + `rest/payloads/` layout every feature
  package uses (no `domain/` layer, no `rest/controllers/` subfolder — dropped repo-wide).
- `typelists` — `GET`/`POST`/`DELETE /private/v1/stock-types` and `.../fund-types`, paginated,
  sharing one pair of payloads (`TypeResponse`/`TypeCreateRequest`) since both resources are
  `{id, name}`. Extends the `profile` layout with a `repositories/` folder.
- `currencyrates` — `GET`/`PUT /private/v1/currency-rates`, addressed by `currencyCode` (not
  `external_id`); `PUT` upserts and, when `isBase: true`, clears the previous base row in the
  same transaction. Owns `CurrencyCode` (`currencyrates/rest/payloads/CurrencyCode.kt`) — the
  typed enum used as the path variable here and imported cross-package by `profile` for
  `preferredCurrency`.
- `holdingsoverview` — `GET /private/v1/holdings` with optional `kind` filter and Spring
  `Pageable` for server-side pagination. Returns `HoldingRowResponse` rows from the
  `holdings_overview` VIEW (joined with `wallets`). Computes `gain` and `gainPct` in Kotlin.
- `overview` — `GET /private/v1/overview` (portfolio summary: `baseCurrency`, totals, per-kind
  summaries with currency conversion) and `GET /private/v1/overview/series` (monthly cumulative
  invested amounts for chart display). `OverviewRepository` performs three separate jOOQ queries
  (stock lots, crypto lots, fund contributions) and accumulates a running total in Kotlin.
- `stockpricesync` — no REST controller. `StockPriceSyncScheduler` runs
  `@Scheduled(cron = "0 0 10-18 * * MON-FRI", zone = "America/Sao_Paulo")` (B3 trading hours only)
  and calls `StockPriceSyncService.syncPrices()`, which fetches every distinct `ticker` in
  `finances.stock_holdings` and calls the `StocksClient` HTTP service (`GET
  /v2/stocks/quote?symbols={ticker}` on [brapi.dev](https://brapi.dev/) — the current documented
  endpoint; the legacy `/api/quote/{ticker}` still works but is explicitly called out as legacy in
  brapi's own docs — one call per ticker, price nested under `results[].data.regularMarketPrice`)
  to refresh `current_price`/`updated_at`. A ticker that 404s, times out, or otherwise fails is
  logged as a warning and skipped — it keeps its last-known price and the loop moves on to the
  next ticker, so one bad ticker never blocks the rest of a run. `StocksClient` lives in
  `shared/http/brapi` rather than inside `stockpricesync` itself — it's a reusable brapi.dev
  client, not owned by this one sync job — and is registered via `@ImportHttpServices(group =
  "brapi")` in `config/http/BrApiHttpClientsConfig`; **Spring Boot
  4.1.0 has no `spring.http.serviceclient.*` auto-configuration** (verified against the shipped
  jars — no such properties exist), so the base URL and the `Authorization: Bearer
  ${investlog.brapi.token}` header (from `BRAPI_TOKEN` — brapi requires a token unlike CoinGecko's
  keyless `/simple/price`) are set programmatically on the group's `RestClient.Builder` via a
  `RestClientHttpServiceGroupConfigurer` bean, sourced from `investlog.brapi.base-url`/
  `investlog.brapi.token`, not from Boot-managed YAML properties.
  `config/SchedulingConfig` (`@EnableScheduling @Profile("!test")`) keeps the cron disabled during
  tests; tests call `syncPrices()` directly and stub brapi with WireMock rather than a
  hand-written fake (this codebase uses no object-mocking framework, but WireMock stubs HTTP, not
  Kotlin objects, so it fits) — `org.wiremock:wiremock-standalone` (not the bare `wiremock`
  artifact) is required, since the bare artifact's transitive Jetty version gets silently
  mangled by Spring's dependency-management BOM. The manual `PATCH
  /wallets/{walletId}/stock-holdings/{holdingId}` endpoint in `stockholdings` still works as an
  override — a hand-edited price is simply overwritten again on the next scheduled run.

### Authentication & Authorization

- **Session authorities are computed once at login and never re-evaluated per request.**
  `AuthService.establishSession` bakes `ROLE_${role}` and `STATUS_${status}` into the session's
  `Authentication` at login time; `SecurityConfig`'s filter chain (`hasAuthority(...)`) only ever
  checks those cached values, never the database. Concretely: promoting/demoting a user, or
  approving them, takes effect on their *next login*, not immediately. Rejecting or deleting a
  user is different and more urgent — a stale `STATUS_APPROVED` authority would otherwise leave
  their access open for the rest of that session. That gap is closed by a **second, independent
  check**: `SecurityContextCurrentUserProvider.getCurrentUser()` re-fetches the user row from the
  database on every call (every real business service resolves the current user through this,
  not through the raw session principal) and throws `UserNotApprovedException` the moment status
  is no longer `APPROVED` — so revocation actually lands on the user's *next real action*, not
  their next login. **When adding any new authorization-relevant, mutable attribute to `CurrentUser`
  (Phase 4's Google-linked accounts included), route the live check through `CurrentUserProvider`,
  not just the filter chain — the filter chain alone will always be one login stale.**
  `/auth/session` and `/auth/logout` deliberately read the raw session principal instead (so a
  pending/rejected user can still check their status and log out).
- **`establishSession` is the only session-issuing code path** — every login flow (password,
  TOTP verify, and Phase 4's Google callback) must funnel through it rather than open-coding a
  second way to mint a session, or a gate implemented in one path silently won't apply to another.
- Jackson 3 (Spring Boot 4): inject `tools.jackson.databind.json.JsonMapper`, not
  `tools.jackson.databind.ObjectMapper` — Spring auto-configures a `JsonMapper` bean as the
  concrete JSON mapper; `ObjectMapper` is now a more generic base type not meant for direct
  injection. Needed anywhere you serialize a response body by hand outside the normal
  controller/`ProblemDetail` pipeline (e.g. `SecurityConfig`'s `AccessDeniedHandler`).
- The shared test harness (`RestClientTestConfiguration` /
  `AdminSessionCookieInterceptor`, `src/test/.../AdminSessionCookieInterceptor.kt`) auto-injects
  an admin session cookie into any `RestTestClient` request that doesn't already carry a `Cookie`
  header. To test a non-admin or unauthenticated path, capture that user's own session cookie
  (via a real login/TOTP-verify call) and pass it explicitly with `.header("Cookie", cookie)` —
  passing an explicit header is what short-circuits the auto-injection.

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

- JUnit 5 (`useJUnitPlatform()`, `maxParallelForks = 4`) + `kotlin-test-junit5`. Forks are capped
  (rather than using all available processors) to avoid Docker/Testcontainers connection-pool
  contention when many forks start a Postgres container at once.
- `TestcontainersConfiguration` (`src/test/.../TestcontainersConfiguration.kt`) registers a
  Postgres `@ServiceConnection` testcontainer; `ServerApplicationTests` imports it for
  `@SpringBootTest`. `BaseIntegrationTest` carries `@DirtiesContext(classMode = AFTER_CLASS)` so
  each test class gets its own fresh container/schema — controller test classes assert exact row
  counts assuming a clean table at class start, and the Spring test-context cache would otherwise
  share one container/database across classes with identical `@SpringBootTest` config.
- `RestClientTestConfiguration` / `AdminSessionCookieInterceptor`
  (`src/test/.../AdminSessionCookieInterceptor.kt`, imported by `BaseIntegrationTest` alongside
  `TestcontainersConfiguration`) auto-authenticates every `RestTestClient` request as the seeded
  admin, so existing test classes don't need to log in explicitly — see **Authentication &
  Authorization** above for how to test as a different/no user.
- `TestServerApplication` is an alternate `main` that boots the app with
  `TestcontainersConfiguration` applied, for running locally against a throwaway
  Testcontainers-managed Postgres.

### Packaging

`bootJar` is configured with layered jars (`dependencies`, `spring-boot-loader`,
`snapshot-dependencies`, `application`), output as `server.jar`. `bootBuildImage` targets
JVM 25, builds on BellSoft's Alpaquita Linux builder (`bellsoft/buildpacks.builder:musl`) for a
smaller musl-based image, and produces `investlog/server:v<version>`.
