# Server REST API design

**Date:** 2026-06-14
**Scope:** `server/` only — REST controllers, services, and jOOQ-backed repositories over the
`system`/`finances` schema (see `2026-06-12-server-persistence-schema-design.md`), plus the
schema additions this surface requires (three new views, two new `system.users` columns).
**No client wiring and no auth implementation** — both explicit follow-ups.

## Goal

Expose the portfolio domain (wallets, stock/crypto/fund holdings + lots/contributions, editable
type lists, currency rates, user profile) as a REST API covering every read/write operation the
client's `portfolio` Pinia store currently performs in-memory (`client/src/stores/portfolio.ts`),
plus the cross-holding aggregates it derives (`totalsByType`, `grandInvestedBase`,
`currentSummary`, `cumulativeSeries`, `walletInvested`/`walletInvestedBase`) — computed
server-side via Postgres views instead of recomputed client-side over a full in-memory list.

## Current-user resolution

No Spring Security yet. A `CurrentUserProvider` resolves a single seeded dev user
(`system.users` row matched by a fixed `google_sub`, e.g. `'dev-user'`) for every request. Every
query is implicitly scoped to `user_id = currentUser.id`. Swapping this for real OAuth later is a
drop-in replacement of `CurrentUserProvider`, not a controller/service change.

## Out of scope

- Authentication/authorization (Spring Security, Google OAuth2).
- Client integration — wiring the Pinia store to these endpoints.
- Multi-currency *display* (showing totals in BRL **and** USD side-by-side). `preferred_currency`
  is stored now (point 6 below) but only used as the v1 conversion target (which currently
  coincides with the base currency); dual-currency display is a later phase.
- Market-data feeds — `current_price`/`current_value` remain manual fields.

## Cross-cutting conventions

### Identifiers
Every resource is addressed by its `external_id` (UUID) in the URL; internal `BIGINT id`s never
leave the server. Exception: `currency-rates` is addressed by `currencyCode` (e.g. `USD`) — it's
a natural, stable per-user key (`UNIQUE(user_id, currency_code)`), so a separate UUID would add
nothing.

### API versioning & path structure
Every endpoint lives under an access-level prefix plus a version segment: **`/private/v1/...`**
for everything in this spec. `/public/v1/...` is reserved for future unauthenticated routes
(health/info, future OAuth callbacks) — no routes defined there yet, since every current endpoint
is scoped to `CurrentUserProvider`'s user.

Versioning uses Spring Framework 7 / Spring Boot 4's built-in support
(`WebMvcConfigurer.configureApiVersioning`): `usePathSegment(1)` (the segment right after
`/private`/`/public`), `addSupportedVersions("v1")`, `setDefaultVersion("v1")`. If a second
version is ever needed, only the handler methods that change declare `version = "v2"` on their
mapping annotation — the rest of the surface stays on `v1`.

### Pagination — every collection endpoint
`stock-types`, `fund-types`, `currency-rates`, `wallets`, `holdings` accept standard Spring
`Pageable` query params (`page`, `size`, `sort`) and return
`org.springframework.data.web.PagedModel<T>`:
`{ content: [...], page: { size, number, totalElements, totalPages } }`. Default `size=20`.

jOOQ pagination pattern: `.limit(size).offset(page * size)` plus a separate `count(*)` query,
wrapped in `PageImpl` → `PagedModel`.

`Pageable`/`Page`/`PagedModel` live in `spring-data-commons`, which `spring-boot-starter-jooq`
does not pull in on its own. `build.gradle.kts` needs `org.springframework.data:spring-data-commons`
added directly — it brings the types plus Spring MVC's `Pageable` argument-resolver
auto-configuration, without pulling in a full Spring Data repository module.

**Aggregate endpoints are the exception**: `GET /private/v1/holdings/summary` and
`GET /private/v1/holdings/timeline` return a single object / a full unpaginated list — see
"Design decisions flagged for review" below.

### Error handling
`@RestControllerAdvice` translating to RFC 7807 `ProblemDetail`:
- unknown `external_id` → 404
- FK violation (e.g. deleting a `stock_type`/`fund_type` still referenced by a holding —
  `ON DELETE RESTRICT`) → 409
- unique violation (`UNIQUE(user_id, name)`, `UNIQUE(user_id, currency_code)`) → 409
- creating a holding under a wallet whose `kind` doesn't match the holding type (e.g.
  `POST /stock-holdings` targeting a `kind='crypto'` wallet) → 409
