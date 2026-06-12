# Server persistence schema design

**Date:** 2026-06-12
**Scope:** `server/` only. Database schema (Liquibase), datasource configuration, and jOOQ
code generation — so the data the client currently keeps in an in-session Pinia store can
eventually be persisted via an API. **No REST controllers/services/repositories, no client
changes, and no auth wiring in this spec** — those are explicit follow-ups.

## Goal

Model the InvestLog domain (wallets, stock/crypto/fund holdings with their lots/contributions,
editable type lists, and currency conversion rates — see `client/src/types.ts` and
`client/src/data/seed.ts`) as a normalized Postgres schema, with the project's `compose.yaml`
Postgres as the target database, and wire up jOOQ so typed query code can be generated from
that schema.

Also lay the groundwork for the planned Google SSO login (a `system.users` table with the
basic profile fields), without implementing authentication itself.

## Conventions

Applied to every table unless noted otherwise:

- `id BIGSERIAL PRIMARY KEY` — internal identifier, used for FKs/joins. **Never exposed.**
- `external_id UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE` — the identifier used in any
  future API. Postgres 13+ (project uses `postgres:17-alpine`) has `gen_random_uuid()` built
  in, no extension required.
- `created_at TIMESTAMPTZ NOT NULL DEFAULT now()` on every table.
- `updated_at TIMESTAMPTZ NOT NULL DEFAULT now()` on tables whose rows can be edited after
  creation (not on lot/contribution tables, which are append-only in the client today).
- Money/quantity/rate columns are unconstrained `NUMERIC` (exact decimals; crypto quantities
  need up to 8 decimal places per `client/src/composables/useFormat.ts`).
- All `user_id REFERENCES system.users(id)` foreign keys (on `wallets`, `stock_types`,
  `fund_types`, `currency_rates`) use the Postgres default (`NO ACTION`/`RESTRICT`) — a user
  row can't be deleted while it owns data. Cascading user deletion is an auth-spec concern.

## Schema layout

Three Postgres schemas:

- **`system`** — identity/profile data (`users`). Anything related to auth/account in the
  future also lands here.
- **`finances`** — the portfolio domain: wallets, holdings (split per kind), type lists,
  currency rates, and the consolidation materialized view.
- **`public`** — left for Liquibase's own tracking tables, renamed to snake_case (see
  Connection config below).

## Tables

### `system.users`

Basic Google SSO profile — prep for future auth, not implemented here.

| Column | Type | Notes |
|---|---|---|
| `id` | `BIGSERIAL PK` | |
| `external_id` | `UUID` | unique, default `gen_random_uuid()` |
| `google_sub` | `TEXT NOT NULL UNIQUE` | Google's stable subject claim |
| `email` | `TEXT NOT NULL UNIQUE` | |
| `name` | `TEXT NOT NULL` | |
| `avatar_url` | `TEXT` | nullable |
| `created_at` / `updated_at` | `TIMESTAMPTZ` | |

### `finances.wallets`

| Column | Type | Notes |
|---|---|---|
| `id`, `external_id`, `created_at`, `updated_at` | — | per conventions |
| `user_id` | `BIGINT NOT NULL REFERENCES system.users(id)` | |
| `name` | `TEXT NOT NULL` | |
| `kind` | `finances.wallet_kind NOT NULL` | enum: `'stocks'`, `'crypto'`, `'funds'` — matches `WalletKind` |
| `currency` | `TEXT NOT NULL` | ISO code, e.g. `BRL`/`USD`/`EUR` |

### `finances.stock_types` / `finances.fund_types`

The editable type lists from Configurações (`stockTypes`/`fundTypes` in the portfolio store).
Identical shape, one table per list:

