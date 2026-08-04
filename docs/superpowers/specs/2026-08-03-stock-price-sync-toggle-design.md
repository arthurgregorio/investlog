# InvestLog — Stock Price Sync Toggle Design

**Date:** 2026-08-03
**Status:** Draft (pending user review)

## Goal

Add a global, admin-controlled on/off switch for the automatic stock price sync job,
surfaced as a toggle in the existing Settings screen. Back it with a reusable
key/value settings table — not a one-off column or table — so the next global
toggle (an already-planned "auto-update currencies" switch) is just another row,
not another migration. Alongside the toggle, add an admin-only button that forces
an immediate sync run on demand, independent of the toggle's state or the cron
schedule.

## Context (as-built)

- `StockPriceSyncScheduler.syncPrices()`
  (`server/src/main/kotlin/br/com/investlog/server/stockpricesync/scheduler/StockPriceSyncScheduler.kt:12-26`)
  runs unconditionally on `@Scheduled(cron = "0 0 10-18 * * MON-FRI", zone =
  "America/Sao_Paulo")`. No flag, property, or DB check gates it today — the only
  existing on/off switch is `@Profile("!test")` on `SchedulingConfig`, which disables
  scheduling app-wide during tests.
- `StockPriceSyncService.syncPrices()` has no user context — it queries every
  distinct ticker across `finances.stock_holdings` system-wide, so a **global** flag
  (not per-user) is the natural fit; a per-user flag would require rescoping this
  query entirely.
- `SettingsView.vue` (route `/settings`) is the app's only "Configurations" screen and
  is admin-only at the router level (`client/src/router/index.ts:54-60`). It's a flat
  stack of `Card` sections (currency rates, stock types, fund types) — no tabs, no
  per-user content. A new toggle fits as a fourth `Card` in this same flat layout.
- No `b-switch` (Buefy boolean toggle) exists anywhere in the client today — this
  feature introduces the pattern.
- Two existing settings precedents, neither a fit on its own: per-user columns
  bolted onto `system.users` (`accentColor`, `preferredCurrency`, wired through
  `PATCH /profile`), and whole global tables (`currency_rates`, `stock_types`,
  `fund_types` — themselves migrated **from** per-user **to** global by
  `24-1200-make-settings-global.xml`). Neither is a generic key/value settings
  store; this design adds that as a new, reusable primitive.
- Migration convention (`server/CLAUDE.md`): new file only, under
  `db/changelog/changes/<year>/<month>/<DD-HHMM>-<description>.xml`, registered via
  `<include>` in `db.changelog-master.xml`. Never edit an existing changelog file.
- `stockpricesync` currently has **no REST controller at all** (`server/CLAUDE.md`
  says so explicitly) — `StockPriceSyncService.syncPrices()` is only ever called by
  the scheduler. It's synchronous and sequential (`server/.../StockPriceSyncService.kt:20-40`):
  one `stocksClient.getQuote(ticker)` HTTP call per distinct ticker, in a loop, no
  concurrency. A manual trigger endpoint calls the same method directly and returns
  once the whole loop finishes — no background job/async infra exists in this
  codebase to do otherwise, and none is needed at current ticker-count scale.
- Admin-only route gating is enforced in `SecurityConfig.kt` per-HTTP-method, e.g.
  `authorize(HttpMethod.POST, "/private/v1/stock-types/**",
  hasAuthority("ROLE_ADMIN"))` (`server/src/main/kotlin/.../config/core/SecurityConfig.kt:69`)
  — a new admin-only endpoint follows this exact pattern, one line added to the
  `authorizeHttpRequests` block.

## Decision

