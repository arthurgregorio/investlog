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

**`@Value` uses Kotlin's `$$` multi-dollar interpolation prefix**, not the old `"\${...}"`
escape: `@Value($$"${investlog.brapi.token:}")`.

**REST controllers always return `ResponseEntity<T>`, never `@ResponseStatus`** — use
`ResponseEntity.ok(...)`, `.status(HttpStatus.CREATED).body(...)`, or `.noContent().build()`
(typed `ResponseEntity<Void>` for delete/no-body responses).

**`@RequestParam`/`@PathVariable` for a value with a fixed set of options is typed as the enum
itself, never `String`** — e.g. `HoldingsOverviewController.findAll`'s `kind: WalletKind?`. Spring
converts the raw query/path string to the enum automatically (matching the constant name), so an
invalid value 400s before the handler method even runs, with no manual validation needed. This
also matches what the client already sends — the frontend posts the enum's own text value, so no
translation step is needed on either side.

**Not every feature needs a `@Service`.** If a controller method would do nothing but call a
repository method and return its result unchanged, skip the service and have the controller call
the repository directly (see `HoldingsOverviewController`/`OverviewController`). Only add a
`@Service` when there's actual work to do between the repository and the response: transforming
the repository's result before it goes to the controller, or real business logic (validation
beyond `@Valid`, multi-step orchestration, side effects, calling other services). A service that
just delegates one-to-one to a repository is a pass-through layer that adds nothing.

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

`Application.kt` is the `@SpringBootApplication` bootstrap. Every feature package uses the same flat
layout — `<feature>/<Controller>.kt` plus `services/`, `repositories/` and `rest/payloads/`, with no
`domain/` layer and no `rest/controllers/` subfolder.

| Package | What it owns |
|---|---|
| `shared/security` | Current-user resolution, `system.users`, admin bootstrap — see **Authentication & Authorization** |
| `shared/persistence` | `pagedModelOf(...)`, the one place jOOQ pages become `PagedModel<T>` |
| `shared/exceptions` | Domain exceptions, each mapped to a `ProblemDetail` |
| `shared/http/brapi` | `StocksClient`, the reusable brapi.dev client |
| `config` | Web/security/scheduling/caching config and `GlobalExceptionHandler` |
| `auth` | Login, register, TOTP, trusted devices, Google OAuth |
| `usersadmin` | Admin-only user management under `/users` |
| `profile` | The current user's own profile and password |
| `wallets` | Wallet CRUD |
| `stockholdings`, `cryptoholdings`, `fundholdings` | The three holding kinds — see **Holdings** |
| `holdingsoverview` | `GET /holdings`, the paginated cross-kind view |
| `results` | Withdrawals, `finances.results`, and the average-cost exit math — see **Results and withdrawals** |
| `walletmoves` | Relocating holdings between wallets and `finances.wallet_moves` — see **Wallet moves** |
| `walletdetail` | `GET /wallets/{id}/detail`, the per-wallet dashboard payload — see **Wallet detail** |
| `overview` | Portfolio summary and the monthly invested series |
| `stockpricesync`, `cryptopricesync`, `usdpricesync` | Scheduled price refresh — see **Price sync** |
| `typelists`, `currencyrates`, `configurations` | Reference data and runtime feature toggles |

In detail:

- `shared/security` — cross-cutting current-user resolution: `CurrentUser` (domain model, with a
  nested `CurrentUser.Status` enum — `PENDING`/`APPROVED`/`BLOCKED`), `UserRole`, `AuthProvider`,
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
  `ROLE_ADMIN` in `SecurityConfiguration`. `block`/`changeRole`/`delete`/`resetPassword` all guard
  against the caller targeting their own account (`SelfActionNotAllowedException`, 400) to
  prevent a self-lockout; `approve`, `unblock`, and `resetTotp` don't need the guard since
  self-targeting them is a genuine no-op, not a lockout.
- `shared/persistence` — `pagedModelOf(content, pageable, total)`, the single place jOOQ page
  results become `org.springframework.data.web.PagedModel<T>` for collection endpoints.
- `shared/exceptions` — domain exceptions (`NotFoundException`, `InvalidCredentialsException`,
  `InvalidTotpCodeException`, `TotpAlreadyEnabledException`, `TotpRequiredException`,
  `UserNotApprovedException`, `SelfActionNotAllowedException`), each mapped by
  `GlobalExceptionHandler` to a `ProblemDetail`.
