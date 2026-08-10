# InvestLog — Daily BRL/USD Exchange Rate Sync Design

**Date:** 2026-08-09
**Status:** Approved — tracked in [#139](https://github.com/arthurgregorio/investlog/issues/139)

## Goal

Add a daily automated sync for the BRL↔USD exchange rate, following the same
architecture as `stockpricesync`/`cryptopricesync`, writing into the existing
`currency_rates` table so the manual-edit UI (`SettingsView.vue`'s "Moeda base e
conversão" card, backed by `CurrencyRateService.upsert`) keeps working as an
override — same pattern already used for stock `current_price`. Server-only PR;
the client enable/disable toggle is a separate follow-up PR (per root `CLAUDE.md`'s
split-by-layer rule).

## Context (as-built)

- `currencyrates/` (`server/src/main/kotlin/br/com/investlog/server/currencyrates/`)
  only supports manual entry via `CurrencyRateService.upsert(currencyCode: CurrencyCode,
  rate: BigDecimal, isBase: Boolean)`, which delegates to
  `CurrencyRateRepository.upsert(currencyCode: String, rate, isBase)` — a jOOQ
  `INSERT ... ON CONFLICT (currency_code) DO UPDATE`. `CurrencyCode` already has both
  `BRL` and `USD` entries — no enum change needed.
- `stockpricesync`/`cryptopricesync` are the two existing sync jobs. Both follow the
  same shape: a `services/` sync service that calls an HTTP client and catches
  `RestClientException` (log + skip run on failure), a `scheduler/` component gated
  by `ConfigurationService.isEnabled(ConfigurationKey.<X>_SYNC_ENABLED)`, catching
  `Exception` around the whole run (log + let the next scheduled run retry).
- `ConfigurationKey` (`server/.../configurations/ConfigurationKey.kt`) is a flat enum
  of typed keys over `system.configurations` rows, loaded into an in-memory cache by
  `ConfigurationService`. New sync jobs default to **disabled** — confirmed by the
  most recent migration (`2026/08/09-1000-disable-price-sync-by-default.xml`), which
  flipped the two existing sync flags to `false`; this job's seed migration inserts
  `false` directly rather than needing a second migration to flip it later.
- HTTP client registration convention (`config/http/*.kt`): a `@Configuration` class
  named after the **API provider** (`CoinGeckoHttpClientsConfig`,
  `BrApiHttpClientsConfig`), `@ImportHttpServices(group = "<provider>", basePackages =
  [...])`, and a `RestClientHttpServiceGroupConfigurer` bean setting the base URL (and
  auth header, when the provider needs one) programmatically — Spring Boot 4.1.0 has
  no `spring.http.serviceclient.*` auto-configuration for this. AwesomeAPI needs no
  key, so the new config only sets `baseUrl`.
- AwesomeAPI's `GET https://economia.awesomeapi.com.br/json/last/USD-BRL` endpoint
  (no key required) returns:
  ```json
  { "USDBRL": { "code": "USD", "codein": "BRL", "bid": "5.35", "ask": "5.36", ... } }
  ```
  `bid` is the field to use as the rate, as a `BigDecimal` (arrives as a JSON string).

## Decision

| Decision | Choice |
| --- | --- |
| New package | `usdpricesync/`, mirroring `cryptopricesync/`'s flat shape (no `domain/` layer) |
| HTTP client naming | Named after the endpoint it calls, not the provider generically — `AwesomeApiLastQuoteClient`, since it only ever calls the "last quote" endpoint |
| HTTP client location | `usdpricesync/http/AwesomeApiLastQuoteClient.kt` — single consumer, same reasoning as `CryptoPriceSyncScheduler`'s narrowly-scoped clients |
| HTTP client config naming | Named after the **provider**, matching `CoinGeckoHttpClientsConfig`/`BrApiHttpClientsConfig` — `AwesomeApiHttpClientsConfig`, kept generic so a future AwesomeAPI endpoint reuses the same group |
| Rate source | `CurrencyRateService.upsert(CurrencyCode.USD, rate, isBase = false)` — reuses the existing cross-feature service, not `CurrencyRateRepository` directly, keeping the service-layer boundary between features intact |
| Scheduler cron | `0 0 7 * * *`, zone `America/Sao_Paulo` (once daily, 7am) |
| Config gate | New `ConfigurationKey.USD_PRICE_SYNC_ENABLED("usd_price_sync_enabled")` |
| Default state | Disabled — seed migration inserts `('usd_price_sync_enabled', 'false')` directly |
| Manual trigger | None — no admin force-sync endpoint/button, per the issue's explicit decision (unlike stock/crypto) |
| Error handling | Service catches `RestClientException`, logs, returns (skip this run, keep last-known rate); scheduler catches `Exception` around the whole call, logs, lets the next scheduled run retry — same two-layer pattern as `CryptoPriceSyncScheduler`/`CryptoPriceSyncService` |

## Architecture

### Server

```
usdpricesync/
  http/
    AwesomeApiLastQuoteClient.kt       # @GetExchange("/json/last/{pair}") -> Map<String, AwesomeApiRateEntry>
  services/
    UsdPriceSyncService.kt             # fetches rate, calls CurrencyRateService.upsert
  scheduler/
    UsdPriceSyncScheduler.kt           # @Scheduled cron, gated by ConfigurationKey.USD_PRICE_SYNC_ENABLED
config/http/
  AwesomeApiHttpClientsConfig.kt       # @ImportHttpServices(group = "awesomeapi", basePackages = [".. usdpricesync.http"])
```

```kotlin
interface AwesomeApiLastQuoteClient {
    @GetExchange("/json/last/{currencyPair}")
    fun getLastQuote(@PathVariable currencyPair: String): Map<String, AwesomeApiRateEntry>
}

data class AwesomeApiRateEntry(val bid: BigDecimal)
```

```kotlin
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

```kotlin
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

`ConfigurationKey.kt` gains one entry:

```kotlin
enum class ConfigurationKey(val key: String) {
    STOCK_PRICE_SYNC_ENABLED("stock_price_sync_enabled"),
    CRYPTO_PRICE_SYNC_ENABLED("crypto_price_sync_enabled"),
    USD_PRICE_SYNC_ENABLED("usd_price_sync_enabled"),
}
```

`AwesomeApiHttpClientsConfig.kt` mirrors `CoinGeckoHttpClientsConfig.kt` minus the
API key handling:

```kotlin
@Configuration(proxyBeanMethods = false)
@ImportHttpServices(group = CLIENT_GROUP_NAME, basePackages = [PACKAGE_TO_SEARCH])
class AwesomeApiHttpClientsConfig(
    @Value($$"${investlog.awesomeapi.base-url}")
    private val awesomeApiBaseUrl: String,
) {
    @Bean
    fun awesomeApiGroupConfigurer(): RestClientHttpServiceGroupConfigurer =
        RestClientHttpServiceGroupConfigurer { groups ->
            groups.filterByName(CLIENT_GROUP_NAME).forEachClient { _, builder ->
                builder.baseUrl(awesomeApiBaseUrl)
            }
        }

    companion object {
        private const val CLIENT_GROUP_NAME = "awesomeapi"
        private const val PACKAGE_TO_SEARCH = "br.com.investlog.server.usdpricesync.http"
    }
}
```

`application.yaml` gains one property alongside `brapi`/`coingecko`:

```yaml
investlog:
  awesomeapi:
    base-url: https://economia.awesomeapi.com.br
```

### Database

New changelog file under `server/src/main/resources/db/changelog/changes/2026/08/`,
included from `db.changelog-master.xml`:

```sql
INSERT INTO system.configurations (key, value) VALUES ('usd_price_sync_enabled', 'false');
```

Symmetric rollback: `DELETE FROM system.configurations WHERE key = 'usd_price_sync_enabled';`

## Files to create / change

| Path | Action |
| --- | --- |
| `server/src/main/resources/db/changelog/changes/2026/08/<DD-HHMM>-seed-usd-price-sync-enabled.xml` | **create** |
| `server/src/main/resources/db/changelog/db.changelog-master.xml` | **update** — `<include>` the new file |
| `server/src/main/resources/application.yaml` | **update** — add `investlog.awesomeapi.base-url` |
| `server/src/main/kotlin/br/com/investlog/server/config/http/AwesomeApiHttpClientsConfig.kt` | **create** |
| `server/src/main/kotlin/br/com/investlog/server/usdpricesync/http/AwesomeApiLastQuoteClient.kt` | **create** |
| `server/src/main/kotlin/br/com/investlog/server/usdpricesync/services/UsdPriceSyncService.kt` | **create** |
| `server/src/main/kotlin/br/com/investlog/server/usdpricesync/scheduler/UsdPriceSyncScheduler.kt` | **create** |
| `server/src/main/kotlin/br/com/investlog/server/configurations/ConfigurationKey.kt` | **update** — add `USD_PRICE_SYNC_ENABLED` |
| `server/src/test/kotlin/br/com/investlog/server/usdpricesync/UsdPriceSyncServiceTest.kt` | **create** |
| `server/src/test/kotlin/br/com/investlog/server/usdpricesync/UsdPriceSyncSchedulerTest.kt` | **create** |

Client PR (separate, follow-up, out of scope for this spec): third `b-switch` in
`SettingsView.vue`'s "Configurações" card, bound to
`configurationsStore.values['usd_price_sync_enabled']`, no force-sync button — per
the issue.

## Verification

- **Server unit test** (`UsdPriceSyncServiceTest`): WireMock stub for
  `GET /json/last/USD-BRL` returning a fixed `bid`, assert `currency_rates` row for
  `USD` is upserted with that rate and `is_base = false`. Second test: WireMock stub
  returns 500/times out, assert `syncRate()` returns without throwing and without
  writing (last-known rate preserved).
- **Server unit test** (`UsdPriceSyncSchedulerTest`): with
  `usd_price_sync_enabled = false` (the seeded default), call `syncRate()` directly
  and assert the WireMock stub receives zero requests — mirrors
  `CryptoPriceSyncSchedulerTest`'s config-off test.
- **Manual check**: `./gradlew test` passes; with the flag flipped to `true` via
  `PATCH /configurations/usd_price_sync_enabled`, manually invoke
  `UsdPriceSyncScheduler.syncRate()` (or wait for 7am America/Sao_Paulo) and confirm
  `currency_rates.USD.rate` updates from the live AwesomeAPI response.