| Decision | Choice |
| --- | --- |
| Scope | Global, single row per key — not per-user |
| Storage | New `system.configurations` table: `key TEXT PRIMARY KEY`, `value TEXT NOT NULL`, `updated_at TIMESTAMPTZ NOT NULL DEFAULT now()` |
| Seed data | One row: `('stock_price_sync_enabled', 'true')`, inserted by the same migration |
| Server caching | All rows loaded into an in-memory `ConcurrentHashMap<String, String>` on startup; reads never hit the DB; writes go DB-then-cache |
| Server package | New `configurations` package, same flat layout as `typelists` (`Controller` + `services/` + `repositories/` + `rest/payloads/`) |
| Server endpoints | `GET /private/v1/configurations`, `PATCH /private/v1/configurations/{key}` — both `ROLE_ADMIN`, same gate as currency rates/type lists |
| Key typing | `ConfigurationKey` enum (e.g. `STOCK_PRICE_SYNC_ENABLED("stock_price_sync_enabled")`) gives call sites a typed handle over the raw string keys stored in the DB |
| Scheduler change | `StockPriceSyncScheduler.syncPrices()` checks `configurationService.isEnabled(STOCK_PRICE_SYNC_ENABLED)` first; if disabled, logs and returns before calling `StockPriceSyncService` at all. Cron still fires on schedule — it just no-ops |
| Client placement | Fourth `Card` in the existing `SettingsView.vue`, no new route/tab |
| Client control | Buefy `b-switch`, first one in the codebase — sets the pattern for the future currency-auto-update toggle |
| Force-sync endpoint | `POST /private/v1/stock-price-sync`, `ROLE_ADMIN`-gated, new `StockPriceSyncController` calling `StockPriceSyncService.syncPrices()` directly |
| Force-sync vs. toggle | The force action **ignores** `stock_price_sync_enabled` — it always runs when clicked, regardless of whether auto-sync is on or off. "Force" means force. |
| Force-sync response | Synchronous — the request blocks until the full ticker loop completes, then returns `204 No Content`. No async job/queue; matches the service's existing synchronous, sequential design |
| Client control (force) | Button in the same "Sincronização automática" `Card`, `b-button` with Buefy's `loading` prop bound to an in-flight ref, disabled while a request is outstanding |

## Architecture

### Database

```sql
CREATE TABLE system.configurations (
    key        TEXT PRIMARY KEY,
    value      TEXT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO system.configurations (key, value)
VALUES ('stock_price_sync_enabled', 'true');
```

New changelog file under `server/src/main/resources/db/changelog/changes/2026/08/`,
included from `db.changelog-master.xml`. Symmetric rollback (`DROP TABLE
system.configurations`).

### Server

```
configurations/
  ConfigurationController.kt          # GET /configurations, PATCH /configurations/{key}
  ConfigurationKey.kt                 # enum: typed keys over raw DB strings
  services/
    ConfigurationService.kt           # in-memory cache, isEnabled(key), update(key, value)
  repositories/
    ConfigurationRepository.kt        # jOOQ: findAll(), upsert(key, value)
  rest/payloads/
    ConfigurationResponse.kt          # { key, value, updatedAt }
    ConfigurationUpdateRequest.kt     # { value }
```

`ConfigurationService` (`@Service @Transactional(readOnly = true)`, per the repo's
service-layer convention):
- `@PostConstruct` loads every row from `ConfigurationRepository.findAll()` into a
  `ConcurrentHashMap<String, String>`.
- `fun isEnabled(key: ConfigurationKey): Boolean` reads the cache, parses `"true"`/
  `"false"`.
- `@Transactional fun update(key: ConfigurationKey, value: String)` upserts via the
  repository, then updates the cache entry — single-instance app, so no
  cross-instance cache invalidation is needed.

`StockPriceSyncScheduler.syncPrices()` gains one line at the top:

```kotlin
if (!configurationService.isEnabled(ConfigurationKey.STOCK_PRICE_SYNC_ENABLED)) {
    logger.info { "Stock price sync skipped: disabled via configuration" }
    return
}
```

`stockpricesync/` gains its first controller, calling the existing service directly
— no new logic in `StockPriceSyncService` itself, and deliberately **not** routed
through the scheduler (so it bypasses the enabled-check above):

```
stockpricesync/
  StockPriceSyncController.kt         # POST /stock-price-sync -> stockPriceSyncService.syncPrices()
  scheduler/
    StockPriceSyncScheduler.kt        # (existing, unchanged except for the guard above)
  services/
    StockPriceSyncService.kt          # (existing, unchanged)
  repositories/
    StockPriceSyncRepository.kt       # (existing, unchanged)
```

```kotlin
@RestController
@RequestMapping("/private/v1/stock-price-sync")
class StockPriceSyncController(
    private val stockPriceSyncService: StockPriceSyncService,
) {
    @PostMapping
    fun forceSync(): ResponseEntity<Void> {
        stockPriceSyncService.syncPrices()
        return ResponseEntity.noContent().build()
    }
}
```

`SecurityConfig.kt` gains one line alongside the existing per-method admin rules:

```kotlin
authorize(HttpMethod.POST, "/private/v1/stock-price-sync/**", hasAuthority("ROLE_ADMIN"))
```

### Client

```
src/api/configurations.ts       # getConfigurations(), updateConfiguration(key, value)
src/api/stockPriceSync.ts       # forceSync(): Promise<void>
src/stores/configurations.ts    # Pinia store: configurations map, load(), updateConfiguration()
src/types.ts                    # + ConfigurationResponse { key, value, updatedAt }
src/views/SettingsView.vue      # + 4th Card: "Sincronização automática"
```