| Column | Type | Notes |
|---|---|---|
| `id`, `external_id`, `created_at` | — | per conventions (no `updated_at` — renamed in place is fine via UPDATE but rows aren't otherwise mutated) |
| `user_id` | `BIGINT NOT NULL REFERENCES system.users(id)` | |
| `name` | `TEXT NOT NULL` | |

Constraint: `UNIQUE (user_id, name)`.

### `finances.currency_rates`

Mirrors `CurrencyConfig { base, rates }`.

| Column | Type | Notes |
|---|---|---|
| `id`, `external_id`, `created_at`, `updated_at` | — | per conventions |
| `user_id` | `BIGINT NOT NULL REFERENCES system.users(id)` | |
| `currency_code` | `TEXT NOT NULL` | e.g. `BRL`, `USD`, `EUR` |
| `rate` | `NUMERIC NOT NULL` | value of 1 unit of `currency_code` in the user's base currency |
| `is_base` | `BOOLEAN NOT NULL DEFAULT false` | exactly one row per user should be the base (rate = 1) |

Constraints: `UNIQUE (user_id, currency_code)`; a partial unique index
`(user_id) WHERE is_base` so at most one row per user can be marked base.

### `finances.stock_holdings` / `finances.stock_lots`

| `stock_holdings` column | Type | Notes |
|---|---|---|
| `id`, `external_id`, `created_at`, `updated_at` | — | per conventions |
| `wallet_id` | `BIGINT NOT NULL REFERENCES finances.wallets(id) ON DELETE CASCADE` | |
| `stock_type_id` | `BIGINT NOT NULL REFERENCES finances.stock_types(id) ON DELETE RESTRICT` | |
| `ticker` | `TEXT NOT NULL` | |
| `name` | `TEXT NOT NULL` | |
| `current_price` | `NUMERIC` | nullable — optional manual field |

| `stock_lots` column | Type | Notes |
|---|---|---|
| `id`, `external_id`, `created_at` | — | per conventions |
| `stock_holding_id` | `BIGINT NOT NULL REFERENCES finances.stock_holdings(id) ON DELETE CASCADE` | |
| `lot_date` | `DATE NOT NULL` | |
| `quantity` | `NUMERIC NOT NULL` | |
| `price` | `NUMERIC NOT NULL` | |

### `finances.crypto_holdings` / `finances.crypto_lots`

Same shape as stocks, minus a type sub-classification (crypto has no sub-type in the client
model):

| `crypto_holdings` column | Type | Notes |
|---|---|---|
| `id`, `external_id`, `created_at`, `updated_at` | — | per conventions |
| `wallet_id` | `BIGINT NOT NULL REFERENCES finances.wallets(id) ON DELETE CASCADE` | |
| `ticker` | `TEXT NOT NULL` | |
| `name` | `TEXT NOT NULL` | |
| `current_price` | `NUMERIC` | nullable |

| `crypto_lots` column | Type | Notes |
|---|---|---|
| `id`, `external_id`, `created_at` | — | per conventions |
| `crypto_holding_id` | `BIGINT NOT NULL REFERENCES finances.crypto_holdings(id) ON DELETE CASCADE` | |
| `lot_date` | `DATE NOT NULL` | |
| `quantity` | `NUMERIC NOT NULL` | |
| `price` | `NUMERIC NOT NULL` | |

### `finances.fund_holdings` / `finances.fund_contributions`

| `fund_holdings` column | Type | Notes |
|---|---|---|
| `id`, `external_id`, `created_at`, `updated_at` | — | per conventions |
| `wallet_id` | `BIGINT NOT NULL REFERENCES finances.wallets(id) ON DELETE CASCADE` | |
| `fund_type_id` | `BIGINT NOT NULL REFERENCES finances.fund_types(id) ON DELETE RESTRICT` | |
| `name` | `TEXT NOT NULL` | |
| `current_value` | `NUMERIC` | nullable — optional manual field |

| `fund_contributions` column | Type | Notes |
|---|---|---|
| `id`, `external_id`, `created_at` | — | per conventions |
| `fund_holding_id` | `BIGINT NOT NULL REFERENCES finances.fund_holdings(id) ON DELETE CASCADE` | |
| `contribution_date` | `DATE NOT NULL` | |
| `amount` | `NUMERIC NOT NULL` | |

### `finances.wallet_kind` (enum type)

`CREATE TYPE finances.wallet_kind AS ENUM ('stocks', 'crypto', 'funds')`.

## Consolidation: `finances.holdings_overview` (materialized view)

A `UNION ALL` across the three holding kinds, each joined to its lot/contribution table and
aggregated, producing one row per holding:

| Column | Source |
|---|---|
| `external_id` | the holding's own `external_id` |
| `wallet_id` | the owning wallet's internal `id` |
| `kind` | literal `'stock'` / `'crypto'` / `'fund'` |
| `name` | holding name |
| `ticker` | stock/crypto ticker, `NULL` for funds |
| `type_label` | `stock_types.name` / `fund_types.name`, `NULL` for crypto |
| `current_price` | stock/crypto manual field, `NULL` for funds |
| `quantity` | `SUM(lots.quantity)`, `NULL` for funds |
| `cost_basis` | `SUM(lots.quantity * lots.price)` or `SUM(contributions.amount)` |
| `current_value` | `current_price * quantity` for stock/crypto, `fund_holdings.current_value` for funds |

A unique index on `external_id` enables `REFRESH MATERIALIZED VIEW CONCURRENTLY`.

**Refresh strategy is not part of this spec.** No triggers are created; refreshing
(after writes, on a schedule, or on read) is an API-layer decision for the follow-up spec.

## Connection config

### `application.yaml` (base, all environments)

```yaml
spring:
  application:
    name: server
  liquibase:
    change-log: classpath:db/changelog/db.changelog-master.xml
    database-change-log-table: database_changelog
    database-change-log-lock-table: database_changelog_lock
```

No `spring.datasource.*` here. For local `bootRun`, the existing
`developmentOnly("org.springframework.boot:spring-boot-docker-compose")` dependency detects
the `compose.yaml` Postgres service and auto-configures the datasource — this already works
today, just unused until now. Liquibase runs against that datasource on startup.

### `application-prod.yaml`

A staged-but-empty `application-prod.yaml` (and `application-dev.yaml`) already exist in the
repo (just `spring.application.name`). This spec fills in `application-prod.yaml`'s datasource
block; `application-dev.yaml` needs no datasource entry since `bootRun` relies on the
docker-compose auto-detected connection.

```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST}:${DB_PORT:5432}/${DB_NAME}
    username: ${DB_USER}
    password: ${DB_PASSWORD}
```

`spring-boot-docker-compose` is `developmentOnly`, so it's absent from the production jar —
no need to explicitly disable it for prod.

## Liquibase changelog layout

XML format, organized by the date each changeset was authored:

```
src/main/resources/db/changelog/
  db.changelog-master.xml
  changes/
    2026/06/
      12-1000-create-schemas.xml          (CREATE SCHEMA system, finances)
      12-1010-create-wallet-kind-enum.xml (finances.wallet_kind)
      12-1020-create-system-users.xml
      12-1030-create-finances-core.xml    (wallets, stock_types, fund_types, currency_rates)
      12-1040-create-finances-holdings.xml (stock/crypto/fund holdings + lots/contributions)
      12-1050-create-holdings-overview-view.xml
```

`db.changelog-master.xml` `<include>`s each file in the order above, using
`relativeToChangelogFile="false"` paths rooted at `db/changelog/changes/...`. Future
changesets follow the same `changes/YYYY/MM/DD-HHMM-<description>.xml` pattern.

## jOOQ code generation

jOOQ needs to introspect a real Postgres to handle schemas, the enum type, and the
materialized view correctly — generic in-memory databases don't support all of that.

- Add the `nu.studer.jooq` Gradle plugin.
- Add a `generateJooq` Gradle task that:
  1. Starts a throwaway `postgres:17-alpine` **Testcontainer** (same image as
     `compose.yaml`/tests).
  2. Applies `db.changelog-master.xml` to it programmatically via `liquibase-core` (already on
     the classpath through `spring-boot-starter-liquibase`).
  3. Points jOOQ codegen's JDBC connection at that container.
  4. Generates Kotlin sources for the `system` and `finances` schemas into
     `build/generated-sources/jooq/main`, base package `br.com.investlog.server.jooq`.
  5. Stops the container.
- `generateJooq` is wired as a dependency of `compileKotlin`, so `./gradlew build` / `bootRun`
  regenerate sources whenever the changelog changes — no running DB or `docker compose up`
  required during a build.
- The generated sources directory is added to `.gitignore` (not checked in).

## Out of scope / follow-ups

- REST controllers, services, and repositories (jOOQ DSL usage against the generated code).
- `holdings_overview` refresh strategy (trigger, scheduled job, or on-demand).
- Seed/demo data for local development.
- Client integration (explicitly excluded — client is read-only context for this work).
- Auth/Security wiring (Spring Security + Google OAuth2 against `system.users`).
