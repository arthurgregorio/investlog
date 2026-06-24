# Currency Display Selector Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the currency selector in the navbar actually work: the user can switch the app's display currency between BRL and USD, and every monetary value shown anywhere (Overview KPIs/chart, wallet cards, holdings table, lot/contribution detail rows) is converted into that currency using the configured exchange rate, except when a value's native currency already matches the selected display currency (no conversion applied).

**Architecture:** Display currency is a new, independent user preference — the already-scaffolded-but-unused `preferredCurrency` field on `system.users` (`GET`/`PATCH /profile`). It is decoupled from the existing rates-anchor concept (`CurrencyRate.isBase`, configured in Settings → "Moeda base e conversão"), which is left untouched. Conversion factor for any amount native to currency `C`, going to display currency `D`, is `rateToBase(C) / rateToBase(D)`, where `rateToBase(code)` is `1` if `code` is the rates-anchor currency, else the stored `CurrencyRate.rate` for that code (falls back to `1` if missing, matching existing behavior). The Overview endpoint (pre-aggregated SQL) converts server-side, passing the user's `preferredCurrency` into the jOOQ query. Wallets/Investments/Holding-detail (already wallet-native, no backend change) convert client-side via a new `useCurrencyStore`, using already-loaded `CurrencyRate` data.

**Tech Stack:** Vue 3 `<script setup>` + TypeScript + Pinia (frontend), Spring Boot 4 / Kotlin / jOOQ (backend).

## Global Constraints

- No abbreviated names — full descriptive names for variables, props, loop iterators (per both `client/CLAUDE.md` and `server/CLAUDE.md`).
- Frontend: `tsconfig.app.json` has `strict`, `noUnusedLocals`, `noUnusedParameters` — unused imports/vars fail `npm run build`.
- Backend: jOOQ codegen requires Docker running locally (`./gradlew test` triggers it via `compileKotlin`).
- Only `BRL` and `USD` exist as `CurrencyCode` values — no other currencies are in scope.
- Settings' "Moeda base e conversão" section (the `isBase` flag and its flip-base logic) must NOT be touched — this plan adds a parallel, independent preference.
- Form inputs (`CreateWalletModal`, `AddPositionModal`, `UpdatePriceModal`) keep entering amounts in the wallet's native currency — conversion is a read-only display concern, never applied to inputs.

---

## File Structure

**Backend (Spring Boot / Kotlin):**
- Modify `server/src/main/kotlin/br/com/investlog/server/profile/domain/services/ProfileService.kt` — fix accent-color/currency clobber bug on partial `PATCH /profile`.
- Modify `server/src/test/kotlin/br/com/investlog/server/profile/rest/controllers/ProfileControllerTest.kt` — add regression test for the bug above.
- Modify `server/src/main/kotlin/br/com/investlog/server/overview/rest/payloads/PortfolioSummaryResponse.kt` — rename `baseCurrency` → `displayCurrency`.
- Modify `server/src/main/kotlin/br/com/investlog/server/overview/domain/repositories/OverviewRepository.kt` — convert to a requested display currency instead of always converting to the rates-anchor currency.
- Modify `server/src/main/kotlin/br/com/investlog/server/overview/domain/services/OverviewService.kt` — pass the current user's `preferredCurrency` into the repository.
- Modify `server/src/test/kotlin/br/com/investlog/server/overview/rest/controllers/OverviewControllerTest.kt` — add tests proving conversion follows `preferredCurrency`.

**Frontend (Vue 3 / Pinia):**
- Modify `client/src/types.ts` — extend `ProfileResponse`, add `ProfileUpdateRequest`, rename `PortfolioSummary.baseCurrency` → `displayCurrency`.
- Modify `client/src/api/profile.ts` — add `updateProfile()`.
- Create `client/src/stores/currency.ts` — `useCurrencyStore`: holds `displayCurrency`, persists it via `PATCH /profile`, exposes `convert(amount, fromCurrency)`.
- Create `client/src/stores/currency.spec.ts` — unit tests for the conversion math.
- Modify `client/src/components/layout/TheNavbar.vue` — replace the static "Base BRL" chip with a working `<b-select>` bound to the new store.
- Modify `client/src/views/OverviewView.vue` — read `summary.displayCurrency` instead of the removed `baseCurrency`.
- Modify `client/src/components/layout/TheTopNav.vue` — same rename, drop the `ratesStore` dependency.
- Modify `client/src/views/WalletsView.vue` — convert wallet KPI figures through `currencyStore.convert(...)`.
- Modify `client/src/views/InvestmentsView.vue` — convert holding row figures through `currencyStore.convert(...)`.
- Modify `client/src/components/investments/HoldingDetailPanel.vue` — convert lot/contribution figures through `currencyStore.convert(...)`.

---

### Task 1: Fix the `PATCH /profile` partial-update clobber bug

**Why this is needed:** `ProfileService.updateProfile` falls back to a *hardcoded* default (`AccentColor.TEAL`, `CurrencyCode.BRL`) for any field omitted from the request body, instead of falling back to the user's *current* value. Once the new currency selector starts calling `PATCH /profile` with only `{ preferredCurrency: "USD" }`, this bug would silently reset `accentColor` back to teal on every currency switch. This must be fixed first since Task 2 onward relies on `PATCH /profile` being a correct partial update.

**Files:**
- Modify: `server/src/test/kotlin/br/com/investlog/server/profile/rest/controllers/ProfileControllerTest.kt`
- Modify: `server/src/main/kotlin/br/com/investlog/server/profile/domain/services/ProfileService.kt:20-29`

**Interfaces:**
- Produces: `ProfileService.updateProfile(request: ProfileUpdateRequest): ProfileResponse` keeps its existing signature; only the fallback values change.

- [ ] **Step 1: Write the failing test**

Insert a new test between the existing `Order(2)` and `Order(3)` tests, and bump the old `Order(3)` to `Order(4)`:

```kotlin
    @Test
    @Order(3)
    fun `updates preferred currency and preserves the previously set accent color`() {

        val response = restTestClient.patch()
            .uri("/private/v1/profile")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"preferredCurrency":"USD"}""")
            .exchange()
            .expectStatus().isOk()
            .returnResult<ProfileResponse>()
            .responseBody

        assertEquals("indigo", response?.accentColor?.text)
        assertEquals("USD", response?.preferredCurrency)
    }

    @Test
    @Order(4)
    fun `rejects an invalid accent color`() {
        restTestClient.patch()
            .uri("/private/v1/profile")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"accentColor":"purple"}""")
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.title").isEqualTo("Bad Request")
    }
```

The full file (for reference, after this edit) keeps `Order(1)` and `Order(2)` exactly as they are today — `Order(2)` sets `accentColor` to `indigo`, which the new `Order(3)` test depends on.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests "br.com.investlog.server.profile.rest.controllers.ProfileControllerTest"`
Expected: FAIL on the new test — `accentColor` comes back `"teal"` instead of `"indigo"`, because `request.accentColor` is null and the current code falls back to `AccentColor.TEAL.text` instead of the user's existing accent.

- [ ] **Step 3: Write the minimal fix**

In `server/src/main/kotlin/br/com/investlog/server/profile/domain/services/ProfileService.kt`, replace the body of `updateProfile`:

```kotlin
    fun updateProfile(request: ProfileUpdateRequest): ProfileResponse {

        val user = currentUserProvider.getCurrentUser()

        return userRepository.updatePreferences(
            userId = user.id,
            accentColor = request.accentColor?.text ?: user.accentColor.text,
            preferredCurrency = request.preferredCurrency?.text ?: user.preferredCurrency,
        ).toResponse()
    }
```

Remove the now-unused imports `AccentColor` and `CurrencyCode` from the top of the file if no other reference remains (check with a search for `AccentColor.` and `CurrencyCode.` in the file — both only appeared in the two replaced lines).

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew test --tests "br.com.investlog.server.profile.rest.controllers.ProfileControllerTest"`
Expected: PASS — all 4 tests green.

- [ ] **Step 5: Commit**

```bash
git add server/src/main/kotlin/br/com/investlog/server/profile/domain/services/ProfileService.kt server/src/test/kotlin/br/com/investlog/server/profile/rest/controllers/ProfileControllerTest.kt
git commit -m "fix: preserve existing profile fields on partial PATCH /profile"
```

---

### Task 2: Convert Overview totals to the user's preferred currency

**Files:**
- Modify: `server/src/main/kotlin/br/com/investlog/server/overview/rest/payloads/PortfolioSummaryResponse.kt`
- Modify: `server/src/main/kotlin/br/com/investlog/server/overview/domain/repositories/OverviewRepository.kt:25-142`
- Modify: `server/src/main/kotlin/br/com/investlog/server/overview/domain/services/OverviewService.kt`
- Modify: `server/src/test/kotlin/br/com/investlog/server/overview/rest/controllers/OverviewControllerTest.kt`