`configurations` store follows the existing pessimistic-update pattern (await the
API, then update local state) used by every other store. The new `Card` in
`SettingsView.vue` renders a `b-switch` bound via computed `get`/`set` to the
`stock_price_sync_enabled` entry, calling `configurationsStore.updateConfiguration(
'stock_price_sync_enabled', value ? 'true' : 'false')` on change, with a success
toast (matching the existing Settings toast pattern for other mutations).

Below the switch, in the same `Card`, a "Atualizar preços agora" button calls
`stockPriceSyncApi.forceSync()` directly from `SettingsView.vue` — no dedicated
store, since there's no persisted state to hold beyond a transient `triggeringSync`
loading ref. The button uses Buefy's `loading` prop while the request is in flight
(the request can take a while — one sequential HTTP call per distinct ticker) and
fires a success/error toast on completion, same pattern as the other Settings
mutations. No client-side gate beyond the page-level admin-only router guard that
already covers all of `/settings`.

## Files to create / change

| Path | Action |
| --- | --- |
| `server/src/main/resources/db/changelog/changes/2026/08/<DD-HHMM>-add-configurations-table.xml` | **create** |
| `server/src/main/resources/db/changelog/db.changelog-master.xml` | **update** — `<include>` the new file |
| `server/src/main/kotlin/br/com/investlog/server/configurations/ConfigurationController.kt` | **create** |
| `server/src/main/kotlin/br/com/investlog/server/configurations/ConfigurationKey.kt` | **create** |
| `server/src/main/kotlin/br/com/investlog/server/configurations/services/ConfigurationService.kt` | **create** |
| `server/src/main/kotlin/br/com/investlog/server/configurations/repositories/ConfigurationRepository.kt` | **create** |
| `server/src/main/kotlin/br/com/investlog/server/configurations/rest/payloads/ConfigurationResponse.kt` | **create** |
| `server/src/main/kotlin/br/com/investlog/server/configurations/rest/payloads/ConfigurationUpdateRequest.kt` | **create** |
| `server/src/main/kotlin/br/com/investlog/server/stockpricesync/scheduler/StockPriceSyncScheduler.kt` | **update** — add the disabled-check guard |
| `server/src/main/kotlin/br/com/investlog/server/stockpricesync/StockPriceSyncController.kt` | **create** — `POST /stock-price-sync` force-trigger |
| `server/src/main/kotlin/br/com/investlog/server/config/core/SecurityConfig.kt` | **update** — `ROLE_ADMIN` gate for `POST /stock-price-sync/**` |
| `client/src/api/configurations.ts` | **create** |
| `client/src/api/stockPriceSync.ts` | **create** — `forceSync()` |
| `client/src/stores/configurations.ts` | **create** |
| `client/src/types.ts` | **update** — add `ConfigurationResponse` |
| `client/src/views/SettingsView.vue` | **update** — add the toggle + force-sync button `Card` |

Per root `CLAUDE.md`, this splits into a **server PR** (migration + `configurations`
package + scheduler guard) and a **client PR** (api/store/view), each `Refs #<issue>`
against the tracking issue for this feature.

## Verification

- **Server unit/integration test**: with `stock_price_sync_enabled` set to `false`
  via the repository/service, call `StockPriceSyncScheduler.syncPrices()` directly
  (mirrors the existing WireMock-based scheduler test) and assert the `StocksClient`
  WireMock stub receives zero requests. With the flag `true` (default), existing
  scheduler test behavior is unchanged.
- **Server integration test**: `ConfigurationControllerTest` — `GET` returns the
  seeded row, `PATCH` as admin updates it and the cache reflects the new value on a
  subsequent `isEnabled()` call, `PATCH` as a non-admin session 403s.
- **Manual UI check**: as an admin, open Settings, toggle the switch off, refresh the
  page — the switch stays off (persisted, not just local state); confirm the next
  scheduled sync run is skipped per the server log line.
- **Server integration test**: `StockPriceSyncControllerTest` — `POST
  /stock-price-sync` as admin returns `204` and updates `stock_holdings.current_price`
  (via WireMock stub, same style as the existing scheduler test); as a non-admin
  session, `403`s. Also verify it succeeds even when `stock_price_sync_enabled` is
  `false` in the database, proving the force path is independent of the toggle.
- **Manual UI check**: with the toggle switched off, click "Atualizar preços agora" —
  prices update anyway; button shows a loading state for the duration and a success
  toast on completion.
