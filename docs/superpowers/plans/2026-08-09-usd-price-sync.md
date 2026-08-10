# Daily BRL/USD Exchange Rate Sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a daily automated job that fetches the USD/BRL exchange rate from AwesomeAPI and upserts it into the existing `currency_rates` table, gated by a new, default-disabled configuration flag.

**Architecture:** New `usdpricesync/` server package mirroring `cryptopricesync/`'s shape (`http/`, `services/`, `scheduler/`, no `domain/` layer). A declarative HTTP client interface (`AwesomeApiLastQuoteClient`) calls AwesomeAPI's keyless "last quote" endpoint; `UsdPriceSyncService` parses the response and delegates persistence to the existing `CurrencyRateService.upsert`; `UsdPriceSyncScheduler` runs it daily at 7am America/Sao_Paulo, gated by `ConfigurationService.isEnabled(ConfigurationKey.USD_PRICE_SYNC_ENABLED)`.

**Tech Stack:** Kotlin 2.3 / Spring Boot 4.1 (`spring-boot-starter-webmvc`, declarative HTTP interface clients via `@ImportHttpServices`), jOOQ (via existing `CurrencyRateRepository`, untouched), Liquibase, JUnit 5 + WireMock for integration tests.

## Global Constraints

- No abbreviated names anywhere (variables, params, aliases) — full words only (`server/CLAUDE.md`).
- Constructor parameters never carry an inline annotation — `@Value(...)` goes on its own line above the `private val` (`server/CLAUDE.md`).
- `@Value` uses Kotlin 2.3's `$$` multi-dollar interpolation prefix, not `"\${...}"` (`server/CLAUDE.md`).
- Every `@Service` class carries `@Transactional(readOnly = true)`; each write method adds its own explicit `@Transactional` (`server/CLAUDE.md`).
- `KotlinLogging` instances are named `logger`, not `log`: `private val logger = KotlinLogging.logger {}` (`server/CLAUDE.md`).
- Never edit an existing Liquibase changelog file — always a new file under `db/changelog/changes/<year>/<month>/<DD-HHMM>-<description>.xml`, included from `db.changelog-master.xml` (`server/CLAUDE.md`).
- No comments that just restate the spec or business logic — only a non-obvious WHY (root `CLAUDE.md`).
- Cron: `0 0 7 * * *`, zone `America/Sao_Paulo` (design spec decision).
- New config key: `usd_price_sync_enabled`, seeded `false` (disabled by default) (design spec decision).
- No admin manual-trigger endpoint/controller for this job (design spec decision — unlike stock/crypto).
- HTTP client interface named after the endpoint it calls (`AwesomeApiLastQuoteClient`); its `@Configuration` class named after the provider (`AwesomeApiHttpClientsConfig`) (design spec decision).
- Rate persistence goes through `CurrencyRateService.upsert(CurrencyCode.USD, rate, isBase = false)`, not `CurrencyRateRepository` directly (design spec decision).
- This is a server-only PR — no client changes in this plan.

---

## Task 1: Add the `USD_PRICE_SYNC_ENABLED` configuration key

**Files:**
- Modify: `server/src/main/kotlin/br/com/investlog/server/configurations/ConfigurationKey.kt`
- Create: `server/src/main/resources/db/changelog/changes/2026/08/09-1100-seed-usd-price-sync-enabled.xml`
- Modify: `server/src/main/resources/db/changelog/db.changelog-master.xml`
- Modify: `server/src/test/kotlin/br/com/investlog/server/configurations/ConfigurationControllerTest.kt`