**Interfaces:**
- Consumes: `CurrentUser.preferredCurrency: String` (already exists, fixed in Task 1's surrounding code).
- Produces: `OverviewRepository.findSummary(userId: Long, displayCurrency: String): PortfolioSummaryResponse` and `OverviewRepository.findSeries(userId: Long, displayCurrency: String): List<SeriesPointResponse>` — both now take an explicit `displayCurrency` parameter. `PortfolioSummaryResponse.displayCurrency: String` (renamed from `baseCurrency: String?`, now non-null).

- [ ] **Step 1: Write the failing tests**

Add to `server/src/test/kotlin/br/com/investlog/server/overview/rest/controllers/OverviewControllerTest.kt`, after the existing `Order(4)` test:

```kotlin
    @Test
    @Order(5)
    fun `GET overview converts a wallet in a different currency using the configured rate`() {

        restTestClient.put()
            .uri("/private/v1/currency-rates/USD")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"rate":5.00}""")
            .exchange()
            .expectStatus().isOk()

        val before = restTestClient.get()
            .uri("/private/v1/overview")
            .exchange()
            .expectStatus().isOk()
            .returnResult<PortfolioSummaryResponse>()
            .responseBody!!

        val usdStockTypeId = restTestClient.post()
            .uri("/private/v1/stock-types")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"USD Overview Stock Type"}""")
            .exchange()
            .returnResult<TypeResponse>()
            .responseBody!!
            .id

        val usdWalletId = restTestClient.post()
            .uri("/private/v1/wallets")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"USD Overview Wallet","kind":"stocks","currency":"USD"}""")
            .exchange()
            .returnResult<WalletResponse>()
            .responseBody!!
            .id

        restTestClient.post()
            .uri("/private/v1/wallets/$usdWalletId/stock-holdings")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                """{"stockTypeId":"$usdStockTypeId","ticker":"USDOV3","currentPrice":110.00,
                   "lot":{"lotDate":"2025-04-10","quantity":2,"price":100.00}}"""
            )
            .exchange()
            .expectStatus().isCreated()

        val after = restTestClient.get()
            .uri("/private/v1/overview")
            .exchange()
            .expectStatus().isOk()
            .returnResult<PortfolioSummaryResponse>()
            .responseBody!!

        assertEquals("BRL", after.displayCurrency)
        assertEquals(0, after.totalCostBasis.compareTo(before.totalCostBasis + BigDecimal("1000.00")))
        assertEquals(0, after.totalCurrentValue.compareTo(before.totalCurrentValue + BigDecimal("1100.00")))
    }

    @Test
    @Order(6)
    fun `GET overview uses the currently selected preferred currency for conversion`() {

        val beforeSwitch = restTestClient.get()
            .uri("/private/v1/overview")
            .exchange()
            .expectStatus().isOk()
            .returnResult<PortfolioSummaryResponse>()
            .responseBody!!

        restTestClient.patch()
            .uri("/private/v1/profile")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"preferredCurrency":"USD"}""")
            .exchange()
            .expectStatus().isOk()

        val afterSwitch = restTestClient.get()
            .uri("/private/v1/overview")
            .exchange()
            .expectStatus().isOk()
            .returnResult<PortfolioSummaryResponse>()
            .responseBody!!

        assertEquals("USD", afterSwitch.displayCurrency)
        assertEquals(
            0,
            afterSwitch.totalCostBasis.compareTo(
                beforeSwitch.totalCostBasis.divide(BigDecimal("5.00"), 10, RoundingMode.HALF_UP)
            ),
        )
    }
```

Add the new imports needed at the top of the file:

```kotlin
import br.com.investlog.server.overview.rest.payloads.PortfolioSummaryResponse
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.test.assertEquals
```

(`assertEquals` is likely already imported; `PortfolioSummaryResponse`, `BigDecimal`, `RoundingMode` are new — check for duplicates before adding.)

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew test --tests "br.com.investlog.server.overview.rest.controllers.OverviewControllerTest"`
Expected: **compile failure** — both new tests reference `PortfolioSummaryResponse.displayCurrency`, which doesn't exist yet (the field is still called `baseCurrency` until Step 3). This is expected; don't try to get a clean pass/fail run until Steps 3-5 (rename + repository/service changes) are done. Once those land, re-run: `Order(5)` passes (today's per-wallet multiplier already uses the configured rate regardless of `preferredCurrency`, since BRL has always been the only rates anchor exercised). `Order(6)` is the real behavioral gate and FAILS against the *old* conversion logic — switching `preferredCurrency` to `USD` would have had no effect on `/overview`; `afterSwitch.totalCostBasis` would still equal `beforeSwitch.totalCostBasis` instead of being divided by 5. Treat Steps 3-5 as one atomic edit you make before re-running tests, not as independently-testable sub-steps.

- [ ] **Step 3: Rename the response field**

In `server/src/main/kotlin/br/com/investlog/server/overview/rest/payloads/PortfolioSummaryResponse.kt`:

```kotlin
package br.com.investlog.server.overview.rest.payloads

import java.math.BigDecimal

data class PortfolioSummaryResponse(
    val displayCurrency: String,
    val totalCostBasis: BigDecimal,
    val totalCurrentValue: BigDecimal,
    val totalGain: BigDecimal,
    val totalGainPct: BigDecimal?,
    val kindSummaries: List<KindSummaryResponse>,
)
```

- [ ] **Step 4: Convert to the requested display currency in the repository**

In `server/src/main/kotlin/br/com/investlog/server/overview/domain/repositories/OverviewRepository.kt`, replace `findSummary` and `findSeries`:

```kotlin
    fun findSummary(userId: Long, displayCurrency: String): PortfolioSummaryResponse {
        val overview = HOLDINGS_OVERVIEW.`as`("overview")
        val wallets = WALLETS.`as`("wallets")
        val currencyRates = CURRENCY_RATES.`as`("currency_rates")

        val displayCurrencyRate = displayCurrencyRate(userId, displayCurrency)
        val appliedRate = DSL.coalesce(currencyRates.RATE, BigDecimal.ONE).div(displayCurrencyRate)
        // `Field<BigDecimal>.div(Number)` should resolve directly; if jOOQ's overload
        // resolution complains, use `.div(DSL.`val`(displayCurrencyRate))` instead.

        val kindSummaries = dsl.select(
            overview.KIND,
            DSL.count().`as`("holding_count"),
            DSL.coalesce(DSL.sum(overview.COST_BASIS.mul(appliedRate)), BigDecimal.ZERO).`as`("total_cost_basis"),
            DSL.coalesce(DSL.sum(overview.CURRENT_VALUE.mul(appliedRate)), BigDecimal.ZERO).`as`("total_current_value"),
        )
            .from(overview)
            .join(wallets).on(wallets.ID.eq(overview.WALLET_ID))
            .leftJoin(currencyRates)
                .on(currencyRates.USER_ID.eq(wallets.USER_ID))
                .and(currencyRates.CURRENCY_CODE.eq(wallets.CURRENCY))
            .where(wallets.USER_ID.eq(userId))
            .groupBy(overview.KIND)
            .fetch { record ->
                val totalCostBasis = record.get("total_cost_basis", BigDecimal::class.java) ?: BigDecimal.ZERO
                val totalCurrentValue = record.get("total_current_value", BigDecimal::class.java) ?: BigDecimal.ZERO
                val totalGain = totalCurrentValue - totalCostBasis

                KindSummaryResponse(
                    kind = record.get(overview.KIND)!!.literal,
                    holdingCount = record.get("holding_count", Int::class.java) ?: 0,
                    totalCostBasis = totalCostBasis,
                    totalCurrentValue = totalCurrentValue,
                    totalGain = totalGain,
                    totalGainPct = gainPct(totalGain, totalCostBasis),
                )
            }

        val totalCostBasis = kindSummaries.fold(BigDecimal.ZERO) { acc, kind -> acc + kind.totalCostBasis }
        val totalCurrentValue = kindSummaries.fold(BigDecimal.ZERO) { acc, kind -> acc + kind.totalCurrentValue }
        val totalGain = totalCurrentValue - totalCostBasis

        return PortfolioSummaryResponse(
            displayCurrency = displayCurrency,
            totalCostBasis = totalCostBasis,
            totalCurrentValue = totalCurrentValue,
            totalGain = totalGain,
            totalGainPct = gainPct(totalGain, totalCostBasis),
            kindSummaries = kindSummaries,
        )
    }

    fun findSeries(userId: Long, displayCurrency: String): List<SeriesPointResponse> {
        val stockRates = CURRENCY_RATES.`as`("stock_rates")
        val cryptoRates = CURRENCY_RATES.`as`("crypto_rates")
        val fundRates = CURRENCY_RATES.`as`("fund_rates")
        val stockWallets = WALLETS.`as`("stock_wallets")
        val cryptoWallets = WALLETS.`as`("crypto_wallets")
        val fundWallets = WALLETS.`as`("fund_wallets")

        val displayCurrencyRate = displayCurrencyRate(userId, displayCurrency)

        data class MonthAmount(val month: String, val amount: BigDecimal)

        val stockAmounts = dsl.select(
            DSL.field("TO_CHAR({0}, 'YYYY-MM')", SQLDataType.VARCHAR, STOCK_LOTS.LOT_DATE).`as`("month"),
            STOCK_LOTS.QUANTITY.mul(STOCK_LOTS.PRICE)
                .mul(DSL.coalesce(stockRates.RATE, BigDecimal.ONE).div(displayCurrencyRate)).`as`("amount"),
        )
            .from(STOCK_LOTS)
            .join(STOCK_HOLDINGS).on(STOCK_HOLDINGS.ID.eq(STOCK_LOTS.STOCK_HOLDING_ID))
            .join(stockWallets).on(stockWallets.ID.eq(STOCK_HOLDINGS.WALLET_ID))
            .leftJoin(stockRates)
                .on(stockRates.USER_ID.eq(stockWallets.USER_ID))
                .and(stockRates.CURRENCY_CODE.eq(stockWallets.CURRENCY))
            .where(stockWallets.USER_ID.eq(userId))
            .fetch { MonthAmount(it.get("month", String::class.java)!!, it.get("amount", BigDecimal::class.java) ?: BigDecimal.ZERO) }

        val cryptoAmounts = dsl.select(
            DSL.field("TO_CHAR({0}, 'YYYY-MM')", SQLDataType.VARCHAR, CRYPTO_LOTS.LOT_DATE).`as`("month"),
            CRYPTO_LOTS.QUANTITY.mul(CRYPTO_LOTS.PRICE)
                .mul(DSL.coalesce(cryptoRates.RATE, BigDecimal.ONE).div(displayCurrencyRate)).`as`("amount"),
        )
            .from(CRYPTO_LOTS)
            .join(CRYPTO_HOLDINGS).on(CRYPTO_HOLDINGS.ID.eq(CRYPTO_LOTS.CRYPTO_HOLDING_ID))
            .join(cryptoWallets).on(cryptoWallets.ID.eq(CRYPTO_HOLDINGS.WALLET_ID))
            .leftJoin(cryptoRates)
                .on(cryptoRates.USER_ID.eq(cryptoWallets.USER_ID))
                .and(cryptoRates.CURRENCY_CODE.eq(cryptoWallets.CURRENCY))
            .where(cryptoWallets.USER_ID.eq(userId))
            .fetch { MonthAmount(it.get("month", String::class.java)!!, it.get("amount", BigDecimal::class.java) ?: BigDecimal.ZERO) }

        val fundAmounts = dsl.select(
            DSL.field("TO_CHAR({0}, 'YYYY-MM')", SQLDataType.VARCHAR, FUND_CONTRIBUTIONS.CONTRIBUTION_DATE).`as`("month"),
            FUND_CONTRIBUTIONS.AMOUNT
                .mul(DSL.coalesce(fundRates.RATE, BigDecimal.ONE).div(displayCurrencyRate)).`as`("amount"),
        )
            .from(FUND_CONTRIBUTIONS)
            .join(FUND_HOLDINGS).on(FUND_HOLDINGS.ID.eq(FUND_CONTRIBUTIONS.FUND_HOLDING_ID))
            .join(fundWallets).on(fundWallets.ID.eq(FUND_HOLDINGS.WALLET_ID))
            .leftJoin(fundRates)
                .on(fundRates.USER_ID.eq(fundWallets.USER_ID))
                .and(fundRates.CURRENCY_CODE.eq(fundWallets.CURRENCY))
            .where(fundWallets.USER_ID.eq(userId))
            .fetch { MonthAmount(it.get("month", String::class.java)!!, it.get("amount", BigDecimal::class.java) ?: BigDecimal.ZERO) }

        val grouped = (stockAmounts + cryptoAmounts + fundAmounts)
            .groupBy { it.month }
            .mapValues { entry -> entry.value.fold(BigDecimal.ZERO) { acc, point -> acc + point.amount } }
            .toSortedMap()

        var cumulative = BigDecimal.ZERO
        return grouped.map { (month, total) ->
            cumulative += total
            SeriesPointResponse(month = month, totalInvested = cumulative)
        }
    }

    /**
     * Rate of [displayCurrency] relative to the rates-anchor currency (1 if it has no
     * configured row, e.g. it IS the anchor — the anchor's own row always stores rate=1).
     */
    private fun displayCurrencyRate(userId: Long, displayCurrency: String): BigDecimal =
        dsl.select(CURRENCY_RATES.RATE)
            .from(CURRENCY_RATES)
            .where(CURRENCY_RATES.USER_ID.eq(userId))
            .and(CURRENCY_RATES.CURRENCY_CODE.eq(displayCurrency))
            .fetchOne(CURRENCY_RATES.RATE) ?: BigDecimal.ONE

    private fun gainPct(gain: BigDecimal, costBasis: BigDecimal): BigDecimal? =
        if (costBasis.signum() != 0) gain.divide(costBasis, 10, RoundingMode.HALF_UP).multiply(BigDecimal("100"))
        else null
```

- [ ] **Step 5: Pass the user's preferred currency from the service**

In `server/src/main/kotlin/br/com/investlog/server/overview/domain/services/OverviewService.kt`:

```kotlin
package br.com.investlog.server.overview.domain.services

import br.com.investlog.server.overview.domain.repositories.OverviewRepository
import br.com.investlog.server.overview.rest.payloads.PortfolioSummaryResponse
import br.com.investlog.server.overview.rest.payloads.SeriesPointResponse
import br.com.investlog.server.shared.security.CurrentUserProvider
import org.springframework.stereotype.Service

@Service
class OverviewService(
    private val currentUserProvider: CurrentUserProvider,
    private val overviewRepository: OverviewRepository,
) {

    fun getSummary(): PortfolioSummaryResponse {
        val user = currentUserProvider.getCurrentUser()
        return overviewRepository.findSummary(user.id, user.preferredCurrency)
    }

    fun getSeries(): List<SeriesPointResponse> {
        val user = currentUserProvider.getCurrentUser()
        return overviewRepository.findSeries(user.id, user.preferredCurrency)
    }
}
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew test --tests "br.com.investlog.server.overview.rest.controllers.OverviewControllerTest"`
Expected: PASS — both new tests green, and the pre-existing `Order(1)`-`Order(4)` tests still pass unchanged (default `preferredCurrency` is `BRL`, and BRL-only wallets always have `appliedRate = 1/1 = 1`, identical to today's behavior).

Run the full backend suite to catch any other consumer of the renamed field or old two-arg-less repository methods:

Run: `./gradlew test`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add server/src/main/kotlin/br/com/investlog/server/overview server/src/test/kotlin/br/com/investlog/server/overview
git commit -m "feat: convert overview totals to the user's preferred display currency"
```

---

### Task 3: Frontend profile types and API method

**Files:**
- Modify: `client/src/types.ts:134-138`
- Modify: `client/src/api/profile.ts`

**Interfaces:**
- Produces: `ProfileResponse { name, email, avatarUrl, accentColor, preferredCurrency }`, `ProfileUpdateRequest { accentColor?: string; preferredCurrency?: string }`, `profileApi.updateProfile(request: ProfileUpdateRequest): Promise<ProfileResponse>`.

- [ ] **Step 1: Extend the types**

In `client/src/types.ts`, replace the `ProfileResponse` interface (lines 134-138):

```typescript
export interface ProfileResponse {
  name: string
  email: string
  avatarUrl: string | null
  accentColor: string
  preferredCurrency: string
}

export interface ProfileUpdateRequest {
  accentColor?: string
  preferredCurrency?: string
}
```

Also rename the `baseCurrency` field on `PortfolioSummary` (lines 56-63):

```typescript
export interface PortfolioSummary {
  displayCurrency: string
  totalCostBasis: number
  totalCurrentValue: number
  totalGain: number
  totalGainPct: number | null
  kindSummaries: KindSummary[]
}
```

- [ ] **Step 2: Add the API method**

In `client/src/api/profile.ts`:

```typescript
import { apiClient } from './client'
import type { ProfileResponse, ProfileUpdateRequest } from '@/types'

export const profileApi = {
  getProfile(): Promise<ProfileResponse> {
    return apiClient.get<ProfileResponse>('/profile').then((response) => response.data)
  },
  updateProfile(request: ProfileUpdateRequest): Promise<ProfileResponse> {
    return apiClient.patch<ProfileResponse>('/profile', request).then((response) => response.data)
  },
}
```

- [ ] **Step 3: Verify the build**

Run: `npm run type-check`
Expected: Fails at this point — `OverviewView.vue` and `TheTopNav.vue` still reference `summary.baseCurrency` / `ratesStore.baseCurrency` against the old shape. This is expected; Task 6 fixes those usages. (If you'd rather keep the build green at every commit, do Task 3 and Task 6 together — they're small enough to combine. The granularity above keeps each task independently reviewable.)

- [ ] **Step 4: Commit**

```bash
git add client/src/types.ts client/src/api/profile.ts
git commit -m "feat: add preferredCurrency to the profile API contract"
```

---

### Task 4: `useCurrencyStore` — display currency + conversion math

**Files:**
- Create: `client/src/stores/currency.ts`
- Create: `client/src/stores/currency.spec.ts`

**Interfaces:**
- Consumes: `useRatesStore()` → `{ baseCurrency: ComputedRef<string>, rates: Ref<CurrencyRate[]> }` (existing, `client/src/stores/rates.ts`); `profileApi.getProfile()` / `profileApi.updateProfile()` (Task 3).
- Produces: `useCurrencyStore()` → `{ displayCurrency: Ref<string>, loaded: Ref<boolean>, loading: Ref<boolean>, load(): Promise<void>, setDisplayCurrency(currencyCode: string): Promise<void>, convert(amount: number, fromCurrency: string): number }`. Every later task (5-8) calls `currencyStore.convert(amount, currencyCode)` and reads `currencyStore.displayCurrency`.

- [ ] **Step 1: Write the failing tests**

Create `client/src/stores/currency.spec.ts`:

```typescript
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useCurrencyStore } from './currency'
import { useRatesStore } from './rates'
import { profileApi } from '@/api/profile'

vi.mock('@/api/profile')

describe('useCurrencyStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('returns the amount unchanged when the source currency matches the display currency', () => {
    const currencyStore = useCurrencyStore()
    currencyStore.displayCurrency = 'BRL'
    expect(currencyStore.convert(250, 'BRL')).toBe(250)
  })

  it('converts from the rates-anchor currency into a non-anchor display currency', () => {
    const ratesStore = useRatesStore()
    ratesStore.rates = [
      { currencyCode: 'BRL', rate: 1, isBase: true },
      { currencyCode: 'USD', rate: 5, isBase: false },
    ]
    const currencyStore = useCurrencyStore()
    currencyStore.displayCurrency = 'USD'
    expect(currencyStore.convert(100, 'BRL')).toBeCloseTo(20)
  })

  it('converts from a non-anchor currency into the rates-anchor display currency', () => {
    const ratesStore = useRatesStore()
    ratesStore.rates = [
      { currencyCode: 'BRL', rate: 1, isBase: true },
      { currencyCode: 'USD', rate: 5, isBase: false },
    ]
    const currencyStore = useCurrencyStore()
    currencyStore.displayCurrency = 'BRL'
    expect(currencyStore.convert(20, 'USD')).toBeCloseTo(100)
  })

  it('falls back to a 1:1 rate when the currency has no configured row', () => {
    const ratesStore = useRatesStore()
    ratesStore.rates = [{ currencyCode: 'BRL', rate: 1, isBase: true }]
    const currencyStore = useCurrencyStore()
    currencyStore.displayCurrency = 'EUR'
    expect(currencyStore.convert(100, 'BRL')).toBe(100)
  })

  it('setDisplayCurrency persists the new preference via PATCH /profile', async () => {
    vi.mocked(profileApi.updateProfile).mockResolvedValue({
      name: 'Arthur',
      email: 'arthur@example.com',
      avatarUrl: null,
      accentColor: 'teal',
      preferredCurrency: 'USD',
    })
    const currencyStore = useCurrencyStore()
    await currencyStore.setDisplayCurrency('USD')
    expect(profileApi.updateProfile).toHaveBeenCalledWith({ preferredCurrency: 'USD' })
    expect(currencyStore.displayCurrency).toBe('USD')
  })

  it('load() reads the preferred currency from the profile endpoint', async () => {
    vi.mocked(profileApi.getProfile).mockResolvedValue({
      name: 'Arthur',
      email: 'arthur@example.com',
      avatarUrl: null,
      accentColor: 'teal',
      preferredCurrency: 'USD',
    })
    const currencyStore = useCurrencyStore()
    await currencyStore.load()
    expect(currencyStore.displayCurrency).toBe('USD')
    expect(currencyStore.loaded).toBe(true)
  })
})
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `npm run test -- currency.spec.ts`
Expected: FAIL — `Cannot find module './currency'` (the store doesn't exist yet).

- [ ] **Step 3: Write the implementation**

Create `client/src/stores/currency.ts`:

```typescript
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { profileApi } from '@/api/profile'
import { useRatesStore } from '@/stores/rates'

export const useCurrencyStore = defineStore('currency', () => {
  const ratesStore = useRatesStore()
  const displayCurrency = ref('BRL')
  const loaded = ref(false)
  const loading = ref(false)

  async function load() {
    if (loaded.value) return
    loading.value = true
    try {
      const profile = await profileApi.getProfile()
      displayCurrency.value = profile.preferredCurrency
      loaded.value = true
    } finally {
      loading.value = false
    }
  }

  async function setDisplayCurrency(currencyCode: string) {
    if (currencyCode === displayCurrency.value) return
    const profile = await profileApi.updateProfile({ preferredCurrency: currencyCode })
    displayCurrency.value = profile.preferredCurrency
  }

  function rateToAnchor(currencyCode: string): number {
    if (currencyCode === ratesStore.baseCurrency) return 1
    return ratesStore.rates.find((rate) => rate.currencyCode === currencyCode)?.rate ?? 1
  }

  function convert(amount: number, fromCurrency: string): number {
    if (fromCurrency === displayCurrency.value) return amount
    return amount * (rateToAnchor(fromCurrency) / rateToAnchor(displayCurrency.value))
  }

  return { displayCurrency, loaded, loading, load, setDisplayCurrency, convert }
})
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `npm run test -- currency.spec.ts`
Expected: PASS — all 6 tests green.

- [ ] **Step 5: Commit**

```bash
git add client/src/stores/currency.ts client/src/stores/currency.spec.ts
git commit -m "feat: add useCurrencyStore for display-currency conversion"
```

---

### Task 5: Wire the working selector into the navbar

**Files:**
- Modify: `client/src/components/layout/TheNavbar.vue`
- Modify: `client/src/assets/styles.css:342-359`

**Interfaces:**
- Consumes: `useCurrencyStore()` (Task 4), `useOverviewStore().refresh()` (existing, `client/src/stores/overview.ts:28-31`), `useRatesStore().currencyCodes` (existing, `client/src/stores/rates.ts:12`).

- [ ] **Step 1: Replace the static chip with a working selector**

In `client/src/components/layout/TheNavbar.vue`, update the script block:

```vue
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { storeToRefs } from 'pinia'
import AppIcon from '@/components/AppIcon.vue'
import Avatar from '@/components/ui/Avatar.vue'
import { useRatesStore } from '@/stores/rates'
import { useCurrencyStore } from '@/stores/currency'
import { useOverviewStore } from '@/stores/overview'
import { useAppearanceStore } from '@/stores/appearance'
import { profileApi } from '@/api/profile'
import type { ProfileResponse } from '@/types'

const ratesStore = useRatesStore()
const currencyStore = useCurrencyStore()
const overviewStore = useOverviewStore()
const appearance = useAppearanceStore()
const { dark } = storeToRefs(appearance)

const profile = ref<ProfileResponse | null>(null)

const currencyOptions = computed(() =>
  ratesStore.currencyCodes.length > 0 ? ratesStore.currencyCodes : ['BRL', 'USD'],
)

onMounted(async () => {
  profile.value = await profileApi.getProfile()
  await Promise.all([ratesStore.load(), currencyStore.load()])
})

async function onCurrencyChange(currencyCode: string) {
  await currencyStore.setDisplayCurrency(currencyCode)
  await overviewStore.refresh()
}

function initials(name: string): string {
  return name
    .split(' ')
    .filter((word) => word.length > 0)
    .slice(0, 2)
    .map((word) => word[0].toUpperCase())
    .join('')
}
</script>
```

Replace the chip in the template:

```vue
      <b-select
        :model-value="currencyStore.displayCurrency"
        size="is-small"
        class="currency-select"
        @update:model-value="onCurrencyChange"
      >
        <option v-for="currencyCode in currencyOptions" :key="currencyCode" :value="currencyCode">
          {{ currencyCode }}
        </option>
      </b-select>
```

(replaces `<span class="base-chip"><AppIcon name="repeat" :size="14" />Base&nbsp;<b>{{ baseCurrency }}</b></span>` — note `baseCurrency` is no longer destructured from `ratesStore` in the script block above, since nothing else in this file needs it.)

- [ ] **Step 2: Match the navbar's control height**

In `client/src/assets/styles.css`, after the existing `.base-chip` rules (around line 359), add:

```css
.currency-select select {
    height: 32px;
}
```

(`.base-chip` itself can stay in the stylesheet — `SettingsView.vue` still uses that class for its own "Moeda base" label, which this plan does not touch.)

- [ ] **Step 3: Manually verify in the browser**

Run: `npm run dev`
Open the app, confirm:
- The navbar shows a `BRL`/`USD` select instead of the static "Base BRL" chip.
- Switching it does not throw console errors (Overview values will not visibly change yet until Task 6 reads the renamed field — that's expected at this point).

- [ ] **Step 4: Commit**

```bash
git add client/src/components/layout/TheNavbar.vue client/src/assets/styles.css
git commit -m "feat: wire a working currency selector into the navbar"
```

---

### Task 6: Consume the renamed `displayCurrency` field in Overview + TopNav

**Files:**
- Modify: `client/src/views/OverviewView.vue:27-28`
- Modify: `client/src/components/layout/TheTopNav.vue`

**Interfaces:**
- Consumes: `PortfolioSummary.displayCurrency` (Task 3).

- [ ] **Step 1: Update `OverviewView.vue`**

Replace lines 27-28:

```typescript
const summary = computed(() => overviewStore.summary)
const baseCurrency = computed(() => summary.value?.displayCurrency ?? 'BRL')
```

(only the right-hand side of the second line changes — `summary.value?.baseCurrency` → `summary.value?.displayCurrency`; every other usage of the local `baseCurrency` computed in the rest of the file is unaffected.)

- [ ] **Step 2: Update `TheTopNav.vue`**

Replace the file's script and template currency references — `ratesStore.baseCurrency` is no longer the right source (it's the rates-anchor, not the display currency); use the overview summary instead:

```vue
<script setup lang="ts">
import { onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon, { type IconName } from '@/components/AppIcon.vue'
import { useOverviewStore } from '@/stores/overview'
import { fmt } from '@/composables/useFormat'

const overviewStore = useOverviewStore()
const route = useRoute()
const router = useRouter()

onMounted(() => {
  overviewStore.load()
})

const nav: { name: string; label: string; icon: IconName }[] = [
  { name: 'overview', label: 'Visão geral', icon: 'dashboard' },
  { name: 'wallets', label: 'Carteiras', icon: 'wallet' },
  { name: 'investments', label: 'Investimentos', icon: 'layers' },
  { name: 'settings', label: 'Configurações', icon: 'settings' },
]
</script>

<template>
  <nav class="topnav">
    <div class="topnav-inner">
      <div class="topnav-items">
        <button
          v-for="item in nav"
          :key="item.name"
          class="nav-item"
          :class="{ active: route.name === item.name }"
          :title="item.label"
          @click="router.push({ name: item.name })"
        >
          <span class="nav-icon"><AppIcon :name="item.icon" :size="18" /></span>
          <span class="nav-label">{{ item.label }}</span>
        </button>
      </div>
      <div class="topnav-total">
        <span class="tn-label">Total investido</span>
        <span class="tn-value">
          {{ fmt.money(overviewStore.summary?.totalCostBasis ?? 0, overviewStore.summary?.displayCurrency, { compact: true }) }}
        </span>
        <span class="tn-sub">· {{ overviewStore.summary?.displayCurrency ?? 'BRL' }}</span>
      </div>
    </div>
  </nav>
</template>
```

This removes `useRatesStore` and the `ratesStore.load()` call from `TheTopNav.vue` (`ratesStore` is still loaded by `TheNavbar.vue`, which mounts alongside it — see Task 5 — so rates data is still available app-wide).

- [ ] **Step 3: Verify the build**

Run: `npm run type-check`
Expected: PASS — no more references to the removed `baseCurrency` field anywhere in the frontend.

Run: `npm run test`
Expected: PASS.

- [ ] **Step 4: Manually verify in the browser**

Run: `npm run dev`
Switch the navbar's currency selector and confirm the Overview KPIs, chart, allocation legend, and type cards all re-render in the new currency after the selector's `overviewStore.refresh()` call resolves (this is end-to-end testable now since both ends of the contract — backend `displayCurrency` and frontend consumption — are wired).

- [ ] **Step 5: Commit**

```bash
git add client/src/views/OverviewView.vue client/src/components/layout/TheTopNav.vue
git commit -m "feat: render overview totals using the selected display currency"
```

---

### Task 7: Convert wallet card figures in `WalletsView.vue`

**Files:**
- Modify: `client/src/views/WalletsView.vue:1-23,125-140`

**Interfaces:**
- Consumes: `useCurrencyStore().convert(amount, fromCurrency)` and `useCurrencyStore().displayCurrency` (Task 4).

- [ ] **Step 1: Import and load the currency store**

In `client/src/views/WalletsView.vue`, update the script:

```typescript
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import {BButton, useDialog, useToast} from 'buefy'
import AppIcon, { type IconName } from '@/components/AppIcon.vue'
import Card from '@/components/ui/Card.vue'
import CardBody from '@/components/ui/CardBody.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import GainChip from '@/components/ui/GainChip.vue'
import { walletsApi } from '@/api/wallets'
import { useWalletsStore } from '@/stores/wallets'
import { useCurrencyStore } from '@/stores/currency'
import { useModals } from '@/composables/useModals'
import { fmt } from '@/composables/useFormat'
import { WALLET_TYPES } from '@/utils/walletTypes'
import type { WalletKind } from '@/types'

const dialog = useDialog()
const toast = useToast()
const walletsStore = useWalletsStore()
const currencyStore = useCurrencyStore()
const router = useRouter()
const modals = useModals()

onMounted(() => walletsStore.load())
```

(only the two new import lines and the `currencyStore` declaration are added; everything else in the script block is unchanged.)

- [ ] **Step 2: Convert the displayed figures**

Replace lines 125-140 (the `wallet-invested` and `wallet-result` blocks):

```vue
          <div class="wallet-invested">
            <div class="wi-value">
              {{ fmt.money(currencyStore.convert(wallet.totalInvested, wallet.currency), currencyStore.displayCurrency) }}
            </div>
            <div class="wi-base">Investido</div>
          </div>
          <div class="wallet-result">
            <div class="wallet-result-item">
              <div class="wallet-result-value">
                {{
                  wallet.currentValue == null
                    ? '—'
                    : fmt.money(currencyStore.convert(wallet.currentValue, wallet.currency), currencyStore.displayCurrency)
                }}
              </div>
              <div class="wallet-result-label">Valor atual</div>
            </div>
            <div class="wallet-result-item">
              <GainChip
                :value="wallet.gain == null ? null : currencyStore.convert(wallet.gain, wallet.currency)"
                :pct="wallet.gainPct"
                :cur="currencyStore.displayCurrency"
              />
              <div class="wallet-result-label">Resultado</div>
            </div>
          </div>
```

The `<span class="cur-chip">{{ wallet.currency }}</span>` tag earlier in the card (line 105) is left untouched — it still shows the wallet's real native currency, distinct from the converted figures.

- [ ] **Step 3: Manually verify in the browser**

Run: `npm run dev`
On the Carteiras page, create (or use an existing) wallet in `USD`. With the navbar selector on `BRL`, confirm its invested/current/gain figures show in BRL (converted). Switch the selector to `USD` and confirm that same wallet's figures now show unconverted (identical to its raw values), while a `BRL` wallet's figures now show converted into USD.

- [ ] **Step 4: Commit**

```bash
git add client/src/views/WalletsView.vue
git commit -m "feat: convert wallet card figures to the selected display currency"
```

---

### Task 8: Convert holdings table and lot/contribution detail figures

**Files:**
- Modify: `client/src/views/InvestmentsView.vue:1-25,241-256`
- Modify: `client/src/components/investments/HoldingDetailPanel.vue:1-9,193-260`

**Interfaces:**
- Consumes: `useCurrencyStore().convert(amount, fromCurrency)` and `useCurrencyStore().displayCurrency` (Task 4).

- [ ] **Step 1: Import the currency store in `InvestmentsView.vue`**

Add to the existing import block (after `useTypesListStore`):

```typescript
import { useCurrencyStore } from '@/stores/currency'
```

Add the instance next to the other store instances:

```typescript
const holdingsListStore = useHoldingsListStore()
const typesListStore = useTypesListStore()
const currencyStore = useCurrencyStore()
```

- [ ] **Step 2: Convert the table cells**

Replace the `c-num` cells (lines 242-256):

```vue
                  <td class="c-num">
                    <template v-if="row.kind !== 'FUNDS' && row.currentPrice != null">
                      {{ fmt.money(currencyStore.convert(row.currentPrice, row.walletCurrency), currencyStore.displayCurrency) }}
                    </template>
                    <template v-else-if="row.kind === 'FUNDS' && row.currentValue != null">
                      {{ fmt.money(currencyStore.convert(row.currentValue, row.walletCurrency), currencyStore.displayCurrency) }}
                    </template>
                    <span v-else class="gl-empty">—</span>
                  </td>
                  <td class="c-num">
                    <div class="cell-strong">
                      {{ fmt.money(currencyStore.convert(row.costBasis, row.walletCurrency), currencyStore.displayCurrency) }}
                    </div>
                  </td>
                  <td class="c-num">
                    <span v-if="row.currentValue == null" class="gl-empty">—</span>
                    <template v-else>
                      {{ fmt.money(currencyStore.convert(row.currentValue, row.walletCurrency), currencyStore.displayCurrency) }}
                    </template>
                  </td>
                  <td class="c-num">
                    <GainChip
                      :value="row.gain == null ? null : currencyStore.convert(row.gain, row.walletCurrency)"
                      :pct="row.gainPct"
                      :cur="currencyStore.displayCurrency"
                    />
                  </td>
```

- [ ] **Step 3: Import the currency store in `HoldingDetailPanel.vue`**

Add to the existing import block:

```typescript
import { useCurrencyStore } from '@/stores/currency'
```

Add the instance next to `dialog`/`toast`:

```typescript
const dialog = useDialog()
const toast = useToast()
const currencyStore = useCurrencyStore()
```

- [ ] **Step 4: Convert the lot/contribution rows and the average-cost footer**

Replace the contribution amount cell (line 205):

```vue
            <td class="c-num">
              {{ fmt.money(currencyStore.convert(contribution.amount, row.walletCurrency), currencyStore.displayCurrency) }}
            </td>
```

Replace the lot price/subtotal cells (lines 230-231):

```vue
            <td class="c-num">
              {{ fmt.money(currencyStore.convert(lot.price, row.walletCurrency), currencyStore.displayCurrency) }}
            </td>
            <td class="c-num">
              {{ fmt.money(currencyStore.convert(lot.quantity * lot.price, row.walletCurrency), currencyStore.displayCurrency) }}
            </td>
```

Replace the average-price footer note (lines 257-260):

```vue
      <span v-if="!isFund && quantity" class="avg-note">
        Preço médio
        <b>{{ fmt.money(currencyStore.convert(costBasis / quantity, row.walletCurrency), currencyStore.displayCurrency) }}</b>
      </span>
```

Do **not** touch `AddPositionModal` / `UpdatePriceModal` (lines 263-282) — those are input forms bound to `:wallet-currency="row.walletCurrency"` and must keep accepting amounts in the wallet's native currency.

- [ ] **Step 5: Manually verify in the browser**

Run: `npm run dev`
Open the Investimentos page, expand a holding row that belongs to a wallet whose currency differs from the navbar's current selection — confirm the row's price/invested/current/gain cells, and the expanded lot/contribution rows and "Preço médio" footer, all show converted values with the correct currency symbol. Switch the navbar selector and confirm everything re-renders converted to the new currency (or unconverted, for wallets whose native currency now matches).

- [ ] **Step 6: Run the full frontend test suite**

Run: `npm run test`
Expected: PASS.

Run: `npm run type-check`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add client/src/views/InvestmentsView.vue client/src/components/investments/HoldingDetailPanel.vue
git commit -m "feat: convert holdings table and lot/contribution figures to the display currency"
```

---

## Summary

| Task | Layer | What it does |
|------|-------|---------------|
| 1 | Backend | Fixes a partial-`PATCH /profile` clobber bug that would otherwise reset `accentColor` every time the new selector runs |
| 2 | Backend | `GET /overview` and `/overview/series` now convert to the user's `preferredCurrency` instead of always the rates-anchor currency |
| 3 | Frontend | Extends `ProfileResponse`/adds `ProfileUpdateRequest`/`profileApi.updateProfile`; renames `PortfolioSummary.baseCurrency` → `displayCurrency` |
| 4 | Frontend | New `useCurrencyStore` — the single source of truth for the selected display currency and the conversion formula |
| 5 | Frontend | The actual working selector, replacing the static "Base BRL" chip in the navbar |
| 6 | Frontend | Overview KPIs/chart and the top-nav total now render in the selected currency |
| 7 | Frontend | Wallet cards convert their figures |
| 8 | Frontend | Holdings table + lot/contribution detail rows convert their figures |
