# Server Persistence Schema Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Liquibase-managed Postgres schema migrations (the `system`/`finances` schemas, `finances.wallet_kind` enum, all portfolio tables, and the `finances.holdings_overview` materialized view), wire up jOOQ Kotlin code generation against that schema via a Testcontainers-backed Gradle task, and configure the Spring Liquibase/datasource connection settings for dev and prod — implementing `docs/superpowers/specs/2026-06-12-server-persistence-schema-design.md` end to end.

**Architecture:** Six dated Liquibase XML changelog files (raw `<sql>` + `<rollback>` changesets, one DDL statement per changeset) under `server/src/main/resources/db/changelog/changes/2026/06/`, included in order by a master changelog. A new `nu.studer.jooq` Gradle plugin adds a `generateJooq` task; a `startJooqDb` task (depended on by `generateJooq`) starts a throwaway `postgres:17-alpine` Testcontainer, applies the master changelog via `liquibase-core`, and points the jOOQ `KotlinGenerator` at that container for the `system` and `finances` schemas. A `stopJooqDb` task (registered as `finalizedBy` on `generateJooq`) tears the container down. The plugin auto-wires `generateJooq`'s output into the `main` source set, so `compileKotlin`/`./gradlew build`/`bootRun` regenerate sources automatically — no manual `docker compose up` needed for a build. `application.yaml` configures Liquibase's changelog location and renamed tracking tables; `application-prod.yaml` adds the `spring.datasource` block for real deployments (local `bootRun` keeps relying on `spring-boot-docker-compose` auto-detecting `compose.yaml`'s Postgres).

**Tech Stack:** Kotlin 2.3.21 / Spring Boot 4.1.0 (JVM 25 toolchain), PostgreSQL 17 (`postgres:17-alpine`), `org.liquibase:liquibase-core:5.0.3` (XML changelogs), `org.jooq:jooq:3.21.5` runtime + `KotlinGenerator`, `nu.studer.jooq` Gradle plugin `10.2.1` (bundles `jooq-codegen`/`jooq-meta-kotlin:3.20.11` for the codegen DSL), `org.testcontainers:testcontainers-postgresql:2.0.5` for the codegen-time database.

**Prerequisite:** Docker must be running locally for `./gradlew generateJooq` and `./gradlew build` (Task 2 and Task 3 verification) — both start a `postgres:17-alpine` Testcontainer. These commands were *not* executed during planning (Docker was unavailable in the planning environment); the expected output below is what a successful run produces.

---

## File Structure

- Create: `server/src/main/resources/db/changelog/db.changelog-master.xml` — master changelog, includes the 6 dated changesets below in order.
- Create: `server/src/main/resources/db/changelog/changes/2026/06/12-1000-create-schemas.xml` — `system`/`finances` schemas.
- Create: `server/src/main/resources/db/changelog/changes/2026/06/12-1010-create-wallet-kind-enum.xml` — `finances.wallet_kind` enum.
- Create: `server/src/main/resources/db/changelog/changes/2026/06/12-1020-create-system-users.xml` — `system.users`.
- Create: `server/src/main/resources/db/changelog/changes/2026/06/12-1030-create-finances-core.xml` — `finances.wallets`, `stock_types`, `fund_types`, `currency_rates`.
- Create: `server/src/main/resources/db/changelog/changes/2026/06/12-1040-create-finances-holdings.xml` — stock/crypto/fund holdings + lots/contributions.
- Create: `server/src/main/resources/db/changelog/changes/2026/06/12-1050-create-holdings-overview-view.xml` — `finances.holdings_overview` materialized view + indexes.
- Modify: `server/build.gradle.kts` — `nu.studer.jooq` plugin, `jooq {}` codegen config, Testcontainers + Liquibase task wiring (`startJooqDb`/`stopJooqDb`/`generateJooq`).
- Modify: `server/src/main/resources/application.yaml` — Liquibase changelog path + renamed tracking tables.
- Modify: `server/src/main/resources/application-prod.yaml` — `spring.datasource` block.

---

## Task 1: Liquibase changelog files

**Files:**
- Create: `server/src/main/resources/db/changelog/changes/2026/06/12-1000-create-schemas.xml`
- Create: `server/src/main/resources/db/changelog/changes/2026/06/12-1010-create-wallet-kind-enum.xml`
- Create: `server/src/main/resources/db/changelog/changes/2026/06/12-1020-create-system-users.xml`
- Create: `server/src/main/resources/db/changelog/changes/2026/06/12-1030-create-finances-core.xml`
- Create: `server/src/main/resources/db/changelog/changes/2026/06/12-1040-create-finances-holdings.xml`
- Create: `server/src/main/resources/db/changelog/changes/2026/06/12-1050-create-holdings-overview-view.xml`
- Create: `server/src/main/resources/db/changelog/db.changelog-master.xml`

All changesets use raw `<sql>` + `<rollback><sql>` (not XML changeType abstractions), one DDL statement per changeset, per the spec's "Liquibase changelog layout" section. Every file uses the same XSD:

```xml
xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
    http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-5.0.xsd"
```

- [ ] **Step 1: Create `12-1000-create-schemas.xml` (system + finances schemas)**

Create `server/src/main/resources/db/changelog/changes/2026/06/12-1000-create-schemas.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
    xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-5.0.xsd">

    <changeSet id="12-1000-1" author="investlog">
        <sql>CREATE SCHEMA system;</sql>
        <rollback>
            <sql>DROP SCHEMA system;</sql>
        </rollback>
    </changeSet>

    <changeSet id="12-1000-2" author="investlog">
        <sql>CREATE SCHEMA finances;</sql>
        <rollback>
            <sql>DROP SCHEMA finances;</sql>
        </rollback>
    </changeSet>

</databaseChangeLog>
```

- [ ] **Step 2: Create `12-1010-create-wallet-kind-enum.xml` (`finances.wallet_kind` enum)**

Create `server/src/main/resources/db/changelog/changes/2026/06/12-1010-create-wallet-kind-enum.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
    xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-5.0.xsd">

    <changeSet id="12-1010-1" author="investlog">
        <sql>CREATE TYPE finances.wallet_kind AS ENUM ('stocks', 'crypto', 'funds');</sql>
        <rollback>
            <sql>DROP TYPE finances.wallet_kind;</sql>
        </rollback>
    </changeSet>

</databaseChangeLog>
```

- [ ] **Step 3: Create `12-1020-create-system-users.xml` (`system.users`)**

Create `server/src/main/resources/db/changelog/changes/2026/06/12-1020-create-system-users.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
    xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-5.0.xsd">

    <changeSet id="12-1020-1" author="investlog">
        <sql>
            CREATE TABLE system.users (
                id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                external_id UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
                google_sub TEXT NOT NULL UNIQUE,
                email TEXT NOT NULL UNIQUE,
                name TEXT NOT NULL,
                avatar_url TEXT,
                created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
            );
        </sql>
        <rollback>
            <sql>DROP TABLE system.users;</sql>
        </rollback>
    </changeSet>

</databaseChangeLog>
```

- [ ] **Step 4: Create `12-1030-create-finances-core.xml` (wallets, stock_types, fund_types, currency_rates)**

Create `server/src/main/resources/db/changelog/changes/2026/06/12-1030-create-finances-core.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
    xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-5.0.xsd">

    <changeSet id="12-1030-1" author="investlog">
        <sql>
            CREATE TABLE finances.wallets (
                id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                external_id UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
                user_id BIGINT NOT NULL REFERENCES system.users(id),
                name TEXT NOT NULL,
                kind finances.wallet_kind NOT NULL,
                currency TEXT NOT NULL,
                created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
            );
        </sql>
        <rollback>
            <sql>DROP TABLE finances.wallets;</sql>
        </rollback>
    </changeSet>

    <changeSet id="12-1030-2" author="investlog">
        <sql>CREATE INDEX idx_wallets_user_id ON finances.wallets (user_id);</sql>
        <rollback>
            <sql>DROP INDEX finances.idx_wallets_user_id;</sql>
        </rollback>
    </changeSet>

    <changeSet id="12-1030-3" author="investlog">
        <sql>
            CREATE TABLE finances.stock_types (
                id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                external_id UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
                user_id BIGINT NOT NULL REFERENCES system.users(id),
                name TEXT NOT NULL,
                created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                UNIQUE (user_id, name)
            );
        </sql>
        <rollback>
            <sql>DROP TABLE finances.stock_types;</sql>
        </rollback>
    </changeSet>

    <changeSet id="12-1030-4" author="investlog">
        <sql>
            CREATE TABLE finances.fund_types (
                id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                external_id UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
                user_id BIGINT NOT NULL REFERENCES system.users(id),
                name TEXT NOT NULL,
                created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                UNIQUE (user_id, name)
            );
        </sql>
        <rollback>
            <sql>DROP TABLE finances.fund_types;</sql>
        </rollback>
    </changeSet>

    <changeSet id="12-1030-5" author="investlog">
        <sql>
            CREATE TABLE finances.currency_rates (
                id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                external_id UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
                user_id BIGINT NOT NULL REFERENCES system.users(id),
                currency_code TEXT NOT NULL,
                rate NUMERIC NOT NULL CHECK (rate > 0),
                is_base BOOLEAN NOT NULL DEFAULT false,
                created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                UNIQUE (user_id, currency_code)
            );
        </sql>
        <rollback>
            <sql>DROP TABLE finances.currency_rates;</sql>
        </rollback>
    </changeSet>

    <changeSet id="12-1030-6" author="investlog">
        <sql>CREATE UNIQUE INDEX uq_currency_rates_user_base ON finances.currency_rates (user_id) WHERE is_base;</sql>
        <rollback>
            <sql>DROP INDEX finances.uq_currency_rates_user_base;</sql>
        </rollback>
    </changeSet>

</databaseChangeLog>
```

- [ ] **Step 5: Create `12-1040-create-finances-holdings.xml` (stock/crypto/fund holdings + lots/contributions)**

Create `server/src/main/resources/db/changelog/changes/2026/06/12-1040-create-finances-holdings.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
    xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-5.0.xsd">

    <changeSet id="12-1040-1" author="investlog">
        <sql>
            CREATE TABLE finances.stock_holdings (
                id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                external_id UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
                wallet_id BIGINT NOT NULL REFERENCES finances.wallets(id) ON DELETE CASCADE,
                stock_type_id BIGINT NOT NULL REFERENCES finances.stock_types(id) ON DELETE RESTRICT,
                ticker TEXT NOT NULL,
                name TEXT NOT NULL,
                current_price NUMERIC CHECK (current_price >= 0),
                created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
            );
        </sql>
        <rollback>
            <sql>DROP TABLE finances.stock_holdings;</sql>
        </rollback>
    </changeSet>

    <changeSet id="12-1040-2" author="investlog">
        <sql>CREATE INDEX idx_stock_holdings_wallet_id ON finances.stock_holdings (wallet_id);</sql>
        <rollback>
            <sql>DROP INDEX finances.idx_stock_holdings_wallet_id;</sql>
        </rollback>
    </changeSet>

    <changeSet id="12-1040-3" author="investlog">
        <sql>CREATE INDEX idx_stock_holdings_stock_type_id ON finances.stock_holdings (stock_type_id);</sql>
        <rollback>
            <sql>DROP INDEX finances.idx_stock_holdings_stock_type_id;</sql>
        </rollback>
    </changeSet>

    <changeSet id="12-1040-4" author="investlog">
        <sql>
            CREATE TABLE finances.stock_lots (
                id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                external_id UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
                stock_holding_id BIGINT NOT NULL REFERENCES finances.stock_holdings(id) ON DELETE CASCADE,
                lot_date DATE NOT NULL,
                quantity NUMERIC NOT NULL CHECK (quantity > 0),
                price NUMERIC NOT NULL CHECK (price >= 0),
                created_at TIMESTAMPTZ NOT NULL DEFAULT now()
            );
        </sql>
        <rollback>
            <sql>DROP TABLE finances.stock_lots;</sql>
        </rollback>
    </changeSet>

    <changeSet id="12-1040-5" author="investlog">
        <sql>CREATE INDEX idx_stock_lots_stock_holding_id ON finances.stock_lots (stock_holding_id);</sql>
        <rollback>
            <sql>DROP INDEX finances.idx_stock_lots_stock_holding_id;</sql>
        </rollback>
    </changeSet>

    <changeSet id="12-1040-6" author="investlog">
        <sql>
            CREATE TABLE finances.crypto_holdings (
                id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                external_id UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
                wallet_id BIGINT NOT NULL REFERENCES finances.wallets(id) ON DELETE CASCADE,
                ticker TEXT NOT NULL,
                name TEXT NOT NULL,
                current_price NUMERIC CHECK (current_price >= 0),
                created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
            );
        </sql>
        <rollback>
            <sql>DROP TABLE finances.crypto_holdings;</sql>
        </rollback>
    </changeSet>

    <changeSet id="12-1040-7" author="investlog">
        <sql>CREATE INDEX idx_crypto_holdings_wallet_id ON finances.crypto_holdings (wallet_id);</sql>
        <rollback>
            <sql>DROP INDEX finances.idx_crypto_holdings_wallet_id;</sql>
        </rollback>
    </changeSet>

    <changeSet id="12-1040-8" author="investlog">
        <sql>
            CREATE TABLE finances.crypto_lots (
                id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                external_id UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
                crypto_holding_id BIGINT NOT NULL REFERENCES finances.crypto_holdings(id) ON DELETE CASCADE,
                lot_date DATE NOT NULL,
                quantity NUMERIC NOT NULL CHECK (quantity > 0),
                price NUMERIC NOT NULL CHECK (price >= 0),
                created_at TIMESTAMPTZ NOT NULL DEFAULT now()
            );
        </sql>
        <rollback>
            <sql>DROP TABLE finances.crypto_lots;</sql>
        </rollback>
    </changeSet>

    <changeSet id="12-1040-9" author="investlog">
        <sql>CREATE INDEX idx_crypto_lots_crypto_holding_id ON finances.crypto_lots (crypto_holding_id);</sql>
        <rollback>
            <sql>DROP INDEX finances.idx_crypto_lots_crypto_holding_id;</sql>
        </rollback>
    </changeSet>

    <changeSet id="12-1040-10" author="investlog">
        <sql>
            CREATE TABLE finances.fund_holdings (
                id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                external_id UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
                wallet_id BIGINT NOT NULL REFERENCES finances.wallets(id) ON DELETE CASCADE,
                fund_type_id BIGINT NOT NULL REFERENCES finances.fund_types(id) ON DELETE RESTRICT,
                name TEXT NOT NULL,
                current_value NUMERIC CHECK (current_value >= 0),
                created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
            );
        </sql>
        <rollback>
            <sql>DROP TABLE finances.fund_holdings;</sql>
        </rollback>
    </changeSet>

    <changeSet id="12-1040-11" author="investlog">
        <sql>CREATE INDEX idx_fund_holdings_wallet_id ON finances.fund_holdings (wallet_id);</sql>
        <rollback>
            <sql>DROP INDEX finances.idx_fund_holdings_wallet_id;</sql>
        </rollback>
    </changeSet>

    <changeSet id="12-1040-12" author="investlog">
        <sql>CREATE INDEX idx_fund_holdings_fund_type_id ON finances.fund_holdings (fund_type_id);</sql>
        <rollback>
            <sql>DROP INDEX finances.idx_fund_holdings_fund_type_id;</sql>
        </rollback>
    </changeSet>

    <changeSet id="12-1040-13" author="investlog">
        <sql>
            CREATE TABLE finances.fund_contributions (
                id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                external_id UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
                fund_holding_id BIGINT NOT NULL REFERENCES finances.fund_holdings(id) ON DELETE CASCADE,
                contribution_date DATE NOT NULL,
                amount NUMERIC NOT NULL CHECK (amount > 0),
                created_at TIMESTAMPTZ NOT NULL DEFAULT now()
            );
        </sql>
        <rollback>
            <sql>DROP TABLE finances.fund_contributions;</sql>
        </rollback>
    </changeSet>

    <changeSet id="12-1040-14" author="investlog">
        <sql>CREATE INDEX idx_fund_contributions_fund_holding_id ON finances.fund_contributions (fund_holding_id);</sql>
        <rollback>
            <sql>DROP INDEX finances.idx_fund_contributions_fund_holding_id;</sql>
        </rollback>
    </changeSet>

</databaseChangeLog>
```

- [ ] **Step 6: Create `12-1050-create-holdings-overview-view.xml` (materialized view + indexes)**

Create `server/src/main/resources/db/changelog/changes/2026/06/12-1050-create-holdings-overview-view.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
    xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-5.0.xsd">

    <changeSet id="12-1050-1" author="investlog">
        <sql>
            CREATE MATERIALIZED VIEW finances.holdings_overview AS
            SELECT
                sh.external_id,
                sh.wallet_id,
                'stocks'::finances.wallet_kind AS kind,
                sh.name,
                sh.ticker,
                st.name AS type_label,
                sh.current_price,
                COALESCE(SUM(sl.quantity), 0) AS quantity,
                COALESCE(SUM(sl.quantity * sl.price), 0) AS cost_basis,
                sh.current_price * COALESCE(SUM(sl.quantity), 0) AS current_value
            FROM finances.stock_holdings sh
            JOIN finances.stock_types st ON st.id = sh.stock_type_id
            LEFT JOIN finances.stock_lots sl ON sl.stock_holding_id = sh.id
            GROUP BY sh.id, sh.external_id, sh.wallet_id, sh.name, sh.ticker, st.name, sh.current_price

            UNION ALL

            SELECT
                ch.external_id,
                ch.wallet_id,
                'crypto'::finances.wallet_kind AS kind,
                ch.name,
                ch.ticker,
                NULL::TEXT AS type_label,
                ch.current_price,
                COALESCE(SUM(cl.quantity), 0) AS quantity,
                COALESCE(SUM(cl.quantity * cl.price), 0) AS cost_basis,
                ch.current_price * COALESCE(SUM(cl.quantity), 0) AS current_value
            FROM finances.crypto_holdings ch
            LEFT JOIN finances.crypto_lots cl ON cl.crypto_holding_id = ch.id
            GROUP BY ch.id, ch.external_id, ch.wallet_id, ch.name, ch.ticker, ch.current_price

            UNION ALL

            SELECT
                fh.external_id,
                fh.wallet_id,
                'funds'::finances.wallet_kind AS kind,
                fh.name,
                NULL::TEXT AS ticker,
                ft.name AS type_label,
                NULL::NUMERIC AS current_price,
                NULL::NUMERIC AS quantity,
                COALESCE(SUM(fc.amount), 0) AS cost_basis,
                fh.current_value
            FROM finances.fund_holdings fh
            JOIN finances.fund_types ft ON ft.id = fh.fund_type_id
            LEFT JOIN finances.fund_contributions fc ON fc.fund_holding_id = fh.id
            GROUP BY fh.id, fh.external_id, fh.wallet_id, fh.name, ft.name, fh.current_value;
        </sql>
        <rollback>
            <sql>DROP MATERIALIZED VIEW finances.holdings_overview;</sql>
        </rollback>
    </changeSet>

    <changeSet id="12-1050-2" author="investlog">
        <sql>CREATE UNIQUE INDEX uq_holdings_overview_external_id ON finances.holdings_overview (external_id);</sql>
        <rollback>
            <sql>DROP INDEX finances.uq_holdings_overview_external_id;</sql>
        </rollback>
    </changeSet>

    <changeSet id="12-1050-3" author="investlog">
        <sql>CREATE INDEX idx_holdings_overview_wallet_id ON finances.holdings_overview (wallet_id);</sql>
        <rollback>
            <sql>DROP INDEX finances.idx_holdings_overview_wallet_id;</sql>
        </rollback>
    </changeSet>

</databaseChangeLog>
```

- [ ] **Step 7: Create the master changelog**

Create `server/src/main/resources/db/changelog/db.changelog-master.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
    xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-5.0.xsd">

    <include file="db/changelog/changes/2026/06/12-1000-create-schemas.xml" relativeToChangelogFile="false"/>
    <include file="db/changelog/changes/2026/06/12-1010-create-wallet-kind-enum.xml" relativeToChangelogFile="false"/>
    <include file="db/changelog/changes/2026/06/12-1020-create-system-users.xml" relativeToChangelogFile="false"/>
    <include file="db/changelog/changes/2026/06/12-1030-create-finances-core.xml" relativeToChangelogFile="false"/>
    <include file="db/changelog/changes/2026/06/12-1040-create-finances-holdings.xml" relativeToChangelogFile="false"/>
    <include file="db/changelog/changes/2026/06/12-1050-create-holdings-overview-view.xml" relativeToChangelogFile="false"/>

</databaseChangeLog>
```

- [ ] **Step 8: Commit**

```bash
git add server/src/main/resources/db/changelog
git commit -m "Add Liquibase changelog for system/finances persistence schema"
```

Note: these changelogs are not runnable in isolation yet — Task 2 wires up a `generateJooq` task that applies this changelog to a real Postgres via Liquibase and is the first point they get executed.

---

## Task 2: jOOQ codegen + Testcontainers wiring in `build.gradle.kts`

**Files:**
- Modify: `server/build.gradle.kts`

This task adds the `nu.studer.jooq` plugin, a `jooq {}` codegen configuration targeting the `system`/`finances` schemas with the `KotlinGenerator`, and `startJooqDb`/`stopJooqDb` tasks that wrap `generateJooq`: start a `postgres:17-alpine` Testcontainer, apply `db.changelog-master.xml` via `liquibase-core`, point jOOQ's JDBC connection at the container, run codegen, then stop the container.

- [ ] **Step 1: Add imports and the `buildscript` classpath block**

In `server/build.gradle.kts`, the file currently starts with:

```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_3

plugins {
```

Replace it with:

```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_3
import liquibase.Liquibase
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.DirectoryResourceAccessor
import nu.studer.gradle.jooq.JooqEdition
import org.jooq.meta.kotlin.*
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.io.File
import java.sql.DriverManager

buildscript {
	repositories {
		mavenCentral()
	}
	dependencies {
		classpath("org.testcontainers:testcontainers-postgresql:2.0.5")
		classpath("org.liquibase:liquibase-core:5.0.3")
		classpath("org.postgresql:postgresql:42.7.11")
	}
}

plugins {
```

These `buildscript` dependencies make Testcontainers/Liquibase/the Postgres JDBC driver available to the build script itself (separate from the project's `dependencies {}`, which only affects compile/runtime/test classpaths). `org.jooq.meta.kotlin.*` and `nu.studer.gradle.jooq.JooqEdition` come from the `nu.studer.jooq` plugin's own classpath (added in the next step), which transitively brings in `org.jooq:jooq-meta-kotlin:3.20.11`.

- [ ] **Step 2: Add the `nu.studer.jooq` plugin**

In `server/build.gradle.kts`, find:

```kotlin
	id("org.springframework.boot") version "4.1.0"
	id("io.spring.dependency-management") version "1.1.7"
}
```

Replace with:

```kotlin
	id("org.springframework.boot") version "4.1.0"
	id("io.spring.dependency-management") version "1.1.7"
	id("nu.studer.jooq") version "10.2.1"
}
```

- [ ] **Step 3: Add the `jooqGenerator` runtime dependency**

In `server/build.gradle.kts`, find:

```kotlin
	// database
	runtimeOnly("org.postgresql:postgresql")
```

Replace with:

```kotlin
	// database
	runtimeOnly("org.postgresql:postgresql")
	jooqGenerator("org.postgresql:postgresql:42.7.11")
```

`jooqGenerator` is the configuration the `nu.studer.jooq` plugin creates for the `generateJooq` task's runtime classpath — it needs its own JDBC driver to introspect Postgres during codegen.

- [ ] **Step 4: Add the `jooq {}` codegen config and the Testcontainers/Liquibase task wiring**

In `server/build.gradle.kts`, find the `kotlin {}` block followed by `springBoot {}`:

```kotlin
kotlin {
	compilerOptions {
		jvmTarget.set(JVM_25)
		languageVersion.set(KOTLIN_2_3)
		freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
	}
}

springBoot {
```

Insert the `jooq {}` config and task wiring between them:

```kotlin
kotlin {
	compilerOptions {
		jvmTarget.set(JVM_25)
		languageVersion.set(KOTLIN_2_3)
		freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
	}
}

jooq {
	version.set("3.21.5")
	edition.set(JooqEdition.OSS)

	configurations {
		create("main") {
			generateSchemaSourceOnCompilation.set(true)

			jooqConfiguration.apply {
				generator {
					name = "org.jooq.codegen.KotlinGenerator"

					database {
						name = "org.jooq.meta.postgres.PostgresDatabase"

						schemata {
							schema {
								inputSchema = "system"
							}
							schema {
								inputSchema = "finances"
							}
						}
					}

					target {
						packageName = "br.com.investlog.server.jooq"
						directory = "build/generated-sources/jooq/main"
					}
				}
			}
		}
	}
}

val jooqDb = PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))

val startJooqDb by tasks.registering {
	doLast {
		jooqDb.start()

		val connection = DriverManager.getConnection(jooqDb.jdbcUrl, jooqDb.username, jooqDb.password)
		try {
			val database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(JdbcConnection(connection))
			val resourceAccessor = DirectoryResourceAccessor(File(projectDir, "src/main/resources"))

			Liquibase("db/changelog/db.changelog-master.xml", resourceAccessor, database).update()
		} finally {
			connection.close()
		}

		jooq.configurations.getByName("main").jooqConfiguration.jdbc {
			driver = "org.postgresql.Driver"
			url = jooqDb.jdbcUrl
			user = jooqDb.username
			password = jooqDb.password
		}
	}
}

val stopJooqDb by tasks.registering {
	doLast {
		jooqDb.stop()
	}
}

tasks.named("generateJooq") {
	dependsOn(startJooqDb)
	finalizedBy(stopJooqDb)
}

springBoot {
```

How this fits together:
- `jooq { configurations { create("main") { ... } } }` registers a `generateJooq` task (the `"main"` configuration name maps to the unsuffixed `generateJooq` task) and adds its output directory as a source dir of the `main` source set — so `compileKotlin` automatically depends on `generateJooq` with no extra wiring.
- `startJooqDb` starts the Testcontainer, applies the changelog from Task 1 via `liquibase-core`, then mutates the *same* `jooqConfiguration` object that `generateJooq` will read, pointing its `jdbc {}` block at the running container.
- `generateJooq` depends on `startJooqDb` (so the container is up and migrated, and the JDBC config is set, before codegen runs) and is `finalizedBy(stopJooqDb)` (so the container is stopped whether codegen succeeds or fails).

- [ ] **Step 5: Run `generateJooq` and verify generated sources**

Run:

```bash
cd server
./gradlew generateJooq
```

Expected: `BUILD SUCCESSFUL`. This starts a `postgres:17-alpine` Testcontainer, applies all 6 Task 1 changesets via Liquibase, runs jOOQ's `KotlinGenerator` against the `system` and `finances` schemas, and stops the container.

Then verify the generated package layout:

```bash
ls server/build/generated-sources/jooq/main/br/com/investlog/server/jooq/system
ls server/build/generated-sources/jooq/main/br/com/investlog/server/jooq/finances/tables
```

Expected: the `system` directory contains `Users.kt` (plus `Tables.kt`, `DefaultSchema`-style support classes); the `finances/tables` directory contains one file per table (`Wallets.kt`, `StockTypes.kt`, `FundTypes.kt`, `CurrencyRates.kt`, `StockHoldings.kt`, `StockLots.kt`, `CryptoHoldings.kt`, `CryptoLots.kt`, `FundHoldings.kt`, `FundContributions.kt`, `HoldingsOverview.kt`) plus a generated enum class for `finances.wallet_kind`.

> Requires Docker running locally (see Prerequisite above). Not executed during planning.

- [ ] **Step 6: Commit**

```bash
git add server/build.gradle.kts
git commit -m "Wire up jOOQ codegen via nu.studer.jooq with Testcontainers-backed Liquibase migration"
```

Generated sources under `build/generated-sources/` are already excluded by the existing `server/.gitignore` (`build/` is ignored) — no `.gitignore` change needed.

---

## Task 3: Connection config + full build verification

**Files:**
- Modify: `server/src/main/resources/application.yaml`
- Modify: `server/src/main/resources/application-prod.yaml`

- [ ] **Step 1: Configure Liquibase in `application.yaml`**

`server/src/main/resources/application.yaml` currently contains:

```yaml
spring:
  application:
    name: server
```

Replace with:

```yaml
spring:
  application:
    name: server
  liquibase:
    change-log: classpath:db/changelog/db.changelog-master.xml
    database-change-log-table: database_changelog
    database-change-log-lock-table: database_changelog_lock
```

No `spring.datasource.*` here — for local `bootRun`, the existing `developmentOnly("org.springframework.boot:spring-boot-docker-compose")` dependency detects the `compose.yaml` Postgres service and auto-configures the datasource; Liquibase then runs against that datasource on startup.

- [ ] **Step 2: Configure the datasource in `application-prod.yaml`**

`server/src/main/resources/application-prod.yaml` currently contains:

```yaml
spring:
  application:
    name: server
```

Replace with:

```yaml
spring:
  application:
    name: server
  datasource:
    url: jdbc:postgresql://${DB_HOST}:${DB_PORT:5432}/${DB_NAME}
    username: ${DB_USER}
    password: ${DB_PASSWORD}
```

`spring-boot-docker-compose` is `developmentOnly`, so it's absent from the production jar — no need to explicitly disable it for prod.

- [ ] **Step 3: Run the full build**

Run:

```bash
cd server
./gradlew build
```

Expected: `BUILD SUCCESSFUL`. This runs `generateJooq` (Task 2's Testcontainers + Liquibase flow) as part of `compileKotlin`, compiles main and test sources, and runs the test suite — including `ServerApplicationTests`, which boots the full Spring context (via `TestcontainersConfiguration`'s `postgres:17-alpine` `@ServiceConnection` container) with Liquibase applying `db.changelog-master.xml` against that container using the `application.yaml` config from Step 1.

> Requires Docker running locally (see Prerequisite above). Not executed during planning.

- [ ] **Step 4: Commit**

```bash
git add server/src/main/resources/application.yaml server/src/main/resources/application-prod.yaml
git commit -m "Configure Liquibase changelog and prod datasource connection settings"
```