- `@Valid` body validation failure → 400

### JSON conventions
camelCase field names, dates as ISO `yyyy-MM-dd` strings, money/quantity as JSON numbers
(`NUMERIC` ↔ jOOQ `BigDecimal`). Spring Boot 4 runs on **Jackson 3** (`tools.jackson.*`): Spring
auto-configures a `tools.jackson.databind.json.JsonMapper` bean — the Jackson 3 replacement for
`com.fasterxml.jackson.databind.ObjectMapper`, which is deprecated and should not be constructed
or referenced directly. Inject the `JsonMapper` bean wherever JSON (de)serialization is needed
outside the request/response cycle (e.g. the `entries` jsonb column — see DTO shapes). The Kotlin
module is the Jackson-3 `jackson-module-kotlin` (`tools.jackson.module.kotlin`), auto-registered
alongside it.

Holding `kind` values in API responses match the existing `finances.wallet_kind` enum /
`WalletKind` (`stocks`/`crypto`/`funds`) — note this is plural, unlike the client's current
`Holding.kind` discriminant (`'stock'/'crypto'/'fund'`); reconciling that is a
client-integration-phase concern.

---

## Schema changes

Five new Liquibase changesets, dated `2026/06/14`, appended to `db.changelog-master.xml` after the
existing entries. **Existing changesets are untouched** — only new files/changesets are added.

### 1. `14-1000-add-user-preferences.xml` (point 6)

```sql
ALTER TABLE system.users
    ADD COLUMN accent_color TEXT NOT NULL DEFAULT 'teal'
        CHECK (accent_color IN ('blue', 'indigo', 'teal', 'green')),
    ADD COLUMN preferred_currency TEXT NOT NULL DEFAULT 'BRL';
```

`'teal'` matches the client's `appearance` store default (`AccentKey`); the `CHECK` mirrors the
four `data-accent` CSS variants (`client/src/types.ts`). `preferred_currency` isn't FK-constrained
to `currency_rates.currency_code` — a user could set a preference before configuring that rate.
The v1 summary endpoint converts to the `is_base` currency regardless (see point 6 discussion
below).

### 2. `14-1010-rebuild-holdings-overview-view.xml` (point 3 — resolves prior materialized-view staleness concern)

