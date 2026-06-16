# Remaining Endpoints Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the four missing API modules (wallets, stock holdings, crypto holdings, fund holdings) so every table in the `finances` schema has a corresponding REST endpoint.

**Architecture:** Four Spring MVC modules following the existing `rest/{controllers,payloads}` + `domain/{services,repositories}` layout. The `wallets` module is built first and exposes `WalletService.resolveId()` which the three holdings modules call to convert a wallet UUID path variable into an internal PK while enforcing ownership. Holdings always embed their child records (lots/contributions) in the response, fetched with a single jOOQ `multiset` query.

**Tech Stack:** Kotlin 2.3 / Spring Boot 4.1 / jOOQ (DSLContext + multiset) / Testcontainers integration tests via `BaseIntegrationTest` / `RestTestClient` / JUnit 5 `@Order`.

---

## File Map

### wallets module
| Action | Path |
|--------|------|
| Create | `server/src/main/kotlin/br/com/investlog/server/wallets/rest/payloads/WalletKind.kt` |
| Create | `server/src/main/kotlin/br/com/investlog/server/wallets/rest/payloads/WalletResponse.kt` |
| Create | `server/src/main/kotlin/br/com/investlog/server/wallets/rest/payloads/WalletCreateRequest.kt` |
| Create | `server/src/main/kotlin/br/com/investlog/server/wallets/rest/payloads/WalletUpdateRequest.kt` |
| Create | `server/src/main/kotlin/br/com/investlog/server/wallets/domain/repositories/WalletRepository.kt` |
| Create | `server/src/main/kotlin/br/com/investlog/server/wallets/domain/services/WalletService.kt` |
| Create | `server/src/main/kotlin/br/com/investlog/server/wallets/rest/controllers/WalletController.kt` |
| Create | `server/src/test/kotlin/br/com/investlog/server/wallets/rest/controllers/WalletControllerTest.kt` |

### stockholdings module
| Action | Path |
|--------|------|
| Create | `server/src/main/kotlin/br/com/investlog/server/stockholdings/rest/payloads/LotCreateRequest.kt` |
| Create | `server/src/main/kotlin/br/com/investlog/server/stockholdings/rest/payloads/LotResponse.kt` |
| Create | `server/src/main/kotlin/br/com/investlog/server/stockholdings/rest/payloads/StockHoldingCreateRequest.kt` |
| Create | `server/src/main/kotlin/br/com/investlog/server/stockholdings/rest/payloads/StockHoldingUpdateRequest.kt` |
| Create | `server/src/main/kotlin/br/com/investlog/server/stockholdings/rest/payloads/StockHoldingResponse.kt` |
| Create | `server/src/main/kotlin/br/com/investlog/server/stockholdings/domain/repositories/StockHoldingRepository.kt` |
| Create | `server/src/main/kotlin/br/com/investlog/server/stockholdings/domain/repositories/StockLotRepository.kt` |
| Create | `server/src/main/kotlin/br/com/investlog/server/stockholdings/domain/services/StockHoldingService.kt` |
| Create | `server/src/main/kotlin/br/com/investlog/server/stockholdings/rest/controllers/StockHoldingController.kt` |
| Create | `server/src/test/kotlin/br/com/investlog/server/stockholdings/rest/controllers/StockHoldingControllerTest.kt` |

### cryptoholdings module
| Action | Path |
|--------|------|
| Create | `server/src/main/kotlin/br/com/investlog/server/cryptoholdings/rest/payloads/CryptoHoldingCreateRequest.kt` |
| Create | `server/src/main/kotlin/br/com/investlog/server/cryptoholdings/rest/payloads/CryptoHoldingUpdateRequest.kt` |
| Create | `server/src/main/kotlin/br/com/investlog/server/cryptoholdings/rest/payloads/CryptoHoldingResponse.kt` |
| Create | `server/src/main/kotlin/br/com/investlog/server/cryptoholdings/domain/repositories/CryptoHoldingRepository.kt` |
| Create | `server/src/main/kotlin/br/com/investlog/server/cryptoholdings/domain/repositories/CryptoLotRepository.kt` |
| Create | `server/src/main/kotlin/br/com/investlog/server/cryptoholdings/domain/services/CryptoHoldingService.kt` |
| Create | `server/src/main/kotlin/br/com/investlog/server/cryptoholdings/rest/controllers/CryptoHoldingController.kt` |
| Create | `server/src/test/kotlin/br/com/investlog/server/cryptoholdings/rest/controllers/CryptoHoldingControllerTest.kt` |

### fundholdings module
| Action | Path |
|--------|------|
| Create | `server/src/main/kotlin/br/com/investlog/server/fundholdings/rest/payloads/ContributionCreateRequest.kt` |
| Create | `server/src/main/kotlin/br/com/investlog/server/fundholdings/rest/payloads/ContributionResponse.kt` |
| Create | `server/src/main/kotlin/br/com/investlog/server/fundholdings/rest/payloads/FundHoldingCreateRequest.kt` |
| Create | `server/src/main/kotlin/br/com/investlog/server/fundholdings/rest/payloads/FundHoldingUpdateRequest.kt` |
| Create | `server/src/main/kotlin/br/com/investlog/server/fundholdings/rest/payloads/FundHoldingResponse.kt` |
| Create | `server/src/main/kotlin/br/com/investlog/server/fundholdings/domain/repositories/FundHoldingRepository.kt` |
| Create | `server/src/main/kotlin/br/com/investlog/server/fundholdings/domain/repositories/FundContributionRepository.kt` |
| Create | `server/src/main/kotlin/br/com/investlog/server/fundholdings/domain/services/FundHoldingService.kt` |
| Create | `server/src/main/kotlin/br/com/investlog/server/fundholdings/rest/controllers/FundHoldingController.kt` |
| Create | `server/src/test/kotlin/br/com/investlog/server/fundholdings/rest/controllers/FundHoldingControllerTest.kt` |

---

## Key Conventions (read before touching any file)

- **Package root:** `br.com.investlog.server`
- **URL prefix:** `WebMvcConfig` automatically prepends `/private/v1` to every `@RestController` — so `@RequestMapping("/wallets")` maps to `/private/v1/wallets`.
- **User scoping:** call `currentUserProvider.getCurrentUser().id` at the service layer to get the internal user PK.
- **Pagination:** use `pagedModelOf(content, pageable, total)` from `shared/persistence/Paging.kt`.
- **404:** throw `NotFoundException(message)` from `shared/exceptions`; `GlobalExceptionHandler` maps it to HTTP 404.
- **409:** `DataIntegrityViolationException` (e.g. duplicate name within a wallet) is auto-mapped to HTTP 409.
- **jOOQ table references:** import from `br.com.investlog.server.jooq.finances.tables.references.*` (e.g. `WALLETS`, `STOCK_HOLDINGS`, `STOCK_LOTS`). The jOOQ-generated `WalletKind` enum is at `br.com.investlog.server.jooq.finances.enums.WalletKind`.
- **`@Transactional`:** place on service methods that perform more than one write (e.g. create holding + insert first lot). Read-only methods don't need it.
- **Tests:** extend `BaseIntegrationTest`, autowire `RestTestClient`, use `@Order` for ordered state. The dev seed inserts one user (`google_sub = 'dev-user'`) and three currency rates — **no** wallets or holdings. Each test class must create its own data.

---

## Task 1 — Wallets Module

**Endpoints:**
- `GET  /private/v1/wallets` — paginated list, user-scoped
- `POST /private/v1/wallets` — create wallet
- `GET  /private/v1/wallets/{id}` — get single wallet by external UUID
- `PATCH /private/v1/wallets/{id}` — rename wallet
- `DELETE /private/v1/wallets/{id}` — delete wallet (DB CASCADE handles child holdings)

- [ ] **Step 1.1 — Write the failing test**

```kotlin
// server/src/test/kotlin/br/com/investlog/server/wallets/rest/controllers/WalletControllerTest.kt
package br.com.investlog.server.wallets.rest.controllers

import br.com.investlog.server.BaseIntegrationTest
import org.junit.jupiter.api.Order
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.RestTestClient
import kotlin.test.Test

class WalletControllerTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTestClient: RestTestClient

    @Test
    @Order(1)
    fun `creates a wallet and returns 201`() {
        restTestClient.post()
            .uri("/private/v1/wallets")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"My Stocks","kind":"stocks","currency":"BRL"}""")
            .exchange()
            .expectStatus().isCreated()
            .expectBody()
            .jsonPath("$.id").isNotEmpty()
            .jsonPath("$.name").isEqualTo("My Stocks")
            .jsonPath("$.kind").isEqualTo("stocks")
            .jsonPath("$.currency").isEqualTo("BRL")
    }

    @Test
    @Order(2)
    fun `lists wallets with pagination`() {
        restTestClient.get()
            .uri("/private/v1/wallets")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.page.totalElements").isEqualTo(1)
            .jsonPath("$.content[0].name").isEqualTo("My Stocks")
    }

    @Test
    @Order(3)
    fun `fetches a single wallet by id`() {
        val id = restTestClient.get()
            .uri("/private/v1/wallets")
            .exchange()
            .returnResult(String::class.java)
            .let {
                restTestClient.get()
                    .uri("/private/v1/wallets")
                    .exchange()
                    .expectBody()
                    .returnResult()
            }
            .let {
                // Extract id from list
                restTestClient.get()
                    .uri("/private/v1/wallets")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.content[0].id").isNotEmpty()
            }

        // Simpler: just GET /wallets, extract first id, then GET /wallets/{id}
        val listBody = restTestClient.get()
            .uri("/private/v1/wallets")
            .exchange()
            .expectBody(String::class.java)
            .returnResult()
            .responseBody!!

        val walletId = org.springframework.test.web.servlet.client.returnResult<br.com.investlog.server.wallets.rest.payloads.WalletResponse>(
            restTestClient.get().uri("/private/v1/wallets").exchange()
        )
        // see simpler pattern below
    }
}
```

> The test above uses a complex chain — replace the single-wallet test body with the simpler pattern below:

```kotlin
// server/src/test/kotlin/br/com/investlog/server/wallets/rest/controllers/WalletControllerTest.kt
package br.com.investlog.server.wallets.rest.controllers

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.wallets.rest.payloads.WalletResponse
import org.junit.jupiter.api.Order
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.returnResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class WalletControllerTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTestClient: RestTestClient

    private fun createWallet(name: String = "My Stocks", kind: String = "stocks", currency: String = "BRL"): WalletResponse =
        restTestClient.post()
            .uri("/private/v1/wallets")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"$name","kind":"$kind","currency":"$currency"}""")
            .exchange()
            .expectStatus().isCreated()
            .returnResult<WalletResponse>()
            .responseBody!!

    @Test
    @Order(1)
    fun `creates a wallet and returns 201`() {
        val wallet = createWallet()
        assertNotNull(wallet.id)
        assertEquals("My Stocks", wallet.name)
        assertEquals("stocks", wallet.kind)
        assertEquals("BRL", wallet.currency)
    }

    @Test
    @Order(2)
    fun `lists wallets`() {
        restTestClient.get()
            .uri("/private/v1/wallets")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.page.totalElements").isEqualTo(1)
            .jsonPath("$.content[0].name").isEqualTo("My Stocks")
    }

    @Test
    @Order(3)
    fun `fetches a wallet by id`() {
        val wallet = createWallet("Crypto Bag", "crypto", "USD")
        restTestClient.get()
            .uri("/private/v1/wallets/${wallet.id}")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.id").isEqualTo(wallet.id.toString())
            .jsonPath("$.name").isEqualTo("Crypto Bag")
    }

    @Test
    @Order(4)
    fun `renames a wallet`() {
        val wallet = createWallet("Old Name", "funds", "EUR")
        restTestClient.patch()
            .uri("/private/v1/wallets/${wallet.id}")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"New Name"}""")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.name").isEqualTo("New Name")
    }

    @Test
    @Order(5)
    fun `deletes a wallet`() {
        val wallet = createWallet("To Delete", "stocks", "BRL")
        restTestClient.delete()
            .uri("/private/v1/wallets/${wallet.id}")
            .exchange()
            .expectStatus().isNoContent()

        restTestClient.get()
            .uri("/private/v1/wallets/${wallet.id}")
            .exchange()
            .expectStatus().isNotFound()
    }

    @Test
    @Order(6)
    fun `returns 400 when kind is missing`() {
        restTestClient.post()
            .uri("/private/v1/wallets")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"X","currency":"BRL"}""")
            .exchange()
            .expectStatus().isBadRequest()
    }
}
```