- `config` — `WebMvcConfiguration` (path-segment API versioning, prefixes `@RestController`s under
  `/private/{version}`; `@EnableSpringDataWebSupport` activates `Pageable` parameter resolution),
  `SecurityConfiguration` (the filter chain — see below; its `AccessDeniedHandler` writes a nested
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
- `configurations` — `GET /private/v1/configurations`, `PATCH /private/v1/configurations/{key}`.
  Runtime feature toggles keyed by `ConfigurationKey`; this is what gates the price-sync jobs.
- `wallets` — `GET`/`POST /private/v1/wallets`, `GET`/`PATCH`/`DELETE /private/v1/wallets/{id}`.
- `stockholdings`, `cryptoholdings`, `fundholdings` — the three holding kinds, each nested under
  `/private/v1/wallets/{walletId}/{stock|crypto|fund}-holdings` with the same CRUD shape plus a
  child collection: `lots` for stocks and crypto, `contributions` for funds. They are separate
  packages rather than one generic holdings package because the three kinds differ in their child
  entity and in which fields are price-synced.

### Results and withdrawals

`results` owns every exit from a position. `POST /private/v1/wallets/{walletId}/{stock|crypto|fund}-holdings/{holdingId}/withdrawals` records one, and `GET /private/v1/results` reads them back cross-kind and paginated. All three write endpoints live on one `WithdrawalController` and funnel into one `WithdrawalService`, so the average-cost math exists once rather than three times.

**`WithdrawalService` reads the position from `finances.holdings_overview`, not from the lot tables.** That view already reports `quantity` and `cost_basis` net of everything withdrawn so far, which makes the average price simply `cost_basis / quantity` — and keeps it correct across repeated partial exits without the service tracking anything itself. The view carries `holding_id` for exactly this reason: it is what lets one query serve all three kinds.

**The withdrawn totals are subtracted in `holdings_overview` through scalar correlated subqueries, never a join.** Joining `finances.results` to a branch that already aggregates lots multiplies the `SUM` by the number of result rows, silently inflating quantity and cost basis on any holding exited more than once. Adding a second such aggregate later (issue #205's transfers reuse this table) must follow the same rule.

**Only stock and crypto subtract withdrawn quantity from `current_value`.** A fund withdrawal decrements `fund_holdings.current_value` directly, so the view nets only its `cost_basis`; subtracting again would double-count.

`holdings_overview` exposes `status` and each of its three consumers filters to `ACTIVE` itself (`HoldingsOverviewRepository`, `OverviewRepository`, `WalletRepository`). `holdings_report_rows` is the deliberate exception — it filters to `ACTIVE` inside the view and does not expose the column, because it merges rows sharing `(wallet_id, kind, ticker, type_label, name)` and two such holdings can differ in status; adding `status` to its `GROUP BY` would split the very rows it exists to merge.

A wallet whose holdings have all been completed reports the same shape as an empty wallet — `holdingCount` 0, `totalInvested` 0, a null `currentValue` — because `WalletRepository`'s subqueries now match no rows. That is the intended outcome, and the wallets view already renders that shape.

### Wallet moves

`walletmoves` relocates holdings between two wallets of the same kind and currency (issue #245). `POST /private/v1/wallets/{originWalletId}/moves` takes a destination and a list of holdings, each with an optional `quantity`; `GET /private/v1/wallets/{walletId}/moves` pages the audit rows in and out of a wallet. A move is not an exit: it writes no `finances.results` row and works entirely through the lot, contribution and holding tables, so neither view needs to know about it.

**A holding that has withdrawals is never deleted by a move.** `finances.results` cascades on holding delete, so the spec's merge (reattach the lots, delete the origin) is only used when the origin has no results. Otherwise `WalletMoveService` takes the moved quantity out at the net average from `holdings_overview` — shrinking the origin's lots and rescaling their prices so the cost drops by exactly `average × moved` — and a fully-moved origin is marked `COMPLETED` in place. The decision and its reasoning are on #245.

`finances.wallet_moves` stores the holdings by external id with no FK and snapshots their name and ticker, because a merge deletes the origin holding; its wallet FKs are `SET NULL`, so deleting an emptied origin wallet keeps the move in the destination's history.

### Wallet detail

`walletdetail` serves `GET /private/v1/wallets/{externalId}/detail`, the single payload behind the per-wallet dashboard (issue #212). It assembles five sections that would otherwise be five client round-trips: header figures, the snapshot series with day/week/month deltas, best and worst performer, the largest holding's share, and an activity summary.

This one earns its `@Service`. `WalletDetailService` runs three repository queries and derives everything else in Kotlin, so it is not the controller-to-repository passthrough the convention tells you to skip.

**The wallet's investment list is deliberately absent.** The client reuses `GET /private/v1/holdings?walletId=`, which is already paginated and already backs the investments table; duplicating it here would mean a second, unpaginated code path for the same rows.

**Deltas are snapshot-to-snapshot, not snapshot-to-live.** `changeOver` compares the latest snapshot against the most recent one at or before `latest - N days`, and returns null when no such snapshot exists. A wallet gets an empty series and three null deltas until `walletsnapshots` has been collecting for long enough, which is why #174 shipped first.

**`findTransactions` unions stock lots, crypto lots and fund contributions.** The three child tables have different column names for the same idea, so the union aliases them to `transaction_date`/`investment_name`/`amount` and the service reads them by alias.

**Known gap, tracked on #212.** The endpoint does not yet exclude `COMPLETED` holdings, because `finances.holding_status` arrives with #204 and is not on this branch or on `main`. When `feature/176-wallet-detail-view` is rebased onto a `main` containing #204, `WalletDetailRepository.findHoldings` needs `status = 'ACTIVE'` added — the query keeps compiling without it and silently counts fully-withdrawn holdings in every current figure.

### Price sync

Three schedulers refresh prices in place, each gated by its own `ConfigurationKey` toggle
(`STOCK_`/`CRYPTO_`/`USD_PRICE_SYNC_ENABLED`) read through `configurations`. `stockpricesync` and
`cryptopricesync` each expose one admin-only manual trigger; `usdpricesync` has no controller at
all. All three share the same failure rule: a ticker that 404s, times out or otherwise fails is
logged as a warning and skipped, keeping its last-known price, so one bad ticker never blocks a run.

- `stockpricesync` — `StockPriceSyncScheduler` runs
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

- `cryptopricesync` — mirrors `stockpricesync`'s shape (repository/services/scheduler/rest, no
  `domain/` layer) but syncs on `@Scheduled(cron = "0 0 * * * *")`, every hour 24/7 since crypto
  markets don't close, gated the same way by `ConfigurationKey.CRYPTO_PRICE_SYNC_ENABLED` and a
  `POST /crypto-price-sync` admin-only manual trigger (`hasAuthority("ROLE_ADMIN")` in
  `SecurityConfiguration`, same as `/stock-price-sync`). The price source is CoinGecko's free, keyless
  `/simple/price`, but unlike brapi.dev's stock tickers, crypto ticker symbols are **not**
  globally unique on CoinGecko — e.g. `btc` and `eth` each match 10+ unrelated coins (verified
  against the live `/coins/list`) — so `CryptoPriceSyncService.syncPrices()` never queries
  `/simple/price` by symbol directly. It first resolves each distinct ticker in
  `finances.crypto_holdings` to CoinGecko's canonical coin id via `GET
  /coins/markets?vs_currency=usd&symbols={tickers}&order=market_cap_desc`, which returns exactly
  one, market-cap-ranked row per symbol (CoinGecko's own disambiguation, not a local heuristic).
  That resolve call goes through `CoinGeckoSymbolResolver`, a separate `@Service` (not a method on
  `CryptoPriceSyncService` itself) so its `@Cacheable` annotation actually takes effect — Spring's
  proxy-based caching is a no-op on self-invocation, so cross-bean placement is load-bearing, not
  a style choice. The cache (`CachingConfiguration`, Caffeine, 10-minute `expireAfterWrite`, keyed by the
  sorted distinct ticker list) exists to collapse repeat resolves within a burst of manual
  triggers; it's always fully expired well before the next hourly run. Only the resolve step is
  cached — the subsequent `GET /simple/price?ids={resolved ids}&vs_currencies={wallet currencies}`
  call is never cached, so the price actually written is always fresh. A ticker CoinGecko has no
  match for (typo, delisted) is skipped with a warning and keeps its last-known price, same
  fallback as `stockpricesync`. `CoinGeckoClient` lives in `cryptopricesync/http` (not `shared/`,
  unlike `StocksClient` — CoinGecko has no other consumer in this codebase) and is registered via
  `@ImportHttpServices(group = "coingecko")` in `config/http/CoinGeckoHttpClientsConfig`, base URL
  set programmatically via `RestClientHttpServiceGroupConfigurer` for the same reason documented
  above for brapi — no `spring.http.serviceclient.*` Boot auto-configuration exists in 4.1.0. Base
  URL and the auth header name are plain properties with their real defaults declared once, in
  `application.yaml` (`investlog.coingecko.base-url`/`api-key-header`, defaulting to
  `api.coingecko.com`/`x-cg-demo-api-key`) — not a plan/tier abstraction, and not duplicated as
  constants in `CoinGeckoHttpClientsConfiguration`. A paid CoinGecko Pro account means editing those two
  values directly (`pro-api.coingecko.com` / `x-cg-pro-api-key`); they're deliberately **not**
  wired as `.env`/`COINGECKO_*` variables through `compose.yaml` the way `COINGECKO_KEY` is —
  `compose.yaml`'s `environment:` block always sets whatever it lists, blank or not, and Spring's
  `${VAR:default}` only falls back on a fully-absent key, not an empty one, so a blank passthrough
  would silently override the YAML default with an empty string and break the `RestClient` with
  `URI with undefined scheme`. Skipping them in `compose.yaml` keeps the key genuinely absent for
  the packaged stack, so `application.yaml`'s default actually applies; a Pro-plan user overrides
  it by editing `application.yaml` (or their own Compose customization) instead. The header is
  added only when `investlog.coingecko.api-key`/`COINGECKO_KEY` is non-blank; both endpoints used
  here work fully keyless on the demo tier, a key just raises the
  practical rate ceiling. `CryptoPriceSyncRepository.updatePrice` matches on ticker **and** wallet currency
  (joined through `finances.wallets`, filtered to `kind = 'CRYPTO'`) since, unlike stocks which are
  always BRL on B3, a crypto wallet can be BRL or USD — the same ticker can need two different
  prices written to two different sets of rows in the same sync run. Tests stub both CoinGecko
  endpoints with WireMock, including a fixture that only returns the canonical coin for a
  collision-prone symbol, asserting the service trusts CoinGecko's resolved id rather than the raw
  ticker.

- `usdpricesync` — no REST controller. Refreshes the USD reference rate on
  `@Scheduled(cron = "0 0 7,18 * * *", zone = "America/Sao_Paulo")`, twice daily.

### Authentication & Authorization

- **Session authorities are computed once at login and never re-evaluated per request.**
  `AuthService.establishSession` bakes `ROLE_${role}` and `STATUS_${status}` into the session's
  `Authentication` at login time; `SecurityConfiguration`'s filter chain (`hasAuthority(...)`) only ever
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
- **Trusted devices** (`auth/services/TrustedDeviceService.kt`) let a verified device skip the TOTP
  step. `trust()` issues a random token, stores only its hash via `TrustedDeviceRepository`, and
  sets a `trusted_device` cookie scoped to `path=/private/v1/auth`; `isTrusted()` re-hashes the
  incoming cookie to look the device up. Managed through `GET`/`DELETE /auth/trusted-devices`.
  Skipping TOTP is the whole point, so treat any change here as a change to the second factor.
- **Google OAuth** (`auth/security/GoogleLogin{Success,Failure}Handler.kt`,
  `GoogleLinkTokenStore.kt`). When a Google account's email already belongs to a local account, the
  success handler does **not** log the user in — it issues a short-lived link token from the
  in-memory `GoogleLinkTokenStore` and redirects to `/login?error=email_in_use&linkToken=…`, which
  is the client's `link` step. `POST /auth/google/link` consumes that token with the account's
  password and funnels through `establishSession` like every other login path. The store is
  in-memory, so pending links do not survive a restart and do not work across instances.
- **Attempt limiting** — `LoginAttemptLimiter` and `TotpAttemptLimiter` wrap a shared
  `AttemptLockoutTracker` with escalating lockouts, configured under
  `investlog.security.login.*`. Both are keyed **per account** (email), not per IP; issue #209
  tracks the resulting gaps (no IP-level limit, and targeted lockout as a DoS vector).
- Jackson 3 (Spring Boot 4): inject `tools.jackson.databind.json.JsonMapper`, not
  `tools.jackson.databind.ObjectMapper` — Spring auto-configures a `JsonMapper` bean as the
  concrete JSON mapper; `ObjectMapper` is now a more generic base type not meant for direct
  injection. Needed anywhere you serialize a response body by hand outside the normal
  controller/`ProblemDetail` pipeline (e.g. `SecurityConfiguration`'s `AccessDeniedHandler`).
- The shared test harness (`RestClientTestConfiguration` /
  `AdminSessionCookieInterceptor`, `src/test/.../AdminSessionCookieInterceptor.kt`) auto-injects
  an admin session cookie into any `RestTestClient` request that doesn't already carry a `Cookie`
  header. To test a non-admin or unauthenticated path, capture that user's own session cookie
  (via a real login/TOTP-verify call) and pass it explicitly with `.header("Cookie", cookie)` —
  passing an explicit header is what short-circuits the auto-injection.

### Stack

- Kotlin 2.4.10 / Spring Boot 4.1.1, JVM 25 toolchain. Root package: `br.com.investlog.server`.
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