Drops the materialized view + its two indexes (regular views can't be indexed), recreates
`finances.holdings_overview` as a plain `VIEW` — always live, no `REFRESH` ever needed. Adds
three columns and nests each holding's lots/contributions as `jsonb` via `jsonb_agg`:

| Column | Notes |
|---|---|
| `external_id` | holding's own external id (unchanged) |
| `user_id` | **new** — internal FK via `wallets.user_id`, for `WHERE user_id = :currentUserId` |
| `wallet_id` | **new** — internal FK, for joins in `portfolio_summary`/`wallet_totals` |
| `wallet_external_id` | **new** — exposed as `walletId` in API responses, and as the `?walletId=` filter target |
| `kind`, `name`, `ticker`, `type_label`, `current_price` | unchanged |
| `quantity`, `cost_basis`, `current_value` | unchanged |
| `entries` | **new**, `jsonb NOT NULL DEFAULT '[]'` — per-holding lots/contributions, see below |

`entries` per branch:
- stock/crypto: `jsonb_agg(jsonb_build_object('id', sl.external_id, 'date', sl.lot_date, 'quantity', sl.quantity, 'price', sl.price) ORDER BY sl.lot_date)`
- funds: `jsonb_agg(jsonb_build_object('id', fc.external_id, 'date', fc.contribution_date, 'amount', fc.amount) ORDER BY fc.contribution_date)`
- both wrapped in `COALESCE(... FILTER (WHERE <child>.id IS NOT NULL), '[]'::jsonb)` so a holding
  with zero lots/contributions gets `[]` instead of `[null]`.

Example (stock branch, others follow the same shape):

```sql
SELECT
    sh.external_id, w.user_id, sh.wallet_id, w.external_id AS wallet_external_id,
    'stocks'::finances.wallet_kind AS kind, sh.name, sh.ticker, st.name AS type_label,
    sh.current_price,
    COALESCE(SUM(sl.quantity), 0) AS quantity,
    COALESCE(SUM(sl.quantity * sl.price), 0) AS cost_basis,
    sh.current_price * COALESCE(SUM(sl.quantity), 0) AS current_value,
    COALESCE(jsonb_agg(jsonb_build_object(
        'id', sl.external_id, 'date', sl.lot_date, 'quantity', sl.quantity, 'price', sl.price
    ) ORDER BY sl.lot_date) FILTER (WHERE sl.id IS NOT NULL), '[]'::jsonb) AS entries
FROM finances.stock_holdings sh
JOIN finances.wallets w ON w.id = sh.wallet_id
JOIN finances.stock_types st ON st.id = sh.stock_type_id
LEFT JOIN finances.stock_lots sl ON sl.stock_holding_id = sh.id
GROUP BY sh.id, sh.external_id, w.user_id, sh.wallet_id, w.external_id, sh.name, sh.ticker,
         st.name, sh.current_price
```

### 3. `14-1020-create-portfolio-summary-view.xml` (points 3, 4)

`finances.portfolio_summary`, grouped by `(user_id, kind)`. Joins `holdings_overview` →
`wallets` → `currency_rates` (`LEFT JOIN ... COALESCE(cr.rate, 1)`, so a wallet whose currency has
no configured rate still counts, at 1:1, rather than being silently dropped by an inner join):

| Column | Meaning |
|---|---|
| `user_id`, `kind` | group key |
| `wallet_count` | `COUNT(DISTINCT wallet_id)` |
| `holding_count` | `COUNT(*)` |
| `invested_base` | `SUM(cost_basis * rate)` — all holdings of this kind |
| `holdings_with_cv_count` | `COUNT(*) FILTER (WHERE current_value IS NOT NULL)` |
| `invested_with_cv_base` | same `SUM`, filtered to holdings with a current value |
| `current_value_base` | `SUM(current_value * rate) FILTER (WHERE current_value IS NOT NULL)` |

At most 3 rows per user. `GET /private/v1/holdings/summary` reads all rows for the current user
and sums them in the service for the "regardless of type" grand totals (point 4) — no extra view
needed for that part.

### 4. `14-1030-create-wallet-totals-view.xml`

`finances.wallet_totals`, grouped by `wallet_id` — the same `cost_basis`/`current_value`
aggregates as above, but per wallet, in both the wallet's own currency and base currency (same
`currency_rates` join). Feeds the enriched `GET /private/v1/wallets` response (`invested`,
`investedBase`, `currentValue`, `currentValueBase`, `holdingCount` per wallet) — covers
`walletInvested`/`walletInvestedBase`.

### 5. `14-1040-create-investment-events-view.xml`

`finances.investment_events` — `UNION ALL` of `stock_lots`/`crypto_lots`/`fund_contributions`,
each joined to its holding → wallet → `currency_rates`, producing one row per transaction:
`(user_id, kind, date, amount_base)`. Feeds `GET /private/v1/holdings/timeline` — covers
`cumulativeSeries`'s event list (`holdingEvents`). The existing monthly-bucketing/cumulative-sum
logic stays client-side, unchanged — this view supplies the raw `(date, amountBase)` events
instead of the client deriving them from the full in-memory `holdings` array. See "Design
decisions flagged for review" for the alternative considered.

---

## Endpoint map

All paths prefixed `/private/v1` (see "API versioning & path structure" above; paths below omit
the prefix for brevity). ✅ = new in this revision (points 1, 4, 6).

### Profile (point 6)

| Method | Path | Body | Response |
|---|---|---|---|
| GET | `/profile` | — | `{ name, email, avatarUrl, accentColor, preferredCurrency }` ✅ |
| PATCH | `/profile` | `{ accentColor?, preferredCurrency? }` | same as GET ✅ — `accentColor` validated via Bean Validation (`@Pattern`/enum, 400 on an unknown value); the DB `CHECK` is a backstop, not the primary validation path |

### Stock types / Fund types

| Method | Path | Body | Response |
|---|---|---|---|
| GET | `/stock-types` | — | `PagedModel<{ id, name }>` |
| POST | `/stock-types` | `{ name }` | `{ id, name }` (201) |
| DELETE | `/stock-types/{id}` | — | 204; 409 if a stock holding still references it |
| GET / POST / DELETE | `/fund-types...` | — | mirrors stock-types |

### Currency rates (point 5)