- [ ] **Step 1.2 — Run the test to confirm it fails**

```
./gradlew test --tests "br.com.investlog.server.wallets.rest.controllers.WalletControllerTest"
```

Expected: compilation error (packages/classes don't exist yet).

- [ ] **Step 1.3 — Create payload classes**

```kotlin
// server/src/main/kotlin/br/com/investlog/server/wallets/rest/payloads/WalletKind.kt
package br.com.investlog.server.wallets.rest.payloads

enum class WalletKind { stocks, crypto, funds }
```

```kotlin
// server/src/main/kotlin/br/com/investlog/server/wallets/rest/payloads/WalletResponse.kt
package br.com.investlog.server.wallets.rest.payloads

import java.time.OffsetDateTime
import java.util.UUID

data class WalletResponse(
    val id: UUID,
    val name: String,
    val kind: String,
    val currency: String,
    val createdAt: OffsetDateTime,
)
```

```kotlin
// server/src/main/kotlin/br/com/investlog/server/wallets/rest/payloads/WalletCreateRequest.kt
package br.com.investlog.server.wallets.rest.payloads

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

data class WalletCreateRequest(
    @field:NotBlank val name: String,
    @field:NotNull val kind: WalletKind,
    @field:NotBlank val currency: String,
)
```

```kotlin
// server/src/main/kotlin/br/com/investlog/server/wallets/rest/payloads/WalletUpdateRequest.kt
package br.com.investlog.server.wallets.rest.payloads

import jakarta.validation.constraints.NotBlank

data class WalletUpdateRequest(
    @field:NotBlank val name: String,
)
```

- [ ] **Step 1.4 — Create the repository**

```kotlin
// server/src/main/kotlin/br/com/investlog/server/wallets/domain/repositories/WalletRepository.kt
package br.com.investlog.server.wallets.domain.repositories

import br.com.investlog.server.jooq.finances.tables.records.WalletsRecord
import br.com.investlog.server.jooq.finances.tables.references.WALLETS
import br.com.investlog.server.shared.persistence.pagedModelOf
import br.com.investlog.server.wallets.rest.payloads.WalletKind
import br.com.investlog.server.wallets.rest.payloads.WalletResponse
import org.jooq.DSLContext
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.util.UUID
import br.com.investlog.server.jooq.finances.enums.WalletKind as JooqWalletKind

@Repository
class WalletRepository(private val dsl: DSLContext) {

    fun findAll(userId: Long, pageable: Pageable): PagedModel<WalletResponse> {
        val content = dsl.selectFrom(WALLETS)
            .where(WALLETS.USER_ID.eq(userId))
            .orderBy(WALLETS.CREATED_AT.desc())
            .limit(pageable.pageSize)
            .offset(pageable.offset.toInt())
            .fetch()
            .map { it.toResponse() }

        val total = dsl.fetchCount(dsl.selectFrom(WALLETS).where(WALLETS.USER_ID.eq(userId)))

        return pagedModelOf(content, pageable, total.toLong())
    }

    fun create(userId: Long, name: String, kind: WalletKind, currency: String): WalletResponse =
        dsl.insertInto(WALLETS)
            .set(WALLETS.USER_ID, userId)
            .set(WALLETS.NAME, name)
            .set(WALLETS.KIND, JooqWalletKind.valueOf(kind.name))
            .set(WALLETS.CURRENCY, currency)
            .returning()
            .fetchSingle()
            .toResponse()

    fun findByExternalId(userId: Long, externalId: UUID): WalletResponse? =
        dsl.selectFrom(WALLETS)
            .where(WALLETS.USER_ID.eq(userId))
            .and(WALLETS.EXTERNAL_ID.eq(externalId))
            .fetchOne()
            ?.toResponse()

    fun findInternalId(userId: Long, externalId: UUID): Long? =
        dsl.select(WALLETS.ID)
            .from(WALLETS)
            .where(WALLETS.USER_ID.eq(userId))
            .and(WALLETS.EXTERNAL_ID.eq(externalId))
            .fetchOne(WALLETS.ID)

    fun update(userId: Long, externalId: UUID, name: String): WalletResponse? =
        dsl.update(WALLETS)
            .set(WALLETS.NAME, name)
            .set(WALLETS.UPDATED_AT, OffsetDateTime.now())
            .where(WALLETS.USER_ID.eq(userId))
            .and(WALLETS.EXTERNAL_ID.eq(externalId))
            .returning()
            .fetchOne()
            ?.toResponse()

    fun deleteByExternalId(userId: Long, externalId: UUID): Int =
        dsl.deleteFrom(WALLETS)
            .where(WALLETS.USER_ID.eq(userId))
            .and(WALLETS.EXTERNAL_ID.eq(externalId))
            .execute()

    private fun WalletsRecord.toResponse() = WalletResponse(
        id = externalId!!,
        name = name!!,
        kind = kind!!.literal,
        currency = currency!!,
        createdAt = createdAt!!,
    )
}
```

- [ ] **Step 1.5 — Create the service**

```kotlin
// server/src/main/kotlin/br/com/investlog/server/wallets/domain/services/WalletService.kt
package br.com.investlog.server.wallets.domain.services

import br.com.investlog.server.shared.exceptions.NotFoundException
import br.com.investlog.server.shared.security.CurrentUserProvider
import br.com.investlog.server.wallets.domain.repositories.WalletRepository
import br.com.investlog.server.wallets.rest.payloads.WalletKind
import br.com.investlog.server.wallets.rest.payloads.WalletResponse
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class WalletService(
    private val currentUserProvider: CurrentUserProvider,
    private val walletRepository: WalletRepository,
) {

    fun findAll(pageable: Pageable): PagedModel<WalletResponse> {
        val userId = currentUserProvider.getCurrentUser().id
        return walletRepository.findAll(userId, pageable)
    }

    fun create(name: String, kind: WalletKind, currency: String): WalletResponse {
        val userId = currentUserProvider.getCurrentUser().id
        return walletRepository.create(userId, name, kind, currency)
    }

    fun findById(externalId: UUID): WalletResponse {
        val userId = currentUserProvider.getCurrentUser().id
        return walletRepository.findByExternalId(userId, externalId)
            ?: throw NotFoundException("Wallet $externalId not found")
    }

    fun update(externalId: UUID, name: String): WalletResponse {
        val userId = currentUserProvider.getCurrentUser().id
        return walletRepository.update(userId, externalId, name)
            ?: throw NotFoundException("Wallet $externalId not found")
    }

    fun delete(externalId: UUID) {
        val userId = currentUserProvider.getCurrentUser().id
        if (walletRepository.deleteByExternalId(userId, externalId) == 0) {
            throw NotFoundException("Wallet $externalId not found")
        }
    }

    /** Used by child modules (stock/crypto/fund holdings) to resolve the internal wallet PK. */
    fun resolveId(externalId: UUID): Long {
        val userId = currentUserProvider.getCurrentUser().id
        return walletRepository.findInternalId(userId, externalId)
            ?: throw NotFoundException("Wallet $externalId not found")
    }
}
```

- [ ] **Step 1.6 — Create the controller**

```kotlin
// server/src/main/kotlin/br/com/investlog/server/wallets/rest/controllers/WalletController.kt
package br.com.investlog.server.wallets.rest.controllers

import br.com.investlog.server.wallets.domain.services.WalletService
import br.com.investlog.server.wallets.rest.payloads.WalletCreateRequest
import br.com.investlog.server.wallets.rest.payloads.WalletResponse
import br.com.investlog.server.wallets.rest.payloads.WalletUpdateRequest
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.http.HttpStatus
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Validated
@RestController
@RequestMapping("/wallets")
class WalletController(private val walletService: WalletService) {

    @GetMapping
    fun findAll(pageable: Pageable): PagedModel<WalletResponse> = walletService.findAll(pageable)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody request: WalletCreateRequest): WalletResponse =
        walletService.create(request.name, request.kind, request.currency)

    @GetMapping("/{id}")
    fun findById(@PathVariable id: UUID): WalletResponse = walletService.findById(id)

    @PatchMapping("/{id}")
    fun update(@PathVariable id: UUID, @Valid @RequestBody request: WalletUpdateRequest): WalletResponse =
        walletService.update(id, request.name)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: UUID) = walletService.delete(id)
}
```

- [ ] **Step 1.7 — Run the tests and confirm they pass**

```
./gradlew test --tests "br.com.investlog.server.wallets.rest.controllers.WalletControllerTest"
```

Expected: all 6 tests pass.

- [ ] **Step 1.8 — Commit**

```bash
git add server/src/main/kotlin/br/com/investlog/server/wallets \
        server/src/test/kotlin/br/com/investlog/server/wallets
git commit -m "feat: add wallets CRUD endpoints"
```

---

## Task 2 — Stock Holdings Module

**Endpoints** (all under `/private/v1/wallets/{walletId}/...`):
- `GET  /stock-holdings` — paginated list with embedded lots
- `POST /stock-holdings` — create holding **and** first lot atomically
- `PATCH /stock-holdings/{holdingId}` — update ticker / name / currentPrice / stockTypeId
- `DELETE /stock-holdings/{holdingId}` — delete holding (lots cascade)
- `POST /stock-holdings/{holdingId}/lots` — add a subsequent lot
- `DELETE /stock-holdings/{holdingId}/lots/{lotId}` — delete one lot

The `{walletId}` and `{holdingId}` path variables are public UUIDs (`external_id`).

- [ ] **Step 2.1 — Write the failing test**

```kotlin
// server/src/test/kotlin/br/com/investlog/server/stockholdings/rest/controllers/StockHoldingControllerTest.kt
package br.com.investlog.server.stockholdings.rest.controllers

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.stockholdings.rest.payloads.StockHoldingResponse
import br.com.investlog.server.wallets.rest.payloads.WalletResponse
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.returnResult
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StockHoldingControllerTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTestClient: RestTestClient

    lateinit var walletId: UUID
    lateinit var stockTypeId: UUID

    @BeforeAll
    fun setup() {
        walletId = restTestClient.post()
            .uri("/private/v1/wallets")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Stocks Wallet","kind":"stocks","currency":"BRL"}""")
            .exchange()
            .returnResult<WalletResponse>()
            .responseBody!!
            .id

        stockTypeId = restTestClient.post()
            .uri("/private/v1/stock-types")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Ação ON"}""")
            .exchange()
            .returnResult<br.com.investlog.server.typelists.rest.payloads.TypeResponse>()
            .responseBody!!
            .id
    }

    private fun createHolding(ticker: String = "PETR4"): StockHoldingResponse =
        restTestClient.post()
            .uri("/private/v1/wallets/$walletId/stock-holdings")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""
                {
                  "stockTypeId":"$stockTypeId",
                  "ticker":"$ticker",
                  "name":"Petrobras",
                  "currentPrice":38.50,
                  "lot":{"lotDate":"2024-01-15","quantity":100,"price":35.00}
                }
            """.trimIndent())
            .exchange()
            .expectStatus().isCreated()
            .returnResult<StockHoldingResponse>()
            .responseBody!!

    @Test
    @Order(1)
    fun `creates a stock holding with initial lot`() {
        val h = createHolding("PETR4")
        assertNotNull(h.id)
        assertEquals("PETR4", h.ticker)
        assertEquals(1, h.lots.size)
        assertEquals("2024-01-15", h.lots[0].lotDate.toString())
    }

    @Test
    @Order(2)
    fun `lists stock holdings for the wallet`() {
        restTestClient.get()
            .uri("/private/v1/wallets/$walletId/stock-holdings")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.page.totalElements").isEqualTo(1)
            .jsonPath("$.content[0].ticker").isEqualTo("PETR4")
            .jsonPath("$.content[0].lots").isArray()
    }

    @Test
    @Order(3)
    fun `adds a lot to an existing holding`() {
        val h = createHolding("VALE3")
        restTestClient.post()
            .uri("/private/v1/wallets/$walletId/stock-holdings/${h.id}/lots")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"lotDate":"2024-03-10","quantity":50,"price":70.00}""")
            .exchange()
            .expectStatus().isCreated()
            .expectBody()
            .jsonPath("$.lotDate").isEqualTo("2024-03-10")
            .jsonPath("$.quantity").isEqualTo(50)
    }

    @Test
    @Order(4)
    fun `updates ticker and current price`() {
        val h = createHolding("BBAS3")
        restTestClient.patch()
            .uri("/private/v1/wallets/$walletId/stock-holdings/${h.id}")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"ticker":"BBAS3","currentPrice":25.00}""")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.currentPrice").isEqualTo(25.00)
    }

    @Test
    @Order(5)
    fun `deletes a holding`() {
        val h = createHolding("ITUB4")
        restTestClient.delete()
            .uri("/private/v1/wallets/$walletId/stock-holdings/${h.id}")
            .exchange()
            .expectStatus().isNoContent()

        restTestClient.get()
            .uri("/private/v1/wallets/$walletId/stock-holdings")
            .exchange()
            .expectBody()
            .jsonPath("$.content[?(@.ticker == 'ITUB4')]").isEmpty()
    }

    @Test
    @Order(6)
    fun `deletes a lot`() {
        val h = createHolding("MGLU3")
        val lot = restTestClient.post()
            .uri("/private/v1/wallets/$walletId/stock-holdings/${h.id}/lots")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"lotDate":"2024-05-01","quantity":10,"price":12.00}""")
            .exchange()
            .returnResult<br.com.investlog.server.stockholdings.rest.payloads.LotResponse>()
            .responseBody!!

        restTestClient.delete()
            .uri("/private/v1/wallets/$walletId/stock-holdings/${h.id}/lots/${lot.id}")
            .exchange()
            .expectStatus().isNoContent()
    }

    @Test
    @Order(7)
    fun `returns 404 for unknown wallet`() {
        restTestClient.get()
            .uri("/private/v1/wallets/${UUID.randomUUID()}/stock-holdings")
            .exchange()
            .expectStatus().isNotFound()
    }
}
```

- [ ] **Step 2.2 — Run to confirm compilation failure**

```
./gradlew test --tests "br.com.investlog.server.stockholdings.rest.controllers.StockHoldingControllerTest"
```

Expected: compilation error.

- [ ] **Step 2.3 — Create payload classes**

```kotlin
// server/src/main/kotlin/br/com/investlog/server/stockholdings/rest/payloads/LotResponse.kt
package br.com.investlog.server.stockholdings.rest.payloads

import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class LotResponse(
    val id: UUID,
    val lotDate: LocalDate,
    val quantity: BigDecimal,
    val price: BigDecimal,
)
```

```kotlin
// server/src/main/kotlin/br/com/investlog/server/stockholdings/rest/payloads/LotCreateRequest.kt
package br.com.investlog.server.stockholdings.rest.payloads

import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero
import java.math.BigDecimal
import java.time.LocalDate

data class LotCreateRequest(
    @field:NotNull val lotDate: LocalDate,
    @field:NotNull @field:Positive val quantity: BigDecimal,
    @field:NotNull @field:PositiveOrZero val price: BigDecimal,
)
```

```kotlin
// server/src/main/kotlin/br/com/investlog/server/stockholdings/rest/payloads/StockHoldingResponse.kt
package br.com.investlog.server.stockholdings.rest.payloads

import java.math.BigDecimal
import java.util.UUID

data class StockHoldingResponse(
    val id: UUID,
    val walletId: UUID,
    val stockTypeId: UUID,
    val ticker: String,
    val name: String,
    val currentPrice: BigDecimal?,
    val lots: List<LotResponse>,
)
```

```kotlin
// server/src/main/kotlin/br/com/investlog/server/stockholdings/rest/payloads/StockHoldingCreateRequest.kt
package br.com.investlog.server.stockholdings.rest.payloads

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.PositiveOrZero
import java.math.BigDecimal
import java.util.UUID

data class StockHoldingCreateRequest(
    @field:NotNull val stockTypeId: UUID,
    @field:NotBlank val ticker: String,
    val name: String? = null,
    @field:PositiveOrZero val currentPrice: BigDecimal? = null,
    @field:Valid @field:NotNull val lot: LotCreateRequest,
)
```

```kotlin
// server/src/main/kotlin/br/com/investlog/server/stockholdings/rest/payloads/StockHoldingUpdateRequest.kt
package br.com.investlog.server.stockholdings.rest.payloads

import jakarta.validation.constraints.PositiveOrZero
import java.math.BigDecimal
import java.util.UUID

data class StockHoldingUpdateRequest(
    val stockTypeId: UUID? = null,
    val ticker: String? = null,
    val name: String? = null,
    @field:PositiveOrZero val currentPrice: BigDecimal? = null,
)
```

- [ ] **Step 2.4 — Create StockHoldingRepository**

The `findAll` query uses jOOQ `multiset` to embed lots in each holding response in a single SQL round-trip. The `stockTypeId` returned in the response is the `stock_types.external_id`.

```kotlin
// server/src/main/kotlin/br/com/investlog/server/stockholdings/domain/repositories/StockHoldingRepository.kt
package br.com.investlog.server.stockholdings.domain.repositories

import br.com.investlog.server.jooq.finances.tables.references.STOCK_HOLDINGS
import br.com.investlog.server.jooq.finances.tables.references.STOCK_LOTS
import br.com.investlog.server.jooq.finances.tables.references.STOCK_TYPES
import br.com.investlog.server.jooq.finances.tables.references.WALLETS
import br.com.investlog.server.shared.persistence.pagedModelOf
import br.com.investlog.server.stockholdings.rest.payloads.LotCreateRequest
import br.com.investlog.server.stockholdings.rest.payloads.LotResponse
import br.com.investlog.server.stockholdings.rest.payloads.StockHoldingResponse
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

@Repository
class StockHoldingRepository(private val dsl: DSLContext) {

    fun findAll(walletInternalId: Long, pageable: Pageable): PagedModel<StockHoldingResponse> {
        val wExtId = WALLETS.`as`("w")
        val st = STOCK_TYPES.`as`("st")
        val sh = STOCK_HOLDINGS.`as`("sh")

        val content = dsl.select(
            sh.EXTERNAL_ID,
            wExtId.EXTERNAL_ID,
            st.EXTERNAL_ID,
            sh.TICKER,
            sh.NAME,
            sh.CURRENT_PRICE,
            DSL.multiset(
                DSL.selectFrom(STOCK_LOTS)
                    .where(STOCK_LOTS.STOCK_HOLDING_ID.eq(sh.ID))
                    .orderBy(STOCK_LOTS.LOT_DATE)
            ).`as`("lots").convertFrom { r ->
                r.map { rec ->
                    LotResponse(
                        id = rec.get(STOCK_LOTS.EXTERNAL_ID)!!,
                        lotDate = rec.get(STOCK_LOTS.LOT_DATE)!!,
                        quantity = rec.get(STOCK_LOTS.QUANTITY)!!,
                        price = rec.get(STOCK_LOTS.PRICE)!!,
                    )
                }
            }
        )
        .from(sh)
        .join(wExtId).on(wExtId.ID.eq(sh.WALLET_ID))
        .join(st).on(st.ID.eq(sh.STOCK_TYPE_ID))
        .where(sh.WALLET_ID.eq(walletInternalId))
        .orderBy(sh.CREATED_AT.desc())
        .limit(pageable.pageSize)
        .offset(pageable.offset.toInt())
        .fetch { rec ->
            StockHoldingResponse(
                id = rec.get(sh.EXTERNAL_ID)!!,
                walletId = rec.get(wExtId.EXTERNAL_ID)!!,
                stockTypeId = rec.get(st.EXTERNAL_ID)!!,
                ticker = rec.get(sh.TICKER)!!,
                name = rec.get(sh.NAME)!!,
                currentPrice = rec.get(sh.CURRENT_PRICE),
                lots = rec.get(6, List::class.java) as List<LotResponse>,
            )
        }

        val total = dsl.fetchCount(
            dsl.selectFrom(STOCK_HOLDINGS).where(STOCK_HOLDINGS.WALLET_ID.eq(walletInternalId))
        )

        return pagedModelOf(content, pageable, total.toLong())
    }

    fun create(
        walletInternalId: Long,
        stockTypeInternalId: Long,
        ticker: String,
        name: String,
        currentPrice: BigDecimal?,
        lot: LotCreateRequest,
    ): StockHoldingResponse {
        val holding = dsl.insertInto(STOCK_HOLDINGS)
            .set(STOCK_HOLDINGS.WALLET_ID, walletInternalId)
            .set(STOCK_HOLDINGS.STOCK_TYPE_ID, stockTypeInternalId)
            .set(STOCK_HOLDINGS.TICKER, ticker.uppercase())
            .set(STOCK_HOLDINGS.NAME, name)
            .set(STOCK_HOLDINGS.CURRENT_PRICE, currentPrice)
            .returning()
            .fetchSingle()

        val lotRec = dsl.insertInto(STOCK_LOTS)
            .set(STOCK_LOTS.STOCK_HOLDING_ID, holding.id)
            .set(STOCK_LOTS.LOT_DATE, lot.lotDate)
            .set(STOCK_LOTS.QUANTITY, lot.quantity)
            .set(STOCK_LOTS.PRICE, lot.price)
            .returning()
            .fetchSingle()

        val walletExternalId = dsl.select(WALLETS.EXTERNAL_ID).from(WALLETS)
            .where(WALLETS.ID.eq(walletInternalId)).fetchSingle(WALLETS.EXTERNAL_ID)!!

        val stockTypeExternalId = dsl.select(STOCK_TYPES.EXTERNAL_ID).from(STOCK_TYPES)
            .where(STOCK_TYPES.ID.eq(stockTypeInternalId)).fetchSingle(STOCK_TYPES.EXTERNAL_ID)!!

        return StockHoldingResponse(
            id = holding.externalId!!,
            walletId = walletExternalId,
            stockTypeId = stockTypeExternalId,
            ticker = holding.ticker!!,
            name = holding.name!!,
            currentPrice = holding.currentPrice,
            lots = listOf(
                LotResponse(
                    id = lotRec.externalId!!,
                    lotDate = lotRec.lotDate!!,
                    quantity = lotRec.quantity!!,
                    price = lotRec.price!!,
                )
            ),
        )
    }

    fun findInternalId(walletInternalId: Long, externalId: UUID): Long? =
        dsl.select(STOCK_HOLDINGS.ID)
            .from(STOCK_HOLDINGS)
            .where(STOCK_HOLDINGS.WALLET_ID.eq(walletInternalId))
            .and(STOCK_HOLDINGS.EXTERNAL_ID.eq(externalId))
            .fetchOne(STOCK_HOLDINGS.ID)

    fun findStockTypeInternalId(externalId: UUID): Long? =
        dsl.select(STOCK_TYPES.ID).from(STOCK_TYPES)
            .where(STOCK_TYPES.EXTERNAL_ID.eq(externalId))
            .fetchOne(STOCK_TYPES.ID)

    fun update(
        walletInternalId: Long,
        externalId: UUID,
        stockTypeInternalId: Long?,
        ticker: String?,
        name: String?,
        currentPrice: BigDecimal?,
    ): StockHoldingResponse? {
        val update = dsl.update(STOCK_HOLDINGS)
            .set(STOCK_HOLDINGS.UPDATED_AT, OffsetDateTime.now())

        if (stockTypeInternalId != null) update.set(STOCK_HOLDINGS.STOCK_TYPE_ID, stockTypeInternalId)
        if (ticker != null) update.set(STOCK_HOLDINGS.TICKER, ticker.uppercase())
        if (name != null) update.set(STOCK_HOLDINGS.NAME, name)
        if (currentPrice != null) update.set(STOCK_HOLDINGS.CURRENT_PRICE, currentPrice)

        val updatedId = update
            .where(STOCK_HOLDINGS.WALLET_ID.eq(walletInternalId))
            .and(STOCK_HOLDINGS.EXTERNAL_ID.eq(externalId))
            .returning(STOCK_HOLDINGS.ID)
            .fetchOne(STOCK_HOLDINGS.ID) ?: return null

        // Re-fetch with lots using the same findAll pattern (single holding)
        return findAll(walletInternalId, org.springframework.data.domain.PageRequest.of(0, 1))
            .content.firstOrNull { it.id == externalId }
            ?: findByInternalId(updatedId, walletInternalId)
    }

    fun deleteByExternalId(walletInternalId: Long, externalId: UUID): Int =
        dsl.deleteFrom(STOCK_HOLDINGS)
            .where(STOCK_HOLDINGS.WALLET_ID.eq(walletInternalId))
            .and(STOCK_HOLDINGS.EXTERNAL_ID.eq(externalId))
            .execute()

    private fun findByInternalId(internalId: Long, walletInternalId: Long): StockHoldingResponse? {
        val wExtId = WALLETS.`as`("w")
        val st = STOCK_TYPES.`as`("st")
        val sh = STOCK_HOLDINGS.`as`("sh")

        return dsl.select(
            sh.EXTERNAL_ID,
            wExtId.EXTERNAL_ID,
            st.EXTERNAL_ID,
            sh.TICKER,
            sh.NAME,
            sh.CURRENT_PRICE,
            DSL.multiset(
                DSL.selectFrom(STOCK_LOTS)
                    .where(STOCK_LOTS.STOCK_HOLDING_ID.eq(sh.ID))
                    .orderBy(STOCK_LOTS.LOT_DATE)
            ).`as`("lots").convertFrom { r ->
                r.map { rec ->
                    LotResponse(
                        id = rec.get(STOCK_LOTS.EXTERNAL_ID)!!,
                        lotDate = rec.get(STOCK_LOTS.LOT_DATE)!!,
                        quantity = rec.get(STOCK_LOTS.QUANTITY)!!,
                        price = rec.get(STOCK_LOTS.PRICE)!!,
                    )
                }
            }
        )
        .from(sh)
        .join(wExtId).on(wExtId.ID.eq(sh.WALLET_ID))
        .join(st).on(st.ID.eq(sh.STOCK_TYPE_ID))
        .where(sh.ID.eq(internalId))
        .and(sh.WALLET_ID.eq(walletInternalId))
        .fetchOne { rec ->
            StockHoldingResponse(
                id = rec.get(sh.EXTERNAL_ID)!!,
                walletId = rec.get(wExtId.EXTERNAL_ID)!!,
                stockTypeId = rec.get(st.EXTERNAL_ID)!!,
                ticker = rec.get(sh.TICKER)!!,
                name = rec.get(sh.NAME)!!,
                currentPrice = rec.get(sh.CURRENT_PRICE),
                lots = rec.get(6, List::class.java) as List<LotResponse>,
            )
        }
    }
}
```

> **Note on `update`:** The jOOQ `UpdateSetMoreStep` cannot be built conditionally via a method chain in a type-safe way; the simplest correct approach is to build the update step and call `.set()` only when the field is non-null. Alternatively, refactor to always pass all fields in the request (making `null` mean "no change" and skipping unchanged fields). If the code above doesn't compile cleanly due to jOOQ's DSL typing, replace with the explicit approach below:
>
> ```kotlin
> // Alternative update implementation (always explicit)
> fun update(...): StockHoldingResponse? {
>     val holding = dsl.selectFrom(STOCK_HOLDINGS)
>         .where(STOCK_HOLDINGS.WALLET_ID.eq(walletInternalId))
>         .and(STOCK_HOLDINGS.EXTERNAL_ID.eq(externalId))
>         .fetchOne() ?: return null
>
>     dsl.update(STOCK_HOLDINGS)
>         .set(STOCK_HOLDINGS.STOCK_TYPE_ID, stockTypeInternalId ?: holding.stockTypeId!!)
>         .set(STOCK_HOLDINGS.TICKER, (ticker ?: holding.ticker!!).uppercase())
>         .set(STOCK_HOLDINGS.NAME, name ?: holding.name!!)
>         .set(STOCK_HOLDINGS.CURRENT_PRICE, currentPrice ?: holding.currentPrice)
>         .set(STOCK_HOLDINGS.UPDATED_AT, OffsetDateTime.now())
>         .where(STOCK_HOLDINGS.ID.eq(holding.id))
>         .execute()
>
>     return findByInternalId(holding.id!!, walletInternalId)
> }
> ```

- [ ] **Step 2.5 — Create StockLotRepository**

```kotlin
// server/src/main/kotlin/br/com/investlog/server/stockholdings/domain/repositories/StockLotRepository.kt
package br.com.investlog.server.stockholdings.domain.repositories

import br.com.investlog.server.jooq.finances.tables.references.STOCK_LOTS
import br.com.investlog.server.stockholdings.rest.payloads.LotResponse
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@Repository
class StockLotRepository(private val dsl: DSLContext) {

    fun addLot(holdingInternalId: Long, lotDate: LocalDate, quantity: BigDecimal, price: BigDecimal): LotResponse =
        dsl.insertInto(STOCK_LOTS)
            .set(STOCK_LOTS.STOCK_HOLDING_ID, holdingInternalId)
            .set(STOCK_LOTS.LOT_DATE, lotDate)
            .set(STOCK_LOTS.QUANTITY, quantity)
            .set(STOCK_LOTS.PRICE, price)
            .returning()
            .fetchSingle()
            .let { rec ->
                LotResponse(
                    id = rec.externalId!!,
                    lotDate = rec.lotDate!!,
                    quantity = rec.quantity!!,
                    price = rec.price!!,
                )
            }

    fun deleteByExternalId(holdingInternalId: Long, externalId: UUID): Int =
        dsl.deleteFrom(STOCK_LOTS)
            .where(STOCK_LOTS.STOCK_HOLDING_ID.eq(holdingInternalId))
            .and(STOCK_LOTS.EXTERNAL_ID.eq(externalId))
            .execute()
}
```

- [ ] **Step 2.6 — Create StockHoldingService**

```kotlin
// server/src/main/kotlin/br/com/investlog/server/stockholdings/domain/services/StockHoldingService.kt
package br.com.investlog.server.stockholdings.domain.services

import br.com.investlog.server.shared.exceptions.NotFoundException
import br.com.investlog.server.shared.security.CurrentUserProvider
import br.com.investlog.server.stockholdings.domain.repositories.StockHoldingRepository
import br.com.investlog.server.stockholdings.domain.repositories.StockLotRepository
import br.com.investlog.server.stockholdings.rest.payloads.LotCreateRequest
import br.com.investlog.server.stockholdings.rest.payloads.LotResponse
import br.com.investlog.server.stockholdings.rest.payloads.StockHoldingCreateRequest
import br.com.investlog.server.stockholdings.rest.payloads.StockHoldingResponse
import br.com.investlog.server.stockholdings.rest.payloads.StockHoldingUpdateRequest
import br.com.investlog.server.wallets.domain.services.WalletService
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class StockHoldingService(
    private val currentUserProvider: CurrentUserProvider,
    private val walletService: WalletService,
    private val stockHoldingRepository: StockHoldingRepository,
    private val stockLotRepository: StockLotRepository,
) {

    fun findAll(walletExternalId: UUID, pageable: Pageable): PagedModel<StockHoldingResponse> {
        val walletInternalId = walletService.resolveId(walletExternalId)
        return stockHoldingRepository.findAll(walletInternalId, pageable)
    }

    @Transactional
    fun create(walletExternalId: UUID, request: StockHoldingCreateRequest): StockHoldingResponse {
        val walletInternalId = walletService.resolveId(walletExternalId)
        val stockTypeInternalId = stockHoldingRepository.findStockTypeInternalId(request.stockTypeId)
            ?: throw NotFoundException("Stock type ${request.stockTypeId} not found")

        return stockHoldingRepository.create(
            walletInternalId = walletInternalId,
            stockTypeInternalId = stockTypeInternalId,
            ticker = request.ticker,
            name = request.name ?: request.ticker.uppercase(),
            currentPrice = request.currentPrice,
            lot = request.lot,
        )
    }

    @Transactional
    fun update(walletExternalId: UUID, holdingExternalId: UUID, request: StockHoldingUpdateRequest): StockHoldingResponse {
        val walletInternalId = walletService.resolveId(walletExternalId)
        val stockTypeInternalId = request.stockTypeId?.let {
            stockHoldingRepository.findStockTypeInternalId(it)
                ?: throw NotFoundException("Stock type $it not found")
        }

        return stockHoldingRepository.update(
            walletInternalId = walletInternalId,
            externalId = holdingExternalId,
            stockTypeInternalId = stockTypeInternalId,
            ticker = request.ticker,
            name = request.name,
            currentPrice = request.currentPrice,
        ) ?: throw NotFoundException("Stock holding $holdingExternalId not found")
    }

    fun delete(walletExternalId: UUID, holdingExternalId: UUID) {
        val walletInternalId = walletService.resolveId(walletExternalId)
        if (stockHoldingRepository.deleteByExternalId(walletInternalId, holdingExternalId) == 0) {
            throw NotFoundException("Stock holding $holdingExternalId not found")
        }
    }

    @Transactional
    fun addLot(walletExternalId: UUID, holdingExternalId: UUID, request: LotCreateRequest): LotResponse {
        val walletInternalId = walletService.resolveId(walletExternalId)
        val holdingInternalId = stockHoldingRepository.findInternalId(walletInternalId, holdingExternalId)
            ?: throw NotFoundException("Stock holding $holdingExternalId not found")

        return stockLotRepository.addLot(holdingInternalId, request.lotDate, request.quantity, request.price)
    }

    fun deleteLot(walletExternalId: UUID, holdingExternalId: UUID, lotExternalId: UUID) {
        val walletInternalId = walletService.resolveId(walletExternalId)
        val holdingInternalId = stockHoldingRepository.findInternalId(walletInternalId, holdingExternalId)
            ?: throw NotFoundException("Stock holding $holdingExternalId not found")

        if (stockLotRepository.deleteByExternalId(holdingInternalId, lotExternalId) == 0) {
            throw NotFoundException("Lot $lotExternalId not found")
        }
    }
}
```

- [ ] **Step 2.7 — Create StockHoldingController**

```kotlin
// server/src/main/kotlin/br/com/investlog/server/stockholdings/rest/controllers/StockHoldingController.kt
package br.com.investlog.server.stockholdings.rest.controllers

import br.com.investlog.server.stockholdings.domain.services.StockHoldingService
import br.com.investlog.server.stockholdings.rest.payloads.LotCreateRequest
import br.com.investlog.server.stockholdings.rest.payloads.LotResponse
import br.com.investlog.server.stockholdings.rest.payloads.StockHoldingCreateRequest
import br.com.investlog.server.stockholdings.rest.payloads.StockHoldingResponse
import br.com.investlog.server.stockholdings.rest.payloads.StockHoldingUpdateRequest
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.http.HttpStatus
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Validated
@RestController
@RequestMapping("/wallets/{walletId}/stock-holdings")
class StockHoldingController(private val stockHoldingService: StockHoldingService) {

    @GetMapping
    fun findAll(@PathVariable walletId: UUID, pageable: Pageable): PagedModel<StockHoldingResponse> =
        stockHoldingService.findAll(walletId, pageable)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@PathVariable walletId: UUID, @Valid @RequestBody request: StockHoldingCreateRequest): StockHoldingResponse =
        stockHoldingService.create(walletId, request)

    @PatchMapping("/{holdingId}")
    fun update(
        @PathVariable walletId: UUID,
        @PathVariable holdingId: UUID,
        @Valid @RequestBody request: StockHoldingUpdateRequest,
    ): StockHoldingResponse = stockHoldingService.update(walletId, holdingId, request)

    @DeleteMapping("/{holdingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable walletId: UUID, @PathVariable holdingId: UUID) =
        stockHoldingService.delete(walletId, holdingId)

    @PostMapping("/{holdingId}/lots")
    @ResponseStatus(HttpStatus.CREATED)
    fun addLot(
        @PathVariable walletId: UUID,
        @PathVariable holdingId: UUID,
        @Valid @RequestBody request: LotCreateRequest,
    ): LotResponse = stockHoldingService.addLot(walletId, holdingId, request)

    @DeleteMapping("/{holdingId}/lots/{lotId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteLot(
        @PathVariable walletId: UUID,
        @PathVariable holdingId: UUID,
        @PathVariable lotId: UUID,
    ) = stockHoldingService.deleteLot(walletId, holdingId, lotId)
}
```

- [ ] **Step 2.8 — Run the tests**

```
./gradlew test --tests "br.com.investlog.server.stockholdings.rest.controllers.StockHoldingControllerTest"
```

Expected: all 7 tests pass. Fix any jOOQ DSL compilation issues using the `update` alternative noted in Step 2.4.

- [ ] **Step 2.9 — Commit**

```bash
git add server/src/main/kotlin/br/com/investlog/server/stockholdings \
        server/src/test/kotlin/br/com/investlog/server/stockholdings
git commit -m "feat: add stock holdings and lots endpoints"
```

---

## Task 3 — Crypto Holdings Module

**Endpoints** (all under `/private/v1/wallets/{walletId}/...`):
- `GET  /crypto-holdings`
- `POST /crypto-holdings` — creates holding + first lot atomically
- `PATCH /crypto-holdings/{holdingId}`
- `DELETE /crypto-holdings/{holdingId}`
- `POST /crypto-holdings/{holdingId}/lots`
- `DELETE /crypto-holdings/{holdingId}/lots/{lotId}`

Crypto is structurally identical to stocks except there is **no** `stockTypeId` field.

- [ ] **Step 3.1 — Write the failing test**

```kotlin
// server/src/test/kotlin/br/com/investlog/server/cryptoholdings/rest/controllers/CryptoHoldingControllerTest.kt
package br.com.investlog.server.cryptoholdings.rest.controllers

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.cryptoholdings.rest.payloads.CryptoHoldingResponse
import br.com.investlog.server.wallets.rest.payloads.WalletResponse
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.returnResult
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CryptoHoldingControllerTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTestClient: RestTestClient

    lateinit var walletId: UUID

    @BeforeAll
    fun setup() {
        walletId = restTestClient.post()
            .uri("/private/v1/wallets")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Crypto Wallet","kind":"crypto","currency":"USD"}""")
            .exchange()
            .returnResult<WalletResponse>()
            .responseBody!!
            .id
    }

    private fun createHolding(ticker: String = "BTC"): CryptoHoldingResponse =
        restTestClient.post()
            .uri("/private/v1/wallets/$walletId/crypto-holdings")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""
                {
                  "ticker":"$ticker",
                  "name":"Bitcoin",
                  "currentPrice":95000.00,
                  "lot":{"lotDate":"2024-01-01","quantity":0.5,"price":42000.00}
                }
            """.trimIndent())
            .exchange()
            .expectStatus().isCreated()
            .returnResult<CryptoHoldingResponse>()
            .responseBody!!

    @Test
    @Order(1)
    fun `creates a crypto holding with initial lot`() {
        val h = createHolding("BTC")
        assertNotNull(h.id)
        assertEquals("BTC", h.ticker)
        assertEquals(1, h.lots.size)
    }

    @Test
    @Order(2)
    fun `lists crypto holdings`() {
        restTestClient.get()
            .uri("/private/v1/wallets/$walletId/crypto-holdings")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.page.totalElements").isEqualTo(1)
            .jsonPath("$.content[0].ticker").isEqualTo("BTC")
    }

    @Test
    @Order(3)
    fun `adds a lot`() {
        val h = createHolding("ETH")
        restTestClient.post()
            .uri("/private/v1/wallets/$walletId/crypto-holdings/${h.id}/lots")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"lotDate":"2024-06-01","quantity":2.0,"price":3500.00}""")
            .exchange()
            .expectStatus().isCreated()
            .expectBody()
            .jsonPath("$.quantity").isEqualTo(2.0)
    }

    @Test
    @Order(4)
    fun `updates current price`() {
        val h = createHolding("SOL")
        restTestClient.patch()
            .uri("/private/v1/wallets/$walletId/crypto-holdings/${h.id}")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"currentPrice":180.00}""")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.currentPrice").isEqualTo(180.00)
    }

    @Test
    @Order(5)
    fun `deletes a holding`() {
        val h = createHolding("ADA")
        restTestClient.delete()
            .uri("/private/v1/wallets/$walletId/crypto-holdings/${h.id}")
            .exchange()
            .expectStatus().isNoContent()
    }

    @Test
    @Order(6)
    fun `returns 404 for unknown wallet`() {
        restTestClient.get()
            .uri("/private/v1/wallets/${UUID.randomUUID()}/crypto-holdings")
            .exchange()
            .expectStatus().isNotFound()
    }
}
```

- [ ] **Step 3.2 — Run to confirm compilation failure**

```
./gradlew test --tests "br.com.investlog.server.cryptoholdings.rest.controllers.CryptoHoldingControllerTest"
```

- [ ] **Step 3.3 — Create payload classes**

Reuse `LotCreateRequest` and `LotResponse` from `stockholdings` (they are generic enough) — or duplicate them in the `cryptoholdings` package. **Duplicating is safer** since the two types may diverge:

```kotlin
// server/src/main/kotlin/br/com/investlog/server/cryptoholdings/rest/payloads/CryptoHoldingResponse.kt
package br.com.investlog.server.cryptoholdings.rest.payloads

import br.com.investlog.server.stockholdings.rest.payloads.LotResponse
import java.math.BigDecimal
import java.util.UUID

data class CryptoHoldingResponse(
    val id: UUID,
    val walletId: UUID,
    val ticker: String,
    val name: String,
    val currentPrice: BigDecimal?,
    val lots: List<LotResponse>,
)
```

```kotlin
// server/src/main/kotlin/br/com/investlog/server/cryptoholdings/rest/payloads/CryptoHoldingCreateRequest.kt
package br.com.investlog.server.cryptoholdings.rest.payloads

import br.com.investlog.server.stockholdings.rest.payloads.LotCreateRequest
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.PositiveOrZero
import java.math.BigDecimal

data class CryptoHoldingCreateRequest(
    @field:NotBlank val ticker: String,
    val name: String? = null,
    @field:PositiveOrZero val currentPrice: BigDecimal? = null,
    @field:Valid @field:NotNull val lot: LotCreateRequest,
)
```

```kotlin
// server/src/main/kotlin/br/com/investlog/server/cryptoholdings/rest/payloads/CryptoHoldingUpdateRequest.kt
package br.com.investlog.server.cryptoholdings.rest.payloads

import jakarta.validation.constraints.PositiveOrZero
import java.math.BigDecimal

data class CryptoHoldingUpdateRequest(
    val ticker: String? = null,
    val name: String? = null,
    @field:PositiveOrZero val currentPrice: BigDecimal? = null,
)
```

- [ ] **Step 3.4 — Create CryptoHoldingRepository**

Mirror of `StockHoldingRepository` but referencing `CRYPTO_HOLDINGS`, `CRYPTO_LOTS`, and without `STOCK_TYPES` join:

```kotlin
// server/src/main/kotlin/br/com/investlog/server/cryptoholdings/domain/repositories/CryptoHoldingRepository.kt
package br.com.investlog.server.cryptoholdings.domain.repositories

import br.com.investlog.server.cryptoholdings.rest.payloads.CryptoHoldingCreateRequest
import br.com.investlog.server.cryptoholdings.rest.payloads.CryptoHoldingResponse
import br.com.investlog.server.jooq.finances.tables.references.CRYPTO_HOLDINGS
import br.com.investlog.server.jooq.finances.tables.references.CRYPTO_LOTS
import br.com.investlog.server.jooq.finances.tables.references.WALLETS
import br.com.investlog.server.shared.persistence.pagedModelOf
import br.com.investlog.server.stockholdings.rest.payloads.LotResponse
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

@Repository
class CryptoHoldingRepository(private val dsl: DSLContext) {

    fun findAll(walletInternalId: Long, pageable: Pageable): PagedModel<CryptoHoldingResponse> {
        val w = WALLETS.`as`("w")
        val ch = CRYPTO_HOLDINGS.`as`("ch")

        val content = dsl.select(
            ch.EXTERNAL_ID,
            w.EXTERNAL_ID,
            ch.TICKER,
            ch.NAME,
            ch.CURRENT_PRICE,
            DSL.multiset(
                DSL.selectFrom(CRYPTO_LOTS)
                    .where(CRYPTO_LOTS.CRYPTO_HOLDING_ID.eq(ch.ID))
                    .orderBy(CRYPTO_LOTS.LOT_DATE)
            ).`as`("lots").convertFrom { r ->
                r.map { rec ->
                    LotResponse(
                        id = rec.get(CRYPTO_LOTS.EXTERNAL_ID)!!,
                        lotDate = rec.get(CRYPTO_LOTS.LOT_DATE)!!,
                        quantity = rec.get(CRYPTO_LOTS.QUANTITY)!!,
                        price = rec.get(CRYPTO_LOTS.PRICE)!!,
                    )
                }
            }
        )
        .from(ch)
        .join(w).on(w.ID.eq(ch.WALLET_ID))
        .where(ch.WALLET_ID.eq(walletInternalId))
        .orderBy(ch.CREATED_AT.desc())
        .limit(pageable.pageSize)
        .offset(pageable.offset.toInt())
        .fetch { rec ->
            CryptoHoldingResponse(
                id = rec.get(ch.EXTERNAL_ID)!!,
                walletId = rec.get(w.EXTERNAL_ID)!!,
                ticker = rec.get(ch.TICKER)!!,
                name = rec.get(ch.NAME)!!,
                currentPrice = rec.get(ch.CURRENT_PRICE),
                lots = rec.get(5, List::class.java) as List<LotResponse>,
            )
        }

        val total = dsl.fetchCount(
            dsl.selectFrom(CRYPTO_HOLDINGS).where(CRYPTO_HOLDINGS.WALLET_ID.eq(walletInternalId))
        )

        return pagedModelOf(content, pageable, total.toLong())
    }

    fun create(walletInternalId: Long, ticker: String, name: String, currentPrice: BigDecimal?, lot: br.com.investlog.server.stockholdings.rest.payloads.LotCreateRequest): CryptoHoldingResponse {
        val holding = dsl.insertInto(CRYPTO_HOLDINGS)
            .set(CRYPTO_HOLDINGS.WALLET_ID, walletInternalId)
            .set(CRYPTO_HOLDINGS.TICKER, ticker.uppercase())
            .set(CRYPTO_HOLDINGS.NAME, name)
            .set(CRYPTO_HOLDINGS.CURRENT_PRICE, currentPrice)
            .returning()
            .fetchSingle()

        val lotRec = dsl.insertInto(CRYPTO_LOTS)
            .set(CRYPTO_LOTS.CRYPTO_HOLDING_ID, holding.id)
            .set(CRYPTO_LOTS.LOT_DATE, lot.lotDate)
            .set(CRYPTO_LOTS.QUANTITY, lot.quantity)
            .set(CRYPTO_LOTS.PRICE, lot.price)
            .returning()
            .fetchSingle()

        val walletExternalId = dsl.select(WALLETS.EXTERNAL_ID).from(WALLETS)
            .where(WALLETS.ID.eq(walletInternalId)).fetchSingle(WALLETS.EXTERNAL_ID)!!

        return CryptoHoldingResponse(
            id = holding.externalId!!,
            walletId = walletExternalId,
            ticker = holding.ticker!!,
            name = holding.name!!,
            currentPrice = holding.currentPrice,
            lots = listOf(
                LotResponse(
                    id = lotRec.externalId!!,
                    lotDate = lotRec.lotDate!!,
                    quantity = lotRec.quantity!!,
                    price = lotRec.price!!,
                )
            ),
        )
    }

    fun findInternalId(walletInternalId: Long, externalId: UUID): Long? =
        dsl.select(CRYPTO_HOLDINGS.ID)
            .from(CRYPTO_HOLDINGS)
            .where(CRYPTO_HOLDINGS.WALLET_ID.eq(walletInternalId))
            .and(CRYPTO_HOLDINGS.EXTERNAL_ID.eq(externalId))
            .fetchOne(CRYPTO_HOLDINGS.ID)

    fun update(walletInternalId: Long, externalId: UUID, ticker: String?, name: String?, currentPrice: BigDecimal?): CryptoHoldingResponse? {
        val holding = dsl.selectFrom(CRYPTO_HOLDINGS)
            .where(CRYPTO_HOLDINGS.WALLET_ID.eq(walletInternalId))
            .and(CRYPTO_HOLDINGS.EXTERNAL_ID.eq(externalId))
            .fetchOne() ?: return null

        dsl.update(CRYPTO_HOLDINGS)
            .set(CRYPTO_HOLDINGS.TICKER, (ticker ?: holding.ticker!!).uppercase())
            .set(CRYPTO_HOLDINGS.NAME, name ?: holding.name!!)
            .set(CRYPTO_HOLDINGS.CURRENT_PRICE, currentPrice ?: holding.currentPrice)
            .set(CRYPTO_HOLDINGS.UPDATED_AT, OffsetDateTime.now())
            .where(CRYPTO_HOLDINGS.ID.eq(holding.id))
            .execute()

        return findAll(walletInternalId, org.springframework.data.domain.PageRequest.of(0, Int.MAX_VALUE))
            .content.firstOrNull { it.id == externalId }
    }

    fun deleteByExternalId(walletInternalId: Long, externalId: UUID): Int =
        dsl.deleteFrom(CRYPTO_HOLDINGS)
            .where(CRYPTO_HOLDINGS.WALLET_ID.eq(walletInternalId))
            .and(CRYPTO_HOLDINGS.EXTERNAL_ID.eq(externalId))
            .execute()
}
```

- [ ] **Step 3.5 — Create CryptoLotRepository**

```kotlin
// server/src/main/kotlin/br/com/investlog/server/cryptoholdings/domain/repositories/CryptoLotRepository.kt
package br.com.investlog.server.cryptoholdings.domain.repositories

import br.com.investlog.server.jooq.finances.tables.references.CRYPTO_LOTS
import br.com.investlog.server.stockholdings.rest.payloads.LotResponse
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@Repository
class CryptoLotRepository(private val dsl: DSLContext) {

    fun addLot(holdingInternalId: Long, lotDate: LocalDate, quantity: BigDecimal, price: BigDecimal): LotResponse =
        dsl.insertInto(CRYPTO_LOTS)
            .set(CRYPTO_LOTS.CRYPTO_HOLDING_ID, holdingInternalId)
            .set(CRYPTO_LOTS.LOT_DATE, lotDate)
            .set(CRYPTO_LOTS.QUANTITY, quantity)
            .set(CRYPTO_LOTS.PRICE, price)
            .returning()
            .fetchSingle()
            .let { rec ->
                LotResponse(
                    id = rec.externalId!!,
                    lotDate = rec.lotDate!!,
                    quantity = rec.quantity!!,
                    price = rec.price!!,
                )
            }

    fun deleteByExternalId(holdingInternalId: Long, externalId: UUID): Int =
        dsl.deleteFrom(CRYPTO_LOTS)
            .where(CRYPTO_LOTS.CRYPTO_HOLDING_ID.eq(holdingInternalId))
            .and(CRYPTO_LOTS.EXTERNAL_ID.eq(externalId))
            .execute()
}
```

- [ ] **Step 3.6 — Create CryptoHoldingService**

```kotlin
// server/src/main/kotlin/br/com/investlog/server/cryptoholdings/domain/services/CryptoHoldingService.kt
package br.com.investlog.server.cryptoholdings.domain.services

import br.com.investlog.server.cryptoholdings.domain.repositories.CryptoHoldingRepository
import br.com.investlog.server.cryptoholdings.domain.repositories.CryptoLotRepository
import br.com.investlog.server.cryptoholdings.rest.payloads.CryptoHoldingCreateRequest
import br.com.investlog.server.cryptoholdings.rest.payloads.CryptoHoldingResponse
import br.com.investlog.server.cryptoholdings.rest.payloads.CryptoHoldingUpdateRequest
import br.com.investlog.server.shared.exceptions.NotFoundException
import br.com.investlog.server.stockholdings.rest.payloads.LotCreateRequest
import br.com.investlog.server.stockholdings.rest.payloads.LotResponse
import br.com.investlog.server.wallets.domain.services.WalletService
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class CryptoHoldingService(
    private val walletService: WalletService,
    private val cryptoHoldingRepository: CryptoHoldingRepository,
    private val cryptoLotRepository: CryptoLotRepository,
) {

    fun findAll(walletExternalId: UUID, pageable: Pageable): PagedModel<CryptoHoldingResponse> {
        val walletInternalId = walletService.resolveId(walletExternalId)
        return cryptoHoldingRepository.findAll(walletInternalId, pageable)
    }

    @Transactional
    fun create(walletExternalId: UUID, request: CryptoHoldingCreateRequest): CryptoHoldingResponse {
        val walletInternalId = walletService.resolveId(walletExternalId)
        return cryptoHoldingRepository.create(
            walletInternalId = walletInternalId,
            ticker = request.ticker,
            name = request.name ?: request.ticker.uppercase(),
            currentPrice = request.currentPrice,
            lot = request.lot,
        )
    }

    @Transactional
    fun update(walletExternalId: UUID, holdingExternalId: UUID, request: CryptoHoldingUpdateRequest): CryptoHoldingResponse {
        val walletInternalId = walletService.resolveId(walletExternalId)
        return cryptoHoldingRepository.update(
            walletInternalId = walletInternalId,
            externalId = holdingExternalId,
            ticker = request.ticker,
            name = request.name,
            currentPrice = request.currentPrice,
        ) ?: throw NotFoundException("Crypto holding $holdingExternalId not found")
    }

    fun delete(walletExternalId: UUID, holdingExternalId: UUID) {
        val walletInternalId = walletService.resolveId(walletExternalId)
        if (cryptoHoldingRepository.deleteByExternalId(walletInternalId, holdingExternalId) == 0) {
            throw NotFoundException("Crypto holding $holdingExternalId not found")
        }
    }

    @Transactional
    fun addLot(walletExternalId: UUID, holdingExternalId: UUID, request: LotCreateRequest): LotResponse {
        val walletInternalId = walletService.resolveId(walletExternalId)
        val holdingInternalId = cryptoHoldingRepository.findInternalId(walletInternalId, holdingExternalId)
            ?: throw NotFoundException("Crypto holding $holdingExternalId not found")
        return cryptoLotRepository.addLot(holdingInternalId, request.lotDate, request.quantity, request.price)
    }

    fun deleteLot(walletExternalId: UUID, holdingExternalId: UUID, lotExternalId: UUID) {
        val walletInternalId = walletService.resolveId(walletExternalId)
        val holdingInternalId = cryptoHoldingRepository.findInternalId(walletInternalId, holdingExternalId)
            ?: throw NotFoundException("Crypto holding $holdingExternalId not found")
        if (cryptoLotRepository.deleteByExternalId(holdingInternalId, lotExternalId) == 0) {
            throw NotFoundException("Lot $lotExternalId not found")
        }
    }
}
```

- [ ] **Step 3.7 — Create CryptoHoldingController**

```kotlin
// server/src/main/kotlin/br/com/investlog/server/cryptoholdings/rest/controllers/CryptoHoldingController.kt
package br.com.investlog.server.cryptoholdings.rest.controllers

import br.com.investlog.server.cryptoholdings.domain.services.CryptoHoldingService
import br.com.investlog.server.cryptoholdings.rest.payloads.CryptoHoldingCreateRequest
import br.com.investlog.server.cryptoholdings.rest.payloads.CryptoHoldingResponse
import br.com.investlog.server.cryptoholdings.rest.payloads.CryptoHoldingUpdateRequest
import br.com.investlog.server.stockholdings.rest.payloads.LotCreateRequest
import br.com.investlog.server.stockholdings.rest.payloads.LotResponse
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.http.HttpStatus
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Validated
@RestController
@RequestMapping("/wallets/{walletId}/crypto-holdings")
class CryptoHoldingController(private val cryptoHoldingService: CryptoHoldingService) {

    @GetMapping
    fun findAll(@PathVariable walletId: UUID, pageable: Pageable): PagedModel<CryptoHoldingResponse> =
        cryptoHoldingService.findAll(walletId, pageable)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@PathVariable walletId: UUID, @Valid @RequestBody request: CryptoHoldingCreateRequest): CryptoHoldingResponse =
        cryptoHoldingService.create(walletId, request)

    @PatchMapping("/{holdingId}")
    fun update(
        @PathVariable walletId: UUID,
        @PathVariable holdingId: UUID,
        @Valid @RequestBody request: CryptoHoldingUpdateRequest,
    ): CryptoHoldingResponse = cryptoHoldingService.update(walletId, holdingId, request)

    @DeleteMapping("/{holdingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable walletId: UUID, @PathVariable holdingId: UUID) =
        cryptoHoldingService.delete(walletId, holdingId)

    @PostMapping("/{holdingId}/lots")
    @ResponseStatus(HttpStatus.CREATED)
    fun addLot(
        @PathVariable walletId: UUID,
        @PathVariable holdingId: UUID,
        @Valid @RequestBody request: LotCreateRequest,
    ): LotResponse = cryptoHoldingService.addLot(walletId, holdingId, request)

    @DeleteMapping("/{holdingId}/lots/{lotId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteLot(
        @PathVariable walletId: UUID,
        @PathVariable holdingId: UUID,
        @PathVariable lotId: UUID,
    ) = cryptoHoldingService.deleteLot(walletId, holdingId, lotId)
}
```

- [ ] **Step 3.8 — Run the tests**

```
./gradlew test --tests "br.com.investlog.server.cryptoholdings.rest.controllers.CryptoHoldingControllerTest"
```

Expected: all 6 tests pass.

- [ ] **Step 3.9 — Commit**

```bash
git add server/src/main/kotlin/br/com/investlog/server/cryptoholdings \
        server/src/test/kotlin/br/com/investlog/server/cryptoholdings
git commit -m "feat: add crypto holdings and lots endpoints"
```

---

## Task 4 — Fund Holdings Module

**Endpoints** (all under `/private/v1/wallets/{walletId}/...`):
- `GET  /fund-holdings`
- `POST /fund-holdings` — creates holding + first contribution atomically
- `PATCH /fund-holdings/{holdingId}` — update name / fundTypeId / currentValue
- `DELETE /fund-holdings/{holdingId}`
- `POST /fund-holdings/{holdingId}/contributions`
- `DELETE /fund-holdings/{holdingId}/contributions/{contributionId}`

Funds differ from stocks/crypto: instead of `lots` (qty × price), they track `contributions` (a dated `amount`). Instead of `current_price`, they have `current_value` (the current total value of the position, set manually).

- [ ] **Step 4.1 — Write the failing test**

```kotlin
// server/src/test/kotlin/br/com/investlog/server/fundholdings/rest/controllers/FundHoldingControllerTest.kt
package br.com.investlog.server.fundholdings.rest.controllers

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.fundholdings.rest.payloads.FundHoldingResponse
import br.com.investlog.server.wallets.rest.payloads.WalletResponse
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.returnResult
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FundHoldingControllerTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTestClient: RestTestClient

    lateinit var walletId: UUID
    lateinit var fundTypeId: UUID

    @BeforeAll
    fun setup() {
        walletId = restTestClient.post()
            .uri("/private/v1/wallets")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Funds Wallet","kind":"funds","currency":"BRL"}""")
            .exchange()
            .returnResult<WalletResponse>()
            .responseBody!!
            .id

        fundTypeId = restTestClient.post()
            .uri("/private/v1/fund-types")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Renda Fixa"}""")
            .exchange()
            .returnResult<br.com.investlog.server.typelists.rest.payloads.TypeResponse>()
            .responseBody!!
            .id
    }

    private fun createHolding(name: String = "Tesouro IPCA+"): FundHoldingResponse =
        restTestClient.post()
            .uri("/private/v1/wallets/$walletId/fund-holdings")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""
                {
                  "fundTypeId":"$fundTypeId",
                  "name":"$name",
                  "currentValue":5500.00,
                  "contribution":{"contributionDate":"2024-01-10","amount":5000.00}
                }
            """.trimIndent())
            .exchange()
            .expectStatus().isCreated()
            .returnResult<FundHoldingResponse>()
            .responseBody!!

    @Test
    @Order(1)
    fun `creates a fund holding with initial contribution`() {
        val h = createHolding()
        assertNotNull(h.id)
        assertEquals("Tesouro IPCA+", h.name)
        assertEquals(1, h.contributions.size)
        assertEquals("2024-01-10", h.contributions[0].contributionDate.toString())
    }

    @Test
    @Order(2)
    fun `lists fund holdings`() {
        restTestClient.get()
            .uri("/private/v1/wallets/$walletId/fund-holdings")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.page.totalElements").isEqualTo(1)
            .jsonPath("$.content[0].name").isEqualTo("Tesouro IPCA+")
            .jsonPath("$.content[0].contributions").isArray()
    }

    @Test
    @Order(3)
    fun `adds a contribution`() {
        val h = createHolding("CDB XP")
        restTestClient.post()
            .uri("/private/v1/wallets/$walletId/fund-holdings/${h.id}/contributions")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"contributionDate":"2024-04-01","amount":2000.00}""")
            .exchange()
            .expectStatus().isCreated()
            .expectBody()
            .jsonPath("$.amount").isEqualTo(2000.00)
    }

    @Test
    @Order(4)
    fun `updates current value`() {
        val h = createHolding("LCI Banco Inter")
        restTestClient.patch()
            .uri("/private/v1/wallets/$walletId/fund-holdings/${h.id}")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"currentValue":6200.00}""")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.currentValue").isEqualTo(6200.00)
    }

    @Test
    @Order(5)
    fun `deletes a holding`() {
        val h = createHolding("FII KNRI11")
        restTestClient.delete()
            .uri("/private/v1/wallets/$walletId/fund-holdings/${h.id}")
            .exchange()
            .expectStatus().isNoContent()
    }

    @Test
    @Order(6)
    fun `deletes a contribution`() {
        val h = createHolding("Debênture Petrobras")
        val contribution = restTestClient.post()
            .uri("/private/v1/wallets/$walletId/fund-holdings/${h.id}/contributions")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"contributionDate":"2024-07-01","amount":1000.00}""")
            .exchange()
            .returnResult<br.com.investlog.server.fundholdings.rest.payloads.ContributionResponse>()
            .responseBody!!

        restTestClient.delete()
            .uri("/private/v1/wallets/$walletId/fund-holdings/${h.id}/contributions/${contribution.id}")
            .exchange()
            .expectStatus().isNoContent()
    }

    @Test
    @Order(7)
    fun `returns 404 for unknown wallet`() {
        restTestClient.get()
            .uri("/private/v1/wallets/${UUID.randomUUID()}/fund-holdings")
            .exchange()
            .expectStatus().isNotFound()
    }
}
```

- [ ] **Step 4.2 — Run to confirm compilation failure**

```
./gradlew test --tests "br.com.investlog.server.fundholdings.rest.controllers.FundHoldingControllerTest"
```

- [ ] **Step 4.3 — Create payload classes**

```kotlin
// server/src/main/kotlin/br/com/investlog/server/fundholdings/rest/payloads/ContributionResponse.kt
package br.com.investlog.server.fundholdings.rest.payloads

import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class ContributionResponse(
    val id: UUID,
    val contributionDate: LocalDate,
    val amount: BigDecimal,
)
```

```kotlin
// server/src/main/kotlin/br/com/investlog/server/fundholdings/rest/payloads/ContributionCreateRequest.kt
package br.com.investlog.server.fundholdings.rest.payloads

import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import java.math.BigDecimal
import java.time.LocalDate

data class ContributionCreateRequest(
    @field:NotNull val contributionDate: LocalDate,
    @field:NotNull @field:Positive val amount: BigDecimal,
)
```

```kotlin
// server/src/main/kotlin/br/com/investlog/server/fundholdings/rest/payloads/FundHoldingResponse.kt
package br.com.investlog.server.fundholdings.rest.payloads

import java.math.BigDecimal
import java.util.UUID

data class FundHoldingResponse(
    val id: UUID,
    val walletId: UUID,
    val fundTypeId: UUID,
    val name: String,
    val currentValue: BigDecimal?,
    val contributions: List<ContributionResponse>,
)
```

```kotlin
// server/src/main/kotlin/br/com/investlog/server/fundholdings/rest/payloads/FundHoldingCreateRequest.kt
package br.com.investlog.server.fundholdings.rest.payloads

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.PositiveOrZero
import java.math.BigDecimal
import java.util.UUID

data class FundHoldingCreateRequest(
    @field:NotNull val fundTypeId: UUID,
    @field:NotBlank val name: String,
    @field:PositiveOrZero val currentValue: BigDecimal? = null,
    @field:Valid @field:NotNull val contribution: ContributionCreateRequest,
)
```

```kotlin
// server/src/main/kotlin/br/com/investlog/server/fundholdings/rest/payloads/FundHoldingUpdateRequest.kt
package br.com.investlog.server.fundholdings.rest.payloads

import jakarta.validation.constraints.PositiveOrZero
import java.math.BigDecimal
import java.util.UUID

data class FundHoldingUpdateRequest(
    val fundTypeId: UUID? = null,
    val name: String? = null,
    @field:PositiveOrZero val currentValue: BigDecimal? = null,
)
```

- [ ] **Step 4.4 — Create FundHoldingRepository**

```kotlin
// server/src/main/kotlin/br/com/investlog/server/fundholdings/domain/repositories/FundHoldingRepository.kt
package br.com.investlog.server.fundholdings.domain.repositories

import br.com.investlog.server.fundholdings.rest.payloads.ContributionCreateRequest
import br.com.investlog.server.fundholdings.rest.payloads.ContributionResponse
import br.com.investlog.server.fundholdings.rest.payloads.FundHoldingResponse
import br.com.investlog.server.jooq.finances.tables.references.FUND_CONTRIBUTIONS
import br.com.investlog.server.jooq.finances.tables.references.FUND_HOLDINGS
import br.com.investlog.server.jooq.finances.tables.references.FUND_TYPES
import br.com.investlog.server.jooq.finances.tables.references.WALLETS
import br.com.investlog.server.shared.persistence.pagedModelOf
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

@Repository
class FundHoldingRepository(private val dsl: DSLContext) {

    fun findAll(walletInternalId: Long, pageable: Pageable): PagedModel<FundHoldingResponse> {
        val w = WALLETS.`as`("w")
        val ft = FUND_TYPES.`as`("ft")
        val fh = FUND_HOLDINGS.`as`("fh")

        val content = dsl.select(
            fh.EXTERNAL_ID,
            w.EXTERNAL_ID,
            ft.EXTERNAL_ID,
            fh.NAME,
            fh.CURRENT_VALUE,
            DSL.multiset(
                DSL.selectFrom(FUND_CONTRIBUTIONS)
                    .where(FUND_CONTRIBUTIONS.FUND_HOLDING_ID.eq(fh.ID))
                    .orderBy(FUND_CONTRIBUTIONS.CONTRIBUTION_DATE)
            ).`as`("contributions").convertFrom { r ->
                r.map { rec ->
                    ContributionResponse(
                        id = rec.get(FUND_CONTRIBUTIONS.EXTERNAL_ID)!!,
                        contributionDate = rec.get(FUND_CONTRIBUTIONS.CONTRIBUTION_DATE)!!,
                        amount = rec.get(FUND_CONTRIBUTIONS.AMOUNT)!!,
                    )
                }
            }
        )
        .from(fh)
        .join(w).on(w.ID.eq(fh.WALLET_ID))
        .join(ft).on(ft.ID.eq(fh.FUND_TYPE_ID))
        .where(fh.WALLET_ID.eq(walletInternalId))
        .orderBy(fh.CREATED_AT.desc())
        .limit(pageable.pageSize)
        .offset(pageable.offset.toInt())
        .fetch { rec ->
            FundHoldingResponse(
                id = rec.get(fh.EXTERNAL_ID)!!,
                walletId = rec.get(w.EXTERNAL_ID)!!,
                fundTypeId = rec.get(ft.EXTERNAL_ID)!!,
                name = rec.get(fh.NAME)!!,
                currentValue = rec.get(fh.CURRENT_VALUE),
                contributions = rec.get(5, List::class.java) as List<ContributionResponse>,
            )
        }

        val total = dsl.fetchCount(
            dsl.selectFrom(FUND_HOLDINGS).where(FUND_HOLDINGS.WALLET_ID.eq(walletInternalId))
        )

        return pagedModelOf(content, pageable, total.toLong())
    }

    fun create(walletInternalId: Long, fundTypeInternalId: Long, name: String, currentValue: BigDecimal?, contribution: ContributionCreateRequest): FundHoldingResponse {
        val holding = dsl.insertInto(FUND_HOLDINGS)
            .set(FUND_HOLDINGS.WALLET_ID, walletInternalId)
            .set(FUND_HOLDINGS.FUND_TYPE_ID, fundTypeInternalId)
            .set(FUND_HOLDINGS.NAME, name)
            .set(FUND_HOLDINGS.CURRENT_VALUE, currentValue)
            .returning()
            .fetchSingle()

        val contribRec = dsl.insertInto(FUND_CONTRIBUTIONS)
            .set(FUND_CONTRIBUTIONS.FUND_HOLDING_ID, holding.id)
            .set(FUND_CONTRIBUTIONS.CONTRIBUTION_DATE, contribution.contributionDate)
            .set(FUND_CONTRIBUTIONS.AMOUNT, contribution.amount)
            .returning()
            .fetchSingle()

        val walletExternalId = dsl.select(WALLETS.EXTERNAL_ID).from(WALLETS)
            .where(WALLETS.ID.eq(walletInternalId)).fetchSingle(WALLETS.EXTERNAL_ID)!!

        val fundTypeExternalId = dsl.select(FUND_TYPES.EXTERNAL_ID).from(FUND_TYPES)
            .where(FUND_TYPES.ID.eq(fundTypeInternalId)).fetchSingle(FUND_TYPES.EXTERNAL_ID)!!

        return FundHoldingResponse(
            id = holding.externalId!!,
            walletId = walletExternalId,
            fundTypeId = fundTypeExternalId,
            name = holding.name!!,
            currentValue = holding.currentValue,
            contributions = listOf(
                ContributionResponse(
                    id = contribRec.externalId!!,
                    contributionDate = contribRec.contributionDate!!,
                    amount = contribRec.amount!!,
                )
            ),
        )
    }

    fun findFundTypeInternalId(externalId: UUID): Long? =
        dsl.select(FUND_TYPES.ID).from(FUND_TYPES)
            .where(FUND_TYPES.EXTERNAL_ID.eq(externalId))
            .fetchOne(FUND_TYPES.ID)

    fun findInternalId(walletInternalId: Long, externalId: UUID): Long? =
        dsl.select(FUND_HOLDINGS.ID)
            .from(FUND_HOLDINGS)
            .where(FUND_HOLDINGS.WALLET_ID.eq(walletInternalId))
            .and(FUND_HOLDINGS.EXTERNAL_ID.eq(externalId))
            .fetchOne(FUND_HOLDINGS.ID)

    fun update(walletInternalId: Long, externalId: UUID, fundTypeInternalId: Long?, name: String?, currentValue: BigDecimal?): FundHoldingResponse? {
        val holding = dsl.selectFrom(FUND_HOLDINGS)
            .where(FUND_HOLDINGS.WALLET_ID.eq(walletInternalId))
            .and(FUND_HOLDINGS.EXTERNAL_ID.eq(externalId))
            .fetchOne() ?: return null

        dsl.update(FUND_HOLDINGS)
            .set(FUND_HOLDINGS.FUND_TYPE_ID, fundTypeInternalId ?: holding.fundTypeId!!)
            .set(FUND_HOLDINGS.NAME, name ?: holding.name!!)
            .set(FUND_HOLDINGS.CURRENT_VALUE, currentValue ?: holding.currentValue)
            .set(FUND_HOLDINGS.UPDATED_AT, OffsetDateTime.now())
            .where(FUND_HOLDINGS.ID.eq(holding.id))
            .execute()

        return findAll(walletInternalId, org.springframework.data.domain.PageRequest.of(0, Int.MAX_VALUE))
            .content.firstOrNull { it.id == externalId }
    }

    fun deleteByExternalId(walletInternalId: Long, externalId: UUID): Int =
        dsl.deleteFrom(FUND_HOLDINGS)
            .where(FUND_HOLDINGS.WALLET_ID.eq(walletInternalId))
            .and(FUND_HOLDINGS.EXTERNAL_ID.eq(externalId))
            .execute()
}
```

- [ ] **Step 4.5 — Create FundContributionRepository**

```kotlin
// server/src/main/kotlin/br/com/investlog/server/fundholdings/domain/repositories/FundContributionRepository.kt
package br.com.investlog.server.fundholdings.domain.repositories

import br.com.investlog.server.fundholdings.rest.payloads.ContributionResponse
import br.com.investlog.server.jooq.finances.tables.references.FUND_CONTRIBUTIONS
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@Repository
class FundContributionRepository(private val dsl: DSLContext) {

    fun addContribution(holdingInternalId: Long, contributionDate: LocalDate, amount: BigDecimal): ContributionResponse =
        dsl.insertInto(FUND_CONTRIBUTIONS)
            .set(FUND_CONTRIBUTIONS.FUND_HOLDING_ID, holdingInternalId)
            .set(FUND_CONTRIBUTIONS.CONTRIBUTION_DATE, contributionDate)
            .set(FUND_CONTRIBUTIONS.AMOUNT, amount)
            .returning()
            .fetchSingle()
            .let { rec ->
                ContributionResponse(
                    id = rec.externalId!!,
                    contributionDate = rec.contributionDate!!,
                    amount = rec.amount!!,
                )
            }

    fun deleteByExternalId(holdingInternalId: Long, externalId: UUID): Int =
        dsl.deleteFrom(FUND_CONTRIBUTIONS)
            .where(FUND_CONTRIBUTIONS.FUND_HOLDING_ID.eq(holdingInternalId))
            .and(FUND_CONTRIBUTIONS.EXTERNAL_ID.eq(externalId))
            .execute()
}
```

- [ ] **Step 4.6 — Create FundHoldingService**

```kotlin
// server/src/main/kotlin/br/com/investlog/server/fundholdings/domain/services/FundHoldingService.kt
package br.com.investlog.server.fundholdings.domain.services

import br.com.investlog.server.fundholdings.domain.repositories.FundContributionRepository
import br.com.investlog.server.fundholdings.domain.repositories.FundHoldingRepository
import br.com.investlog.server.fundholdings.rest.payloads.ContributionCreateRequest
import br.com.investlog.server.fundholdings.rest.payloads.ContributionResponse
import br.com.investlog.server.fundholdings.rest.payloads.FundHoldingCreateRequest
import br.com.investlog.server.fundholdings.rest.payloads.FundHoldingResponse
import br.com.investlog.server.fundholdings.rest.payloads.FundHoldingUpdateRequest
import br.com.investlog.server.shared.exceptions.NotFoundException
import br.com.investlog.server.wallets.domain.services.WalletService
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class FundHoldingService(
    private val walletService: WalletService,
    private val fundHoldingRepository: FundHoldingRepository,
    private val fundContributionRepository: FundContributionRepository,
) {

    fun findAll(walletExternalId: UUID, pageable: Pageable): PagedModel<FundHoldingResponse> {
        val walletInternalId = walletService.resolveId(walletExternalId)
        return fundHoldingRepository.findAll(walletInternalId, pageable)
    }

    @Transactional
    fun create(walletExternalId: UUID, request: FundHoldingCreateRequest): FundHoldingResponse {
        val walletInternalId = walletService.resolveId(walletExternalId)
        val fundTypeInternalId = fundHoldingRepository.findFundTypeInternalId(request.fundTypeId)
            ?: throw NotFoundException("Fund type ${request.fundTypeId} not found")

        return fundHoldingRepository.create(
            walletInternalId = walletInternalId,
            fundTypeInternalId = fundTypeInternalId,
            name = request.name,
            currentValue = request.currentValue,
            contribution = request.contribution,
        )
    }

    @Transactional
    fun update(walletExternalId: UUID, holdingExternalId: UUID, request: FundHoldingUpdateRequest): FundHoldingResponse {
        val walletInternalId = walletService.resolveId(walletExternalId)
        val fundTypeInternalId = request.fundTypeId?.let {
            fundHoldingRepository.findFundTypeInternalId(it)
                ?: throw NotFoundException("Fund type $it not found")
        }

        return fundHoldingRepository.update(
            walletInternalId = walletInternalId,
            externalId = holdingExternalId,
            fundTypeInternalId = fundTypeInternalId,
            name = request.name,
            currentValue = request.currentValue,
        ) ?: throw NotFoundException("Fund holding $holdingExternalId not found")
    }

    fun delete(walletExternalId: UUID, holdingExternalId: UUID) {
        val walletInternalId = walletService.resolveId(walletExternalId)
        if (fundHoldingRepository.deleteByExternalId(walletInternalId, holdingExternalId) == 0) {
            throw NotFoundException("Fund holding $holdingExternalId not found")
        }
    }

    @Transactional
    fun addContribution(walletExternalId: UUID, holdingExternalId: UUID, request: ContributionCreateRequest): ContributionResponse {
        val walletInternalId = walletService.resolveId(walletExternalId)
        val holdingInternalId = fundHoldingRepository.findInternalId(walletInternalId, holdingExternalId)
            ?: throw NotFoundException("Fund holding $holdingExternalId not found")
        return fundContributionRepository.addContribution(holdingInternalId, request.contributionDate, request.amount)
    }

    fun deleteContribution(walletExternalId: UUID, holdingExternalId: UUID, contributionExternalId: UUID) {
        val walletInternalId = walletService.resolveId(walletExternalId)
        val holdingInternalId = fundHoldingRepository.findInternalId(walletInternalId, holdingExternalId)
            ?: throw NotFoundException("Fund holding $holdingExternalId not found")
        if (fundContributionRepository.deleteByExternalId(holdingInternalId, contributionExternalId) == 0) {
            throw NotFoundException("Contribution $contributionExternalId not found")
        }
    }
}
```

- [ ] **Step 4.7 — Create FundHoldingController**

```kotlin
// server/src/main/kotlin/br/com/investlog/server/fundholdings/rest/controllers/FundHoldingController.kt
package br.com.investlog.server.fundholdings.rest.controllers

import br.com.investlog.server.fundholdings.domain.services.FundHoldingService
import br.com.investlog.server.fundholdings.rest.payloads.ContributionCreateRequest
import br.com.investlog.server.fundholdings.rest.payloads.ContributionResponse
import br.com.investlog.server.fundholdings.rest.payloads.FundHoldingCreateRequest
import br.com.investlog.server.fundholdings.rest.payloads.FundHoldingResponse
import br.com.investlog.server.fundholdings.rest.payloads.FundHoldingUpdateRequest
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.http.HttpStatus
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Validated
@RestController
@RequestMapping("/wallets/{walletId}/fund-holdings")
class FundHoldingController(private val fundHoldingService: FundHoldingService) {

    @GetMapping
    fun findAll(@PathVariable walletId: UUID, pageable: Pageable): PagedModel<FundHoldingResponse> =
        fundHoldingService.findAll(walletId, pageable)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@PathVariable walletId: UUID, @Valid @RequestBody request: FundHoldingCreateRequest): FundHoldingResponse =
        fundHoldingService.create(walletId, request)

    @PatchMapping("/{holdingId}")
    fun update(
        @PathVariable walletId: UUID,
        @PathVariable holdingId: UUID,
        @Valid @RequestBody request: FundHoldingUpdateRequest,
    ): FundHoldingResponse = fundHoldingService.update(walletId, holdingId, request)

    @DeleteMapping("/{holdingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable walletId: UUID, @PathVariable holdingId: UUID) =
        fundHoldingService.delete(walletId, holdingId)

    @PostMapping("/{holdingId}/contributions")
    @ResponseStatus(HttpStatus.CREATED)
    fun addContribution(
        @PathVariable walletId: UUID,
        @PathVariable holdingId: UUID,
        @Valid @RequestBody request: ContributionCreateRequest,
    ): ContributionResponse = fundHoldingService.addContribution(walletId, holdingId, request)

    @DeleteMapping("/{holdingId}/contributions/{contributionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteContribution(
        @PathVariable walletId: UUID,
        @PathVariable holdingId: UUID,
        @PathVariable contributionId: UUID,
    ) = fundHoldingService.deleteContribution(walletId, holdingId, contributionId)
}
```

- [ ] **Step 4.8 — Run the tests**

```
./gradlew test --tests "br.com.investlog.server.fundholdings.rest.controllers.FundHoldingControllerTest"
```

Expected: all 7 tests pass.

- [ ] **Step 4.9 — Commit**

```bash
git add server/src/main/kotlin/br/com/investlog/server/fundholdings \
        server/src/test/kotlin/br/com/investlog/server/fundholdings
git commit -m "feat: add fund holdings and contributions endpoints"
```

---

## Task 5 — Full Test Suite Verification

- [ ] **Step 5.1 — Run all tests**

```
./gradlew test
```

Expected: all tests pass. Any failures here are likely due to cross-test-class state if tests share the Testcontainer database — check `@Order` annotations and whether a test class is creating data that leaks into another class's assertions.

- [ ] **Step 5.2 — Final commit if any fixes were needed**

```bash
git add -p
git commit -m "fix: resolve cross-test isolation after adding new modules"
```

---

## Known Subtleties

### multiset index access
The `rec.get(6, List::class.java)` pattern (index-based field access for the multiset column) works but is fragile. If you add more SELECT columns, update the index. Alternatively, use the `Field` reference directly:
```kotlin
val lotsField = DSL.multiset(...).`as`("lots").convertFrom { ... }
// then in fetch: rec.get(lotsField)
```

### update returns `null` for "current_price cleared to null"
The `update` methods use `currentPrice ?: holding.currentPrice` which means a client can never _clear_ `currentPrice` to `null` via a PATCH. This is acceptable for the current scope — if clearing is needed later, use a dedicated endpoint or pass a sentinel value.

### `WalletKind` naming
The local `WalletKind` enum (in `wallets/rest/payloads`) is different from the jOOQ-generated one. The import alias `import br.com.investlog.server.jooq.finances.enums.WalletKind as JooqWalletKind` in `WalletRepository` prevents the clash.