**Interfaces:**
- Produces: `ConfigurationKey.USD_PRICE_SYNC_ENABLED` (enum constant, `.key == "usd_price_sync_enabled"`) — consumed by Task 3's scheduler.
- Produces: a `system.configurations` row `('usd_price_sync_enabled', 'false')`, present in every environment from this migration onward — consumed by Task 2's and Task 3's tests (via `PATCH /private/v1/configurations/usd_price_sync_enabled`, which 404s if the row doesn't exist).

- [ ] **Step 1: Write the failing test**

Add this test to the end of `ConfigurationControllerTest.kt`, right after the existing `` `a non-admin is forbidden from updating a configuration` `` test (before the closing brace of the class, above `private fun registerApproveAndLogin`):

```kotlin
    @Test
    @Order(6)
    fun `returns the seeded usd_price_sync_enabled configuration as false`() {
        restTestClient.get()
            .uri("/private/v1/configurations")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content[?(@.key=='usd_price_sync_enabled')].value").isEqualTo("false")
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "br.com.investlog.server.configurations.ConfigurationControllerTest"`
Expected: FAIL — the `usd_price_sync_enabled` row doesn't exist yet, so the jsonPath expression matches nothing and `isEqualTo("false")` fails.

- [ ] **Step 3: Add the enum constant**

In `ConfigurationKey.kt`, add the new entry:

```kotlin
package br.com.investlog.server.configurations

enum class ConfigurationKey(val key: String) {
    STOCK_PRICE_SYNC_ENABLED("stock_price_sync_enabled"),
    CRYPTO_PRICE_SYNC_ENABLED("crypto_price_sync_enabled"),
    USD_PRICE_SYNC_ENABLED("usd_price_sync_enabled"),
}
```

- [ ] **Step 4: Create the seed migration**

Create `server/src/main/resources/db/changelog/changes/2026/08/09-1100-seed-usd-price-sync-enabled.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
    xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        https://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-5.0.xsd">

    <changeSet id="09-1100-1" author="${claude}">
        <comment>Seed usd_price_sync_enabled, defaulting the automated USD/BRL sync job to off</comment>
        <sql>
            INSERT INTO system.configurations (key, value) VALUES ('usd_price_sync_enabled', 'false');
        </sql>
        <rollback>
            <sql>
                DELETE FROM system.configurations WHERE key = 'usd_price_sync_enabled';
            </sql>
        </rollback>
    </changeSet>

</databaseChangeLog>
```

Register it in `db.changelog-master.xml`, immediately after the existing `2026/08` includes:

```xml
    <include file="db/changelog/changes/2026/08/09-1100-seed-usd-price-sync-enabled.xml" relativeToChangelogFile="false"/>
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew test --tests "br.com.investlog.server.configurations.ConfigurationControllerTest"`
Expected: PASS (all 6 tests, including the new one)

- [ ] **Step 6: Commit**

```bash
git add server/src/main/kotlin/br/com/investlog/server/configurations/ConfigurationKey.kt \
        server/src/main/resources/db/changelog/changes/2026/08/09-1100-seed-usd-price-sync-enabled.xml \
        server/src/main/resources/db/changelog/db.changelog-master.xml \
        server/src/test/kotlin/br/com/investlog/server/configurations/ConfigurationControllerTest.kt
git commit -m "server: add usd_price_sync_enabled configuration key

Refs #139"
```

---

## Task 2: AwesomeAPI HTTP client and `UsdPriceSyncService`

**Files:**
- Create: `server/src/main/kotlin/br/com/investlog/server/usdpricesync/http/AwesomeApiLastQuoteClient.kt`
- Create: `server/src/main/kotlin/br/com/investlog/server/config/http/AwesomeApiHttpClientsConfig.kt`
- Modify: `server/src/main/resources/application.yaml`
- Create: `server/src/main/kotlin/br/com/investlog/server/usdpricesync/services/UsdPriceSyncService.kt`
- Test: `server/src/test/kotlin/br/com/investlog/server/usdpricesync/UsdPriceSyncServiceTest.kt`

**Interfaces:**
- Consumes: `CurrencyRateService.upsert(currencyCode: CurrencyCode, rate: BigDecimal, isBase: Boolean): CurrencyRateResponse` (existing, `br.com.investlog.server.currencyrates.services.CurrencyRateService`).
- Consumes: `CurrencyRateService.findAll(pageable: Pageable): PagedModel<CurrencyRateResponse>` (existing) — used only by the test to read back the persisted rate.
- Produces: `UsdPriceSyncService.syncRate()` (no-arg, `Unit`) — consumed by Task 3's scheduler.

- [ ] **Step 1: Write the failing test**

Create `server/src/test/kotlin/br/com/investlog/server/usdpricesync/UsdPriceSyncServiceTest.kt`:

```kotlin
package br.com.investlog.server.usdpricesync

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.currencyrates.rest.payloads.CurrencyCode
import br.com.investlog.server.currencyrates.services.CurrencyRateService
import br.com.investlog.server.usdpricesync.services.UsdPriceSyncService
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UsdPriceSyncServiceTest : BaseIntegrationTest() {

    @Autowired
    lateinit var usdPriceSyncService: UsdPriceSyncService

    @Autowired
    lateinit var currencyRateService: CurrencyRateService

    @BeforeEach
    fun resetStubs() {
        wireMockServer.resetAll()
    }

    @AfterAll
    fun tearDown() {
        wireMockServer.stop()
    }

    private fun usdRate(): BigDecimal =
        currencyRateService.findAll(PageRequest.of(0, 10)).content
            .single { it.currencyCode == CurrencyCode.USD }
            .rate

    @Test
    @Order(1)
    fun `keeps the last-known rate when AwesomeAPI fails`() {
        wireMockServer.stubFor(
            get(urlPathEqualTo("/json/last/USD-BRL"))
                .willReturn(aResponse().withStatus(500))
        )

        usdPriceSyncService.syncRate()

        assertEquals(0, usdRate().compareTo(BigDecimal("5")))
    }

    @Test
    @Order(2)
    fun `fetches the USD-BRL quote and upserts it as a non-base currency rate`() {
        wireMockServer.stubFor(
            get(urlPathEqualTo("/json/last/USD-BRL"))
                .willReturn(okJson("""{"USDBRL":{"code":"USD","codein":"BRL","bid":"5.35"}}"""))
        )

        usdPriceSyncService.syncRate()

        assertEquals(0, usdRate().compareTo(BigDecimal("5.35")))
    }

    companion object {
        private val wireMockServer = WireMockServer(wireMockConfig().dynamicPort())

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            wireMockServer.start()
            registry.add("investlog.awesomeapi.base-url") { "http://localhost:${wireMockServer.port()}" }
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "br.com.investlog.server.usdpricesync.UsdPriceSyncServiceTest"`
Expected: FAIL to compile — `UsdPriceSyncService`, `AwesomeApiLastQuoteClient`, and the `investlog.awesomeapi.base-url` property don't exist yet.

- [ ] **Step 3: Create the HTTP client interface**

Create `server/src/main/kotlin/br/com/investlog/server/usdpricesync/http/AwesomeApiLastQuoteClient.kt`:

```kotlin
package br.com.investlog.server.usdpricesync.http

import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.service.annotation.GetExchange
import java.math.BigDecimal

interface AwesomeApiLastQuoteClient {

    @GetExchange("/json/last/{currencyPair}")
    fun getLastQuote(@PathVariable currencyPair: String): Map<String, AwesomeApiRateEntry>
}

data class AwesomeApiRateEntry(val bid: BigDecimal)
```

- [ ] **Step 4: Register the AwesomeAPI HTTP client group**

Create `server/src/main/kotlin/br/com/investlog/server/config/http/AwesomeApiHttpClientsConfig.kt`:

```kotlin
package br.com.investlog.server.config.http

import br.com.investlog.server.config.http.AwesomeApiHttpClientsConfig.Companion.CLIENT_GROUP_NAME
import br.com.investlog.server.config.http.AwesomeApiHttpClientsConfig.Companion.PACKAGE_TO_SEARCH
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer
import org.springframework.web.service.registry.ImportHttpServices

@Configuration(proxyBeanMethods = false)
@ImportHttpServices(group = CLIENT_GROUP_NAME, basePackages = [PACKAGE_TO_SEARCH])
class AwesomeApiHttpClientsConfig(
    @Value($$"${investlog.awesomeapi.base-url}")
    private val awesomeApiBaseUrl: String,
) {

    @Bean
    fun awesomeApiGroupConfigurer(): RestClientHttpServiceGroupConfigurer =
        RestClientHttpServiceGroupConfigurer { groups ->
            groups.filterByName(CLIENT_GROUP_NAME)
                .forEachClient { _, builder ->
                    builder.baseUrl(awesomeApiBaseUrl)
                }
        }

    companion object {
        private const val CLIENT_GROUP_NAME = "awesomeapi"
        private const val PACKAGE_TO_SEARCH = "br.com.investlog.server.usdpricesync.http"
    }
}
```

- [ ] **Step 5: Add the base URL property**

In `server/src/main/resources/application.yaml`, add this block immediately after the existing `coingecko:` block (same `investlog:` indentation level):

```yaml
  awesomeapi:
    base-url: https://economia.awesomeapi.com.br
```

- [ ] **Step 6: Implement `UsdPriceSyncService`**

Create `server/src/main/kotlin/br/com/investlog/server/usdpricesync/services/UsdPriceSyncService.kt`:

```kotlin
package br.com.investlog.server.usdpricesync.services

import br.com.investlog.server.currencyrates.rest.payloads.CurrencyCode
import br.com.investlog.server.currencyrates.services.CurrencyRateService
import br.com.investlog.server.usdpricesync.http.AwesomeApiLastQuoteClient
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.RestClientException

private val logger = KotlinLogging.logger {}

@Service
@Transactional(readOnly = true)
class UsdPriceSyncService(
    private val awesomeApiLastQuoteClient: AwesomeApiLastQuoteClient,
    private val currencyRateService: CurrencyRateService,
) {

    @Transactional
    fun syncRate() {
        val quote = try {
            awesomeApiLastQuoteClient.getLastQuote("USD-BRL")["USDBRL"]
        } catch (ex: RestClientException) {
            logger.error(ex) { "Failed to fetch USD/BRL rate from AwesomeAPI, skipping this run" }
            return
        }

        if (quote == null) {
            logger.warn { "AwesomeAPI response missing USDBRL entry, skipping this run" }
            return
        }

        currencyRateService.upsert(CurrencyCode.USD, quote.bid, isBase = false)
        logger.info { "USD/BRL rate sync completed: rate=${quote.bid}" }
    }
}
```

- [ ] **Step 7: Run test to verify it passes**

Run: `./gradlew test --tests "br.com.investlog.server.usdpricesync.UsdPriceSyncServiceTest"`
Expected: PASS (both tests)

- [ ] **Step 8: Commit**

```bash
git add server/src/main/kotlin/br/com/investlog/server/usdpricesync/http/AwesomeApiLastQuoteClient.kt \
        server/src/main/kotlin/br/com/investlog/server/config/http/AwesomeApiHttpClientsConfig.kt \
        server/src/main/resources/application.yaml \
        server/src/main/kotlin/br/com/investlog/server/usdpricesync/services/UsdPriceSyncService.kt \
        server/src/test/kotlin/br/com/investlog/server/usdpricesync/UsdPriceSyncServiceTest.kt
git commit -m "server: add UsdPriceSyncService backed by AwesomeAPI

Refs #139"
```

---

## Task 3: `UsdPriceSyncScheduler`

**Files:**
- Create: `server/src/main/kotlin/br/com/investlog/server/usdpricesync/scheduler/UsdPriceSyncScheduler.kt`
- Test: `server/src/test/kotlin/br/com/investlog/server/usdpricesync/UsdPriceSyncSchedulerTest.kt`

**Interfaces:**
- Consumes: `UsdPriceSyncService.syncRate()` (Task 2), `ConfigurationService.isEnabled(key: ConfigurationKey): Boolean` (existing), `ConfigurationKey.USD_PRICE_SYNC_ENABLED` (Task 1).
- Produces: `UsdPriceSyncScheduler.syncRate()` (no-arg, `Unit`, also the `@Scheduled` entry point) — no further consumers within this plan; this is the job's public entry point invoked by Spring's scheduler at runtime.

- [ ] **Step 1: Write the failing test**

Create `server/src/test/kotlin/br/com/investlog/server/usdpricesync/UsdPriceSyncSchedulerTest.kt`:

```kotlin
package br.com.investlog.server.usdpricesync

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.usdpricesync.scheduler.UsdPriceSyncScheduler
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.client.RestTestClient
import kotlin.test.Test

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UsdPriceSyncSchedulerTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTestClient: RestTestClient

    @Autowired
    lateinit var usdPriceSyncScheduler: UsdPriceSyncScheduler

    @AfterAll
    fun tearDown() {
        wireMockServer.stop()
    }

    @Test
    fun `scheduler skips the sync run when usd_price_sync_enabled is false`() {
        restTestClient.patch()
            .uri("/private/v1/configurations/usd_price_sync_enabled")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"value":"false"}""")
            .exchange()
            .expectStatus().isOk()

        usdPriceSyncScheduler.syncRate()

        wireMockServer.verify(0, getRequestedFor(urlPathEqualTo("/json/last/USD-BRL")))
    }

    companion object {
        private val wireMockServer = WireMockServer(wireMockConfig().dynamicPort())

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            wireMockServer.start()
            registry.add("investlog.awesomeapi.base-url") { "http://localhost:${wireMockServer.port()}" }
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "br.com.investlog.server.usdpricesync.UsdPriceSyncSchedulerTest"`
Expected: FAIL to compile — `UsdPriceSyncScheduler` doesn't exist yet.

- [ ] **Step 3: Implement `UsdPriceSyncScheduler`**

Create `server/src/main/kotlin/br/com/investlog/server/usdpricesync/scheduler/UsdPriceSyncScheduler.kt`:

```kotlin
package br.com.investlog.server.usdpricesync.scheduler

import br.com.investlog.server.configurations.ConfigurationKey
import br.com.investlog.server.configurations.services.ConfigurationService
import br.com.investlog.server.usdpricesync.services.UsdPriceSyncService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
class UsdPriceSyncScheduler(
    private val usdPriceSyncService: UsdPriceSyncService,
    private val configurationService: ConfigurationService,
) {

    @Scheduled(cron = "0 0 7 * * *", zone = "America/Sao_Paulo")
    fun syncRate() {

        if (!configurationService.isEnabled(ConfigurationKey.USD_PRICE_SYNC_ENABLED)) {
            logger.info { "USD price sync skipped: disabled via configuration" }
            return
        }

        try {
            usdPriceSyncService.syncRate()
        } catch (exception: Exception) {
            logger.error(exception) { "USD price sync run failed, next scheduled run will retry" }
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "br.com.investlog.server.usdpricesync.UsdPriceSyncSchedulerTest"`
Expected: PASS

- [ ] **Step 5: Run the full server test suite**

Run: `./gradlew test`
Expected: PASS — all existing tests plus the three new ones, no regressions.

- [ ] **Step 6: Commit**

```bash
git add server/src/main/kotlin/br/com/investlog/server/usdpricesync/scheduler/UsdPriceSyncScheduler.kt \
        server/src/test/kotlin/br/com/investlog/server/usdpricesync/UsdPriceSyncSchedulerTest.kt
git commit -m "server: schedule daily USD/BRL rate sync

Closes #139"
```

---

## After all tasks: open the PR

This branch (`feature/139-usd-price-sync`) is the **server PR** for issue #139. Per root
`CLAUDE.md`, the client toggle (`SettingsView.vue`) is a separate follow-up PR — not part of
this plan. Since this server PR is the last piece needed to close out the server-side work and
the client PR is tracked separately as its own follow-up, confirm with the user whether to use
`Closes #139` (if no further server work is expected) or `Refs #139` (if the issue should stay
open until the client PR also lands) before pushing — Task 3's commit message above defaults to
`Closes #139` but this should be revisited at PR-creation time, not assumed.