| Method | Path | Body | Response |
|---|---|---|---|
| GET | `/currency-rates` | — | `PagedModel<{ currencyCode, rate, isBase }>` |
| PUT | `/currency-rates/{currencyCode}` | `{ rate, isBase? }` | `{ currencyCode, rate, isBase }` — upsert; if `isBase: true`, the service clears the previous base row in the same transaction (partial-unique-index constraint) |

This table is the single source of truth for rates. `currencySymbol`/`currencies` (display
symbols, the list of currencies offered in the UI) remain a client-side presentation constant —
not user data, no table needed.

### Wallets

| Method | Path | Body | Response |
|---|---|---|---|
| GET | `/wallets` | — | `PagedModel<WalletDto>` — `{ id, name, kind, currency, holdingCount, invested, investedBase, currentValue, currentValueBase }` (last 5 fields from `wallet_totals`) |
| POST | `/wallets` | `{ name, kind, currency }` | `WalletDto` (201) |
| DELETE | `/wallets/{id}` | — | 204; cascades to its holdings (`ON DELETE CASCADE`) |

### Holdings — per kind (create / delete / lots / contributions)

| Method | Path | Body | Response |
|---|---|---|---|
| POST | `/stock-holdings` | `{ walletId, stockType, ticker, name, currentPrice?, lot: { date, quantity, price } }` | `StockHoldingDto` (201) |
| DELETE | `/stock-holdings/{id}` | — | 204 |
| POST | `/stock-holdings/{id}/lots` | `{ date, quantity, price }` | `LotDto` (201) |
| DELETE | `/stock-holdings/{id}/lots/{lotId}` | — | 204 ✅ |
| POST / DELETE | `/crypto-holdings...` | mirrors stock, no `stockType` field | |
| DELETE | `/crypto-holdings/{id}/lots/{lotId}` | — | 204 ✅ |
| POST | `/fund-holdings` | `{ walletId, fundType, name, currentValue?, contribution: { date, amount } }` | `FundHoldingDto` (201) |
| DELETE | `/fund-holdings/{id}` | — | 204 |
| POST | `/fund-holdings/{id}/contributions` | `{ date, amount }` | `ContributionDto` (201) |
| DELETE | `/fund-holdings/{id}/contributions/{contribId}` | — | 204 ✅ |

`name` is a required string (may be empty, matching `(name || '').trim()` on the client) — the
DB column is `NOT NULL` but has no length/blank constraint. Creating a holding always supplies its
first lot/contribution in the same call (matches `addStock`/`addCrypto`/`addFund` — there's no
"holding with zero lots" creation path; one can only arise later via point-1 deletes).

### Holdings — unified read + aggregates (points 3, 4)

| Method | Path | Query | Response |
|---|---|---|---|
| GET | `/holdings` | `walletId?`, `kind?`, page/size/sort | `PagedModel<StockHoldingDto \| CryptoHoldingDto \| FundHoldingDto>` (discriminated by `kind`), backed by `holdings_overview` |
| GET | `/holdings/summary` | — | `{ byType: [...], total: {...} }`, backed by `portfolio_summary` ✅ |
| GET | `/holdings/timeline` | — | `[{ date, kind, amountBase }]`, backed by `investment_events`, unpaginated ✅ |

`holdings/summary` response shape:

```json
{
  "byType": [
    { "kind": "stocks", "walletCount": 2, "holdingCount": 7, "invested": 12345.0,
      "holdingsWithCurrentValue": 7, "investedWithCurrentValue": 12345.0, "currentValue": 13800.0 },
    { "kind": "crypto", "...": "..." },
    { "kind": "funds", "...": "..." }
  ],
  "total": {
    "invested": 30000.0, "holdingCount": 11,
    "holdingsWithCurrentValue": 9, "investedWithCurrentValue": 28000.0,
    "currentValue": 31000.0, "gain": 3000.0, "gainPct": 10.71
  }
}
```

`byType[*]` rows come straight from `portfolio_summary`; `total` is the service-layer sum across
the (≤3) `byType` rows plus the derived `gain`/`gainPct` — covers `totalsByType` +
`grandInvestedBase` + `currentSummary` in one call. All monetary values are in the user's base
currency (`currency_rates.is_base`).

---

## DTO shapes

```kotlin
data class LotDto(val id: UUID, val date: LocalDate, val quantity: BigDecimal, val price: BigDecimal)
data class ContributionDto(val id: UUID, val date: LocalDate, val amount: BigDecimal)

data class StockHoldingDto(
    val id: UUID, val walletId: UUID, val kind: String = "stocks",
    val ticker: String, val name: String, val stockType: String,
    val currentPrice: BigDecimal?, val quantity: BigDecimal, val costBasis: BigDecimal,
    val currentValue: BigDecimal?, val lots: List<LotDto>,
)
// CryptoHoldingDto: identical, minus stockType
data class FundHoldingDto(
    val id: UUID, val walletId: UUID, val kind: String = "funds",
    val name: String, val fundType: String,
    val currentValue: BigDecimal?, val costBasis: BigDecimal,
    val contributions: List<ContributionDto>,
)
```

The `entries` jsonb column is deserialized into `List<LotDto>` or `List<ContributionDto>` based
on `kind` when mapping the jOOQ record to the response DTO. jOOQ maps Postgres `jsonb` to
`org.jooq.JSONB`; the mapper calls `.data()` to get the raw JSON string and passes it to the
injected `JsonMapper`'s `.readValue(...)` (Jackson 3 — see JSON conventions above) — no custom
jOOQ binding needed.

After the five new changesets land, `./gradlew generateJooq` regenerates sources for the new/
changed views automatically (it applies the full changelog to a throwaway Testcontainer) — no
extra wiring beyond what the persistence-schema spec already set up.

---

## Seed data updates

The dev-seed data gains:
- `system.users` dev row: `accent_color = 'teal'`, `preferred_currency = 'BRL'` (matches client
  defaults).
- `finances.currency_rates` seed rows mirroring `seed.ts`'s `config.rates`:
  `(BRL, 1, is_base=true)`, `(USD, 5.42, false)`, `(EUR, 5.88, false)`.

---

## Client selector → endpoint mapping (reference for future client integration)

| Store selector (`portfolio.ts`) | Endpoint |
|---|---|
| `holdings` (full list) | `GET /holdings` (paginated) |
| `holdingCost`/`holdingQty`/`holdingCurrentValue`/`holdingGain`/`holdingEvents` | derived from a single `GET /holdings` row — `quantity`/`costBasis`/`currentValue`/`lots`/`contributions` are already aggregated server-side |
| `walletHoldings(id)` | `GET /holdings?walletId=...` |
| `walletInvested`/`walletInvestedBase` | `GET /wallets` row (`invested`/`investedBase`) |
| `totalsByType` | `GET /holdings/summary` → `byType` |
| `grandInvestedBase`/`currentSummary` | `GET /holdings/summary` → `total` |
| `cumulativeSeries` | `GET /holdings/timeline`, client keeps its monthly-bucketing logic |
| `config.rates`/`setRate` | `GET`/`PUT /currency-rates` |
| `stockTypes`/`fundTypes` + add/remove | `GET/POST/DELETE /stock-types`, `/fund-types` |
| `appearance.accent` | `GET/PATCH /profile` → `accentColor` (replaces the localStorage-only accent; dark mode stays client-only — it's not a stored user preference) |

---

## Design decisions flagged for review

1. **`holdings/summary` and `holdings/timeline` are unpaginated**, unlike every other collection
   endpoint — they're analytical aggregates the client needs in full to compute totals/charts.
   The alternative — paginating `/holdings/timeline` and having the client reassemble all pages —
   defeats the purpose of the view. Recommend keeping these two as the exceptions to "pagination
   everywhere."
2. **`holdings/timeline` returns raw events, not pre-bucketed monthly totals.** A more
   "database-does-the-aggregation" version would use a window function
   (`SUM() OVER (ORDER BY month)`) to return ready-made cumulative monthly totals, but correctly
   filling months with zero transactions needs `generate_series` + gap-filling — meaningfully more
   complex. Recommend the simple events view + existing client-side bucketing for v1.
3. **`accent_color`/`preferred_currency` live on `system.users`**, not a separate
   `user_preferences` table — two columns don't justify a join. Revisit if more preferences
   accumulate.
4. **Currency conversion direction**: `currency_rates.rate` = "value of 1 unit of
   `currency_code` expressed in the base currency" — confirmed against `portfolio.ts`'s
   `toBase = (v, cur) => v * rate(cur)`. All view conversions are `amount * rate`; the `is_base`
   row always has `rate = 1`.
