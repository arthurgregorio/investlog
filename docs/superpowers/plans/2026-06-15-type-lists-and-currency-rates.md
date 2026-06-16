# Type Lists & Currency Rates Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement `stock-types`, `fund-types`, and `currency-rates` CRUD endpoints under `/private/v1`, plus the cross-cutting pagination (`PagedModel<T>`) and error-handling (404/409) infrastructure every later collection endpoint will reuse.

**Architecture:** Add `spring-data-commons` (for `Pageable`/`Page`/`PagedModel`) and a tiny `shared/persistence` helper that wraps a jOOQ result page into a `PagedModel<T>`. Extend `GlobalExceptionHandler` with two new mappings: a new `NotFoundException` → 404, and Spring's `DataIntegrityViolationException` (thrown by jOOQ on unique/FK violations, translated automatically by `spring-boot-starter-jooq`) → 409. Two new modules follow the `rest/{controllers,dtos}` + `domain/{repositories,services}` layout established by `profile`: `typelists` (stock types + fund types, sharing one pair of DTOs since both are `{id, name}`) and `currencyrates` (upsert-by-`currencyCode`, with the base-currency-switch transaction described in the spec).

This is **Plan 2 of 4** covering `docs/superpowers/specs/2026-06-14-server-rest-api-design.md`. No schema changes are needed — `finances.stock_types`, `finances.fund_types`, and `finances.currency_rates` already exist (`06/12-1030-create-finances-core.xml`), and their jOOQ types were already generated as part of Plan 1.

**Tech Stack:** Kotlin 2.3.21, Spring Boot 4.1.0 / Spring Framework 7 (WebMVC), `spring-data-commons` (`Pageable`/`PagedModel`), jOOQ 3.21 (KotlinGenerator), PostgreSQL 17, JUnit 5 + `kotlin-test-junit5`, Testcontainers, `RestTestClient`.

---

## File Structure

```
server/build.gradle.kts                                                          [MODIFY]
server/src/main/resources/application.yaml                                       [MODIFY]

server/src/main/kotlin/br/com/investlog/server/
  shared/
    persistence/
      Paging.kt                                                                  [CREATE]
    exceptions/
      NotFoundException.kt                                                       [CREATE]
  config/
    GlobalExceptionHandler.kt                                                    [MODIFY]
  typelists/
    rest/dtos/
      TypeResponse.kt                                                            [CREATE]
      TypeCreateRequest.kt                                                       [CREATE]
    domain/repositories/
      StockTypeRepository.kt                                                     [CREATE]
      FundTypeRepository.kt                                                      [CREATE]
    domain/services/
      StockTypeService.kt                                                        [CREATE]
      FundTypeService.kt                                                         [CREATE]
    rest/controllers/
      StockTypeController.kt                                                     [CREATE]
      FundTypeController.kt                                                      [CREATE]
  currencyrates/
    rest/dtos/
      CurrencyRateResponse.kt                                                    [CREATE]
      CurrencyRateUpsertRequest.kt                                               [CREATE]
    domain/repositories/
      CurrencyRateRepository.kt                                                  [CREATE]
    domain/services/
      CurrencyRateService.kt                                                     [CREATE]
    rest/controllers/
      CurrencyRateController.kt                                                  [CREATE]

server/src/test/kotlin/br/com/investlog/server/
  shared/persistence/
    PagingTest.kt                                                                [CREATE]
  config/
    GlobalExceptionHandlerTest.kt                                                [CREATE]
  typelists/rest/controllers/
    StockTypeControllerTest.kt                                                   [CREATE]
    FundTypeControllerTest.kt                                                    [CREATE]
  currencyrates/rest/controllers/
    CurrencyRateControllerTest.kt                                                [CREATE]

server/CLAUDE.md                                                                 [MODIFY]
```

- **`shared/persistence`**: `pagedModelOf(content, pageable, total)` — the one place jOOQ page results become `org.springframework.data.web.PagedModel<T>`. Every paginated repository in this and future plans calls it.
- **`shared/exceptions`**: `NotFoundException`, thrown by services when an `external_id` doesn't resolve to a row for the current user.
- **`config/GlobalExceptionHandler`**: gains `@ExceptionHandler(NotFoundException::class)` → 404 and `@ExceptionHandler(DataIntegrityViolationException::class)` → 409 (covers both unique-constraint violations on `POST` and FK-restrict violations on `DELETE`, per the spec's error-handling table).
- **`typelists`**: `stock-types` and `fund-types` are structurally identical (`{id, name}`, `UNIQUE(user_id, name)`, referenced `ON DELETE RESTRICT` by holdings) but live in different tables — two small repositories/services/controllers share one pair of DTOs (`TypeResponse`/`TypeCreateRequest`).
- **`currencyrates`**: single resource, addressed by `currencyCode` (not `external_id`, per spec). `PUT /{currencyCode}` upserts; when `isBase: true`, the repository clears the previous base row in the same transaction (the partial unique index `uq_currency_rates_user_base` only allows one `is_base = true` row per user).

---

## Task 1: Pagination foundation — `spring-data-commons` + `pagedModelOf`

**Files:**
- Modify: `server/build.gradle.kts`
- Modify: `server/src/main/resources/application.yaml`
- Create: `server/src/main/kotlin/br/com/investlog/server/shared/persistence/Paging.kt`
- Test: `server/src/test/kotlin/br/com/investlog/server/shared/persistence/PagingTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package br.com.investlog.server.shared.persistence

import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import org.springframework.data.domain.PageRequest

class PagingTest {

    private val objectMapper = ObjectMapper()

    @Test
    fun `wraps content and pageable into a PagedModel`() {
        val model = pagedModelOf(listOf("a", "b"), PageRequest.of(0, 20), 2L)
        val json = objectMapper.valueToTree<JsonNode>(model)

        assertEquals(listOf("a", "b"), model.content.toList())
        assertEquals(0, json["page"]["number"].asInt())
        assertEquals(20, json["page"]["size"].asInt())
        assertEquals(2, json["page"]["totalElements"].asInt())
        assertEquals(1, json["page"]["totalPages"].asInt())
    }
}
```

> **Note:** `PagedModel.PageMetadata` is a Java record — its accessors (`size()`, `number()`,
> etc.) are NOT exposed as Kotlin properties (`.size`, `.number`), so don't access them that way.
> This test instead serializes the model with `ObjectMapper` and asserts on the resulting JSON,
> which is also exactly what the controller tests' `$.page.*` jsonPath assertions rely on.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "br.com.investlog.server.shared.persistence.PagingTest"`
Expected: `BUILD FAILED` — compilation error, `unresolved reference: pagedModelOf`.

- [ ] **Step 3: Add the `spring-data-commons` dependency**

In `server/build.gradle.kts`, add to the `dependencies` block, in the "spring stuff" group (after `spring-boot-starter-webmvc`):

```kotlin
	implementation("org.springframework.data:spring-data-commons")
```

- [ ] **Step 4: Configure the default page size**

In `server/src/main/resources/application.yaml`, add a `data.web.pageable` block under `spring:` (the spec requires `Default size=20`, but `PageableHandlerMethodArgumentResolver`'s built-in default is 10):

```yaml
spring:
  application:
    name: server

  data:
    web:
      pageable:
        default-page-size: 20

  liquibase:
    change-log: classpath:db/changelog/db.changelog-master.xml
    database-change-log-table: database_changelog
    database-change-log-lock-table: database_changelog_lock
```

- [ ] **Step 5: Implement `pagedModelOf`**

```kotlin
package br.com.investlog.server.shared.persistence

import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel

fun <T> pagedModelOf(content: List<T>, pageable: Pageable, total: Long): PagedModel<T> =
    PagedModel(PageImpl(content, pageable, total))
```

- [ ] **Step 6: Run test to verify it passes**

Run: `./gradlew test --tests "br.com.investlog.server.shared.persistence.PagingTest"`
Expected: `BUILD SUCCESSFUL`, 1/1 tests passing.

- [ ] **Step 7: Commit**

```bash
git add server/build.gradle.kts server/src/main/resources/application.yaml server/src/main/kotlin/br/com/investlog/server/shared/persistence/Paging.kt server/src/test/kotlin/br/com/investlog/server/shared/persistence/PagingTest.kt
git commit -m "Add spring-data-commons and a pagedModelOf helper for PagedModel responses"
```

---

## Task 2: `NotFoundException` (404) and `DataIntegrityViolationException` (409) handling

**Files:**
- Create: `server/src/main/kotlin/br/com/investlog/server/shared/exceptions/NotFoundException.kt`
- Modify: `server/src/main/kotlin/br/com/investlog/server/config/GlobalExceptionHandler.kt`
- Test: `server/src/test/kotlin/br/com/investlog/server/config/GlobalExceptionHandlerTest.kt`

The current `GlobalExceptionHandler.kt` is:

```kotlin
package br.com.investlog.server.config

import java.time.Instant
import org.slf4j.LoggerFactory
import org.springframework.context.support.DefaultMessageSourceResolvable
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

@RestControllerAdvice
class GlobalExceptionHandler : ResponseEntityExceptionHandler() {

    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    override fun handleMethodArgumentNotValid(
        ex: MethodArgumentNotValidException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val errors = ex.allErrors.map(DefaultMessageSourceResolvable::getDefaultMessage)

        val problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.message ?: "Validation failed")
        problemDetail.title = "Validation Error"
        problemDetail.setProperty("errors", errors)
        problemDetail.setProperty("timestamp", Instant.now())

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problemDetail)
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception): ProblemDetail {
        log.error("Unexpected exception occurred", ex)

        val problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred")
        problemDetail.setProperty("timestamp", Instant.now())

        return problemDetail
    }
}
```

- [ ] **Step 1: Write the failing test**

```kotlin
package br.com.investlog.server.config

import br.com.investlog.server.shared.exceptions.NotFoundException
import kotlin.test.Test
import kotlin.test.assertEquals
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus

class GlobalExceptionHandlerTest {

    private val handler = GlobalExceptionHandler()

    @Test
    fun `maps NotFoundException to a 404 ProblemDetail`() {
        val problemDetail = handler.handleNotFound(NotFoundException("Stock type abc not found"))

        assertEquals(HttpStatus.NOT_FOUND.value(), problemDetail.status)
        assertEquals("Stock type abc not found", problemDetail.detail)
    }

    @Test
    fun `maps DataIntegrityViolationException to a 409 ProblemDetail`() {
        val problemDetail = handler.handleDataIntegrityViolation(DataIntegrityViolationException("duplicate key value"))

        assertEquals(HttpStatus.CONFLICT.value(), problemDetail.status)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "br.com.investlog.server.config.GlobalExceptionHandlerTest"`
Expected: `BUILD FAILED` — compilation errors, `unresolved reference: NotFoundException`, `handleNotFound`, `handleDataIntegrityViolation`.

- [ ] **Step 3: Create `NotFoundException`**

```kotlin
package br.com.investlog.server.shared.exceptions

class NotFoundException(message: String) : RuntimeException(message)
```

- [ ] **Step 4: Add the two new handlers to `GlobalExceptionHandler`**

Add these imports (alongside the existing ones, keeping alphabetical order):

```kotlin
import br.com.investlog.server.shared.exceptions.NotFoundException
import org.springframework.dao.DataIntegrityViolationException
```

Add these two methods to the class, before `handleUnexpected`:

```kotlin
    @ExceptionHandler(NotFoundException::class)
    fun handleNotFound(ex: NotFoundException): ProblemDetail {
        val problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.message ?: "Resource not found")
        problemDetail.setProperty("timestamp", Instant.now())

        return problemDetail
    }

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrityViolation(ex: DataIntegrityViolationException): ProblemDetail {
        val problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "The request conflicts with existing data")
        problemDetail.setProperty("timestamp", Instant.now())

        return problemDetail
    }
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew test --tests "br.com.investlog.server.config.GlobalExceptionHandlerTest"`
Expected: `BUILD SUCCESSFUL`, 2/2 tests passing.

- [ ] **Step 6: Commit**

```bash
git add server/src/main/kotlin/br/com/investlog/server/shared/exceptions/NotFoundException.kt server/src/main/kotlin/br/com/investlog/server/config/GlobalExceptionHandler.kt server/src/test/kotlin/br/com/investlog/server/config/GlobalExceptionHandlerTest.kt
git commit -m "Map NotFoundException to 404 and DataIntegrityViolationException to 409"
```

---

## Task 3: Stock types CRUD (`GET`/`POST`/`DELETE /stock-types`)

**Files:**
- Create: `server/src/main/kotlin/br/com/investlog/server/typelists/rest/dtos/TypeResponse.kt`
- Create: `server/src/main/kotlin/br/com/investlog/server/typelists/rest/dtos/TypeCreateRequest.kt`
- Create: `server/src/main/kotlin/br/com/investlog/server/typelists/domain/repositories/StockTypeRepository.kt`
- Create: `server/src/main/kotlin/br/com/investlog/server/typelists/domain/services/StockTypeService.kt`
- Create: `server/src/main/kotlin/br/com/investlog/server/typelists/rest/controllers/StockTypeController.kt`
- Test: `server/src/test/kotlin/br/com/investlog/server/typelists/rest/controllers/StockTypeControllerTest.kt`

`finances.stock_types` (from `06/12-1030-create-finances-core.xml`) is:

```sql
CREATE TABLE finances.stock_types (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    external_id UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    user_id BIGINT NOT NULL REFERENCES system.users(id),
    name TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, name)
);
```

Its jOOQ types (already generated by Plan 1's `generateJooq`) are
`br.com.investlog.server.jooq.finances.tables.references.STOCK_TYPES` and
`br.com.investlog.server.jooq.finances.tables.records.StockTypesRecord`.

- [ ] **Step 1: Write the failing test**

```kotlin
package br.com.investlog.server.typelists.rest.controllers

import br.com.investlog.server.TestcontainersConfiguration
import br.com.investlog.server.typelists.rest.dtos.TypeResponse
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.RestTestClient

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration::class)
@AutoConfigureRestTestClient
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class StockTypeControllerTest {

    @Autowired
    lateinit var restTestClient: RestTestClient

    companion object {
        private var createdId: UUID? = null
    }

    @Test
    @Order(1)
    fun `returns an empty page initially`() {
        restTestClient.get()
            .uri("/private/v1/stock-types")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.page.totalElements").isEqualTo(0)
            .jsonPath("$.page.size").isEqualTo(20)
            .jsonPath("$.content").isArray()
    }

    @Test
    @Order(2)
    fun `creates a stock type`() {
        val response = restTestClient.post()
            .uri("/private/v1/stock-types")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Acoes Brasil"}""")
            .exchange()
            .expectStatus().isCreated()
            .returnResult(TypeResponse::class.java)
            .responseBody

        assertEquals("Acoes Brasil", response?.name)
        createdId = response?.id
    }

    @Test
    @Order(3)
    fun `lists the created stock type`() {
        restTestClient.get()
            .uri("/private/v1/stock-types")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.page.totalElements").isEqualTo(1)
            .jsonPath("$.content[0].name").isEqualTo("Acoes Brasil")
    }

    @Test
    @Order(4)
    fun `rejects a duplicate name with 409`() {
        restTestClient.post()
            .uri("/private/v1/stock-types")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Acoes Brasil"}""")
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.CONFLICT)
    }

    @Test
    @Order(5)
    fun `rejects a blank name with 400`() {
        restTestClient.post()
            .uri("/private/v1/stock-types")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":""}""")
            .exchange()
            .expectStatus().isBadRequest()
    }

    @Test
    @Order(6)
    fun `deletes the created stock type`() {
        restTestClient.delete()
            .uri("/private/v1/stock-types/${createdId}")
            .exchange()
            .expectStatus().isNoContent()
    }

    @Test
    @Order(7)
    fun `returns 404 when deleting an unknown id`() {
        restTestClient.delete()
            .uri("/private/v1/stock-types/${UUID.randomUUID()}")
            .exchange()
            .expectStatus().isNotFound()
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "br.com.investlog.server.typelists.rest.controllers.StockTypeControllerTest"`
Expected: `BUILD FAILED` — compilation errors, `unresolved reference: TypeResponse` (and the controller doesn't exist, so every request 404s).

- [ ] **Step 3: Create the shared DTOs**

```kotlin
package br.com.investlog.server.typelists.rest.dtos

import java.util.UUID

data class TypeResponse(val id: UUID, val name: String)
```

```kotlin
package br.com.investlog.server.typelists.rest.dtos

import jakarta.validation.constraints.NotBlank

data class TypeCreateRequest(
    @field:NotBlank(message = "name must not be blank")
    val name: String,
)
```

- [ ] **Step 4: Create `StockTypeRepository`**

```kotlin
package br.com.investlog.server.typelists.domain.repositories

import br.com.investlog.server.jooq.finances.tables.records.StockTypesRecord
import br.com.investlog.server.jooq.finances.tables.references.STOCK_TYPES
import br.com.investlog.server.shared.persistence.pagedModelOf
import br.com.investlog.server.typelists.rest.dtos.TypeResponse
import java.util.UUID
import org.jooq.DSLContext
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.stereotype.Repository

@Repository
class StockTypeRepository(private val dsl: DSLContext) {

    fun findAll(userId: Long, pageable: Pageable): PagedModel<TypeResponse> {
        val content = dsl.selectFrom(STOCK_TYPES)
            .where(STOCK_TYPES.USER_ID.eq(userId))
            .orderBy(STOCK_TYPES.NAME)
            .limit(pageable.pageSize)
            .offset(pageable.offset.toInt())
            .fetch()
            .map { it.toResponse() }

        val total = dsl.fetchCount(
            dsl.selectFrom(STOCK_TYPES).where(STOCK_TYPES.USER_ID.eq(userId))
        )

        return pagedModelOf(content, pageable, total.toLong())
    }

    fun create(userId: Long, name: String): TypeResponse =
        dsl.insertInto(STOCK_TYPES)
            .set(STOCK_TYPES.USER_ID, userId)
            .set(STOCK_TYPES.NAME, name)
            .returning()
            .fetchSingle()
            .toResponse()

    fun deleteByExternalId(userId: Long, externalId: UUID): Int =
        dsl.deleteFrom(STOCK_TYPES)
            .where(STOCK_TYPES.USER_ID.eq(userId))
            .and(STOCK_TYPES.EXTERNAL_ID.eq(externalId))
            .execute()

    private fun StockTypesRecord.toResponse() = TypeResponse(
        id = externalId!!,
        name = name!!,
    )
}
```

- [ ] **Step 5: Create `StockTypeService`**

```kotlin
package br.com.investlog.server.typelists.domain.services

import br.com.investlog.server.shared.exceptions.NotFoundException
import br.com.investlog.server.shared.security.CurrentUserProvider
import br.com.investlog.server.typelists.domain.repositories.StockTypeRepository
import br.com.investlog.server.typelists.rest.dtos.TypeResponse
import java.util.UUID
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.stereotype.Service

@Service
class StockTypeService(
    private val currentUserProvider: CurrentUserProvider,
    private val stockTypeRepository: StockTypeRepository,
) {

    fun findAll(pageable: Pageable): PagedModel<TypeResponse> {
        val userId = currentUserProvider.getCurrentUser().id

        return stockTypeRepository.findAll(userId, pageable)
    }

    fun create(name: String): TypeResponse {
        val userId = currentUserProvider.getCurrentUser().id

        return stockTypeRepository.create(userId, name)
    }

    fun delete(externalId: UUID) {
        val userId = currentUserProvider.getCurrentUser().id

        if (stockTypeRepository.deleteByExternalId(userId, externalId) == 0) {
            throw NotFoundException("Stock type $externalId not found")
        }
    }
}
```

- [ ] **Step 6: Create `StockTypeController`**

```kotlin
package br.com.investlog.server.typelists.rest.controllers

import br.com.investlog.server.typelists.domain.services.StockTypeService
import br.com.investlog.server.typelists.rest.dtos.TypeCreateRequest
import br.com.investlog.server.typelists.rest.dtos.TypeResponse
import jakarta.validation.Valid
import java.util.UUID
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/stock-types")
class StockTypeController(private val stockTypeService: StockTypeService) {

    @GetMapping
    fun findAll(pageable: Pageable): PagedModel<TypeResponse> =
        stockTypeService.findAll(pageable)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody request: TypeCreateRequest): TypeResponse =
        stockTypeService.create(request.name)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: UUID) {
        stockTypeService.delete(id)
    }
}
```

- [ ] **Step 7: Run test to verify it passes**

Run: `./gradlew test --tests "br.com.investlog.server.typelists.rest.controllers.StockTypeControllerTest"`
Expected: `BUILD SUCCESSFUL`, 7/7 tests passing.

- [ ] **Step 8: Commit**

```bash
git add server/src/main/kotlin/br/com/investlog/server/typelists server/src/test/kotlin/br/com/investlog/server/typelists/rest/controllers/StockTypeControllerTest.kt
git commit -m "Add stock-types CRUD endpoints"
```

---

## Task 4: Fund types CRUD (`GET`/`POST`/`DELETE /fund-types`)

**Files:**
- Create: `server/src/main/kotlin/br/com/investlog/server/typelists/domain/repositories/FundTypeRepository.kt`
- Create: `server/src/main/kotlin/br/com/investlog/server/typelists/domain/services/FundTypeService.kt`
- Create: `server/src/main/kotlin/br/com/investlog/server/typelists/rest/controllers/FundTypeController.kt`
- Test: `server/src/test/kotlin/br/com/investlog/server/typelists/rest/controllers/FundTypeControllerTest.kt`

`finances.fund_types` has the identical shape to `finances.stock_types` (same columns, same `UNIQUE(user_id, name)`). Its jOOQ types are
`br.com.investlog.server.jooq.finances.tables.references.FUND_TYPES` and
`br.com.investlog.server.jooq.finances.tables.records.FundTypesRecord`.
This task mirrors Task 3 exactly, targeting `/fund-types` and reusing the `TypeResponse`/`TypeCreateRequest` DTOs created in Task 3.

- [ ] **Step 1: Write the failing test**

```kotlin
package br.com.investlog.server.typelists.rest.controllers

import br.com.investlog.server.TestcontainersConfiguration
import br.com.investlog.server.typelists.rest.dtos.TypeResponse
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.RestTestClient

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration::class)
@AutoConfigureRestTestClient
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class FundTypeControllerTest {

    @Autowired
    lateinit var restTestClient: RestTestClient

    companion object {
        private var createdId: UUID? = null
    }

    @Test
    @Order(1)
    fun `returns an empty page initially`() {
        restTestClient.get()
            .uri("/private/v1/fund-types")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.page.totalElements").isEqualTo(0)
            .jsonPath("$.content").isArray()
    }

    @Test
    @Order(2)
    fun `creates a fund type`() {
        val response = restTestClient.post()
            .uri("/private/v1/fund-types")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Renda Fixa"}""")
            .exchange()
            .expectStatus().isCreated()
            .returnResult(TypeResponse::class.java)
            .responseBody

        assertEquals("Renda Fixa", response?.name)
        createdId = response?.id
    }

    @Test
    @Order(3)
    fun `lists the created fund type`() {
        restTestClient.get()
            .uri("/private/v1/fund-types")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.page.totalElements").isEqualTo(1)
            .jsonPath("$.content[0].name").isEqualTo("Renda Fixa")
    }

    @Test
    @Order(4)
    fun `rejects a duplicate name with 409`() {
        restTestClient.post()
            .uri("/private/v1/fund-types")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Renda Fixa"}""")
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.CONFLICT)
    }

    @Test
    @Order(5)
    fun `deletes the created fund type`() {
        restTestClient.delete()
            .uri("/private/v1/fund-types/${createdId}")
            .exchange()
            .expectStatus().isNoContent()
    }

    @Test
    @Order(6)
    fun `returns 404 when deleting an unknown id`() {
        restTestClient.delete()
            .uri("/private/v1/fund-types/${UUID.randomUUID()}")
            .exchange()
            .expectStatus().isNotFound()
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "br.com.investlog.server.typelists.rest.controllers.FundTypeControllerTest"`
Expected: `BUILD FAILED` — every request 404s (no `/fund-types` mapping exists yet).

- [ ] **Step 3: Create `FundTypeRepository`**

```kotlin
package br.com.investlog.server.typelists.domain.repositories

import br.com.investlog.server.jooq.finances.tables.records.FundTypesRecord
import br.com.investlog.server.jooq.finances.tables.references.FUND_TYPES
import br.com.investlog.server.shared.persistence.pagedModelOf
import br.com.investlog.server.typelists.rest.dtos.TypeResponse
import java.util.UUID
import org.jooq.DSLContext
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.stereotype.Repository

@Repository
class FundTypeRepository(private val dsl: DSLContext) {

    fun findAll(userId: Long, pageable: Pageable): PagedModel<TypeResponse> {
        val content = dsl.selectFrom(FUND_TYPES)
            .where(FUND_TYPES.USER_ID.eq(userId))
            .orderBy(FUND_TYPES.NAME)
            .limit(pageable.pageSize)
            .offset(pageable.offset.toInt())
            .fetch()
            .map { it.toResponse() }

        val total = dsl.fetchCount(
            dsl.selectFrom(FUND_TYPES).where(FUND_TYPES.USER_ID.eq(userId))
        )

        return pagedModelOf(content, pageable, total.toLong())
    }

    fun create(userId: Long, name: String): TypeResponse =
        dsl.insertInto(FUND_TYPES)
            .set(FUND_TYPES.USER_ID, userId)
            .set(FUND_TYPES.NAME, name)
            .returning()
            .fetchSingle()
            .toResponse()

    fun deleteByExternalId(userId: Long, externalId: UUID): Int =
        dsl.deleteFrom(FUND_TYPES)
            .where(FUND_TYPES.USER_ID.eq(userId))
            .and(FUND_TYPES.EXTERNAL_ID.eq(externalId))
            .execute()

    private fun FundTypesRecord.toResponse() = TypeResponse(
        id = externalId!!,
        name = name!!,
    )
}
```

- [ ] **Step 4: Create `FundTypeService`**

```kotlin
package br.com.investlog.server.typelists.domain.services

import br.com.investlog.server.shared.exceptions.NotFoundException
import br.com.investlog.server.shared.security.CurrentUserProvider
import br.com.investlog.server.typelists.domain.repositories.FundTypeRepository
import br.com.investlog.server.typelists.rest.dtos.TypeResponse
import java.util.UUID
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.stereotype.Service

@Service
class FundTypeService(
    private val currentUserProvider: CurrentUserProvider,
    private val fundTypeRepository: FundTypeRepository,
) {

    fun findAll(pageable: Pageable): PagedModel<TypeResponse> {
        val userId = currentUserProvider.getCurrentUser().id

        return fundTypeRepository.findAll(userId, pageable)
    }

    fun create(name: String): TypeResponse {
        val userId = currentUserProvider.getCurrentUser().id

        return fundTypeRepository.create(userId, name)
    }

    fun delete(externalId: UUID) {
        val userId = currentUserProvider.getCurrentUser().id

        if (fundTypeRepository.deleteByExternalId(userId, externalId) == 0) {
            throw NotFoundException("Fund type $externalId not found")
        }
    }
}
```

- [ ] **Step 5: Create `FundTypeController`**

```kotlin
package br.com.investlog.server.typelists.rest.controllers

import br.com.investlog.server.typelists.domain.services.FundTypeService
import br.com.investlog.server.typelists.rest.dtos.TypeCreateRequest
import br.com.investlog.server.typelists.rest.dtos.TypeResponse
import jakarta.validation.Valid
import java.util.UUID
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/fund-types")
class FundTypeController(private val fundTypeService: FundTypeService) {

    @GetMapping
    fun findAll(pageable: Pageable): PagedModel<TypeResponse> =
        fundTypeService.findAll(pageable)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody request: TypeCreateRequest): TypeResponse =
        fundTypeService.create(request.name)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: UUID) {
        fundTypeService.delete(id)
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `./gradlew test --tests "br.com.investlog.server.typelists.rest.controllers.FundTypeControllerTest"`
Expected: `BUILD SUCCESSFUL`, 6/6 tests passing.

- [ ] **Step 7: Commit**

```bash
git add server/src/main/kotlin/br/com/investlog/server/typelists server/src/test/kotlin/br/com/investlog/server/typelists/rest/controllers/FundTypeControllerTest.kt
git commit -m "Add fund-types CRUD endpoints"
```

---

## Task 5: Currency rates (`GET`/`PUT /currency-rates`)

**Files:**
- Create: `server/src/main/kotlin/br/com/investlog/server/currencyrates/rest/dtos/CurrencyRateResponse.kt`
- Create: `server/src/main/kotlin/br/com/investlog/server/currencyrates/rest/dtos/CurrencyRateUpsertRequest.kt`
- Create: `server/src/main/kotlin/br/com/investlog/server/currencyrates/domain/repositories/CurrencyRateRepository.kt`
- Create: `server/src/main/kotlin/br/com/investlog/server/currencyrates/domain/services/CurrencyRateService.kt`
- Create: `server/src/main/kotlin/br/com/investlog/server/currencyrates/rest/controllers/CurrencyRateController.kt`
- Test: `server/src/test/kotlin/br/com/investlog/server/currencyrates/rest/controllers/CurrencyRateControllerTest.kt`

`finances.currency_rates` (from `06/12-1030-create-finances-core.xml`) is:

```sql
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

CREATE UNIQUE INDEX uq_currency_rates_user_base ON finances.currency_rates (user_id) WHERE is_base;
```

Its jOOQ types are `br.com.investlog.server.jooq.finances.tables.references.CURRENCY_RATES` and
`br.com.investlog.server.jooq.finances.tables.records.CurrencyRatesRecord`.

`14-1050-seed-dev-data.xml` seeds the dev user with three rows: `BRL` (rate `1`, `is_base=true`), `USD` (rate `5.42`), `EUR` (rate `5.88`) — ordering by `currency_code` ascending gives `BRL, EUR, USD`.

- [ ] **Step 1: Write the failing test**

```kotlin
package br.com.investlog.server.currencyrates.rest.controllers

import br.com.investlog.server.TestcontainersConfiguration
import br.com.investlog.server.currencyrates.rest.dtos.CurrencyRateResponse
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.RestTestClient

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration::class)
@AutoConfigureRestTestClient
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class CurrencyRateControllerTest {

    @Autowired
    lateinit var restTestClient: RestTestClient

    @Test
    @Order(1)
    fun `returns the seeded currency rates ordered by currency code`() {
        restTestClient.get()
            .uri("/private/v1/currency-rates")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.page.totalElements").isEqualTo(3)
            .jsonPath("$.content[0].currencyCode").isEqualTo("BRL")
            .jsonPath("$.content[0].isBase").isEqualTo(true)
            .jsonPath("$.content[1].currencyCode").isEqualTo("EUR")
            .jsonPath("$.content[2].currencyCode").isEqualTo("USD")
            .jsonPath("$.content[2].isBase").isEqualTo(false)
    }

    @Test
    @Order(2)
    fun `updates an existing rate without changing the base currency`() {
        val response = restTestClient.put()
            .uri("/private/v1/currency-rates/USD")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"rate":5.50}""")
            .exchange()
            .expectStatus().isOk()
            .returnResult(CurrencyRateResponse::class.java)
            .responseBody

        assertEquals(0, response?.rate?.compareTo(BigDecimal("5.50")))
        assertEquals(false, response?.isBase)
    }

    @Test
    @Order(3)
    fun `switches the base currency in the same transaction`() {
        val response = restTestClient.put()
            .uri("/private/v1/currency-rates/USD")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"rate":5.60,"isBase":true}""")
            .exchange()
            .expectStatus().isOk()
            .returnResult(CurrencyRateResponse::class.java)
            .responseBody

        assertEquals(true, response?.isBase)

        restTestClient.get()
            .uri("/private/v1/currency-rates")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content[0].currencyCode").isEqualTo("BRL")
            .jsonPath("$.content[0].isBase").isEqualTo(false)
            .jsonPath("$.content[2].currencyCode").isEqualTo("USD")
            .jsonPath("$.content[2].isBase").isEqualTo(true)
    }

    @Test
    @Order(4)
    fun `creates a new currency rate`() {
        val response = restTestClient.put()
            .uri("/private/v1/currency-rates/GBP")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"rate":7.0}""")
            .exchange()
            .expectStatus().isOk()
            .returnResult(CurrencyRateResponse::class.java)
            .responseBody

        assertEquals("GBP", response?.currencyCode)
        assertEquals(0, response?.rate?.compareTo(BigDecimal("7.0")))
        assertEquals(false, response?.isBase)

        restTestClient.get()
            .uri("/private/v1/currency-rates")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.page.totalElements").isEqualTo(4)
    }

    @Test
    @Order(5)
    fun `rejects a non-positive rate with 400`() {
        restTestClient.put()
            .uri("/private/v1/currency-rates/JPY")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"rate":0}""")
            .exchange()
            .expectStatus().isBadRequest()
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "br.com.investlog.server.currencyrates.rest.controllers.CurrencyRateControllerTest"`
Expected: `BUILD FAILED` — compilation error, `unresolved reference: CurrencyRateResponse` (controller doesn't exist yet).

- [ ] **Step 3: Create the DTOs**

```kotlin
package br.com.investlog.server.currencyrates.rest.dtos

import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal

data class CurrencyRateResponse(
    val currencyCode: String,
    val rate: BigDecimal,
    @get:JsonProperty("isBase")
    val isBase: Boolean,
)
```

```kotlin
package br.com.investlog.server.currencyrates.rest.dtos

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.DecimalMin
import java.math.BigDecimal

data class CurrencyRateUpsertRequest(
    @field:DecimalMin(value = "0", inclusive = false, message = "rate must be greater than 0")
    val rate: BigDecimal,

    @JsonProperty("isBase")
    val isBase: Boolean = false,
)
```

> **Note:** `val isBase: Boolean` triggers a well-known Jackson/Kotlin getter-naming quirk —
> Kotlin generates `isBase()` (not `getIsBase()`), which standard JavaBean introspection would
> turn into the property name `base`, not `isBase`. The `@get:JsonProperty("isBase")` /
> `@JsonProperty("isBase")` annotations above pin the JSON field name to `isBase` as required by
> the spec's `{currencyCode, rate, isBase}` shape — do not rename it to `base` to make a test
> pass.

- [ ] **Step 4: Create `CurrencyRateRepository`**

```kotlin
package br.com.investlog.server.currencyrates.domain.repositories

import br.com.investlog.server.currencyrates.rest.dtos.CurrencyRateResponse
import br.com.investlog.server.jooq.finances.tables.records.CurrencyRatesRecord
import br.com.investlog.server.jooq.finances.tables.references.CURRENCY_RATES
import br.com.investlog.server.shared.persistence.pagedModelOf
import java.math.BigDecimal
import java.time.OffsetDateTime
import org.jooq.DSLContext
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class CurrencyRateRepository(private val dsl: DSLContext) {

    fun findAll(userId: Long, pageable: Pageable): PagedModel<CurrencyRateResponse> {
        val content = dsl.selectFrom(CURRENCY_RATES)
            .where(CURRENCY_RATES.USER_ID.eq(userId))
            .orderBy(CURRENCY_RATES.CURRENCY_CODE)
            .limit(pageable.pageSize)
            .offset(pageable.offset.toInt())
            .fetch()
            .map { it.toResponse() }

        val total = dsl.fetchCount(
            dsl.selectFrom(CURRENCY_RATES).where(CURRENCY_RATES.USER_ID.eq(userId))
        )

        return pagedModelOf(content, pageable, total.toLong())
    }

    @Transactional
    fun upsert(userId: Long, currencyCode: String, rate: BigDecimal, isBase: Boolean): CurrencyRateResponse {
        if (isBase) {
            dsl.update(CURRENCY_RATES)
                .set(CURRENCY_RATES.IS_BASE, false)
                .where(CURRENCY_RATES.USER_ID.eq(userId))
                .and(CURRENCY_RATES.IS_BASE.isTrue())
                .and(CURRENCY_RATES.CURRENCY_CODE.ne(currencyCode))
                .execute()
        }

        return dsl.insertInto(CURRENCY_RATES)
            .set(CURRENCY_RATES.USER_ID, userId)
            .set(CURRENCY_RATES.CURRENCY_CODE, currencyCode)
            .set(CURRENCY_RATES.RATE, rate)
            .set(CURRENCY_RATES.IS_BASE, isBase)
            .onConflict(CURRENCY_RATES.USER_ID, CURRENCY_RATES.CURRENCY_CODE)
            .doUpdate()
            .set(CURRENCY_RATES.RATE, rate)
            .set(CURRENCY_RATES.IS_BASE, isBase)
            .set(CURRENCY_RATES.UPDATED_AT, OffsetDateTime.now())
            .returning()
            .fetchSingle()
            .toResponse()
    }

    private fun CurrencyRatesRecord.toResponse() = CurrencyRateResponse(
        currencyCode = currencyCode!!,
        rate = rate!!,
        isBase = isBase!!,
    )
}
```

- [ ] **Step 5: Create `CurrencyRateService`**

```kotlin
package br.com.investlog.server.currencyrates.domain.services

import br.com.investlog.server.currencyrates.domain.repositories.CurrencyRateRepository
import br.com.investlog.server.currencyrates.rest.dtos.CurrencyRateResponse
import br.com.investlog.server.shared.security.CurrentUserProvider
import java.math.BigDecimal
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.stereotype.Service

@Service
class CurrencyRateService(
    private val currentUserProvider: CurrentUserProvider,
    private val currencyRateRepository: CurrencyRateRepository,
) {

    fun findAll(pageable: Pageable): PagedModel<CurrencyRateResponse> {
        val userId = currentUserProvider.getCurrentUser().id

        return currencyRateRepository.findAll(userId, pageable)
    }

    fun upsert(currencyCode: String, rate: BigDecimal, isBase: Boolean): CurrencyRateResponse {
        val userId = currentUserProvider.getCurrentUser().id

        return currencyRateRepository.upsert(userId, currencyCode, rate, isBase)
    }
}
```

- [ ] **Step 6: Create `CurrencyRateController`**

```kotlin
package br.com.investlog.server.currencyrates.rest.controllers

import br.com.investlog.server.currencyrates.domain.services.CurrencyRateService
import br.com.investlog.server.currencyrates.rest.dtos.CurrencyRateResponse
import br.com.investlog.server.currencyrates.rest.dtos.CurrencyRateUpsertRequest
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/currency-rates")
class CurrencyRateController(private val currencyRateService: CurrencyRateService) {

    @GetMapping
    fun findAll(pageable: Pageable): PagedModel<CurrencyRateResponse> =
        currencyRateService.findAll(pageable)

    @PutMapping("/{currencyCode}")
    fun upsert(
        @PathVariable currencyCode: String,
        @Valid @RequestBody request: CurrencyRateUpsertRequest,
    ): CurrencyRateResponse =
        currencyRateService.upsert(currencyCode, request.rate, request.isBase)
}
```

- [ ] **Step 7: Run test to verify it passes**

Run: `./gradlew test --tests "br.com.investlog.server.currencyrates.rest.controllers.CurrencyRateControllerTest"`
Expected: `BUILD SUCCESSFUL`, 5/5 tests passing.

- [ ] **Step 8: Commit**

```bash
git add server/src/main/kotlin/br/com/investlog/server/currencyrates server/src/test/kotlin/br/com/investlog/server/currencyrates/rest/controllers/CurrencyRateControllerTest.kt
git commit -m "Add currency-rates list and upsert endpoints"
```

---

## Task 6: Update `server/CLAUDE.md`

**Files:**
- Modify: `server/CLAUDE.md`

- [ ] **Step 1: Update the Architecture section**

The current Architecture section's bullet list (after `Application.kt` bootstrap) is:

```markdown
- `shared/security` — cross-cutting current-user resolution: `CurrentUser` (domain model),
  `UserRepository` (queries/updates `system.users` via the generated jOOQ `USERS` table), and
  `CurrentUserProvider`/`FixedCurrentUserProvider` (resolves the current user; fixed to a dev
  user for now).
- `config` — `WebMvcConfig` (path-segment API versioning, prefixes `@RestController`s under
  `/private/{version}`) and `GlobalExceptionHandler` (RFC 7807 `ProblemDetail` error responses).
- `profile` — the first business module: `GET`/`PATCH /private/v1/profile`, following a
  `rest/{controllers,dtos}` + `domain/services` layout that future modules will follow.

The persistence schema itself is fully defined (see below).
```

Replace it with:

```markdown
- `shared/security` — cross-cutting current-user resolution: `CurrentUser` (domain model),
  `UserRepository` (queries/updates `system.users` via the generated jOOQ `USERS` table), and
  `CurrentUserProvider`/`FixedCurrentUserProvider` (resolves the current user; fixed to a dev
  user for now).
- `shared/persistence` — `pagedModelOf(content, pageable, total)`, the single place jOOQ page
  results become `org.springframework.data.web.PagedModel<T>` for collection endpoints.
- `shared/exceptions` — `NotFoundException`, mapped by `GlobalExceptionHandler` to a 404
  `ProblemDetail`.
- `config` — `WebMvcConfig` (path-segment API versioning, prefixes `@RestController`s under
  `/private/{version}`) and `GlobalExceptionHandler` (RFC 7807 `ProblemDetail` error responses:
  400 validation errors, 404 `NotFoundException`, 409 `DataIntegrityViolationException` from
  unique/FK-restrict violations, 500 catch-all).
- `profile` — `GET`/`PATCH /private/v1/profile`, following a `rest/{controllers,dtos}` +
  `domain/services` layout.
- `typelists` — `GET`/`POST`/`DELETE /private/v1/stock-types` and `.../fund-types`, paginated,
  sharing one pair of DTOs (`TypeResponse`/`TypeCreateRequest`) since both resources are
  `{id, name}`. Extends the `profile` layout with `domain/repositories`.
- `currencyrates` — `GET`/`PUT /private/v1/currency-rates`, addressed by `currencyCode` (not
  `external_id`); `PUT` upserts and, when `isBase: true`, clears the previous base row in the
  same transaction.

The persistence schema itself is fully defined (see below).
```

- [ ] **Step 2: Note the `spring-data-commons` dependency**

In the bullet list further down that starts with `- Kotlin 2.3.21 / Spring Boot 4.1.0...`, find this bullet:

```markdown
- Other starters: `actuator`, `mail`, `validation`.
```

Replace it with:

```markdown
- Other starters: `actuator`, `mail`, `validation`.
- `spring-data-commons` provides `Pageable`/`Page`/`PagedModel` and the MVC argument-resolver
  auto-configuration for paginated collection endpoints (default page size 20, configured via
  `spring.data.web.pageable.default-page-size`).
```

- [ ] **Step 3: Commit**

```bash
git add server/CLAUDE.md
git commit -m "Document typelists/currencyrates modules and pagination conventions in CLAUDE.md"
```

---

## Self-Review

**Spec coverage** (against `docs/superpowers/specs/2026-06-14-server-rest-api-design.md`):
- "Pagination — every collection endpoint" → Task 1 (`pagedModelOf`, `spring-data-commons`,
  default `size=20`), applied in Tasks 3-5.
- "Error handling" 404/409/400 → Task 2 (`NotFoundException`/`DataIntegrityViolationException`
  handlers), exercised by Tasks 3-5's tests.
- "Stock types / Fund types" endpoint table (`GET`/`POST`/`DELETE`, `{id, name}`,
  `UNIQUE(user_id, name)` → 409, FK-restrict on delete → 409) → Tasks 3 and 4.
- "Currency rates" endpoint table (`GET` paginated `{currencyCode, rate, isBase}`,
  `PUT /{currencyCode}` upsert, base-currency-switch transaction) → Task 5.
- `currencySymbol`/`currencies` display constants explicitly stay client-side — no task needed.

**Out of scope for this plan** (left for Plan 3, per Plan 1's "Next Plans"):
- The FK-restrict → 409 path for `DELETE /stock-types/{id}` / `/fund-types/{id}` is wired up by
  Task 2's `DataIntegrityViolationException` handler, but can't be exercised end-to-end until
  Plan 3 adds `stock_holdings`/`fund_holdings` rows that reference these types — Task 3/4's tests
  cover the reachable 409 case (unique-name violation on `POST`) instead.

**Placeholder scan:** none — every step has complete code, exact commands, and expected output.

**Type consistency:** `TypeResponse`/`TypeCreateRequest` (Task 3) are reused as-is by Task 4.
`CurrencyRateResponse`/`CurrencyRateUpsertRequest` (Task 5) are self-contained. `pagedModelOf`
(Task 1) signature `(List<T>, Pageable, Long) -> PagedModel<T>` matches every call site in Tasks
3-5. `NotFoundException`/`handleDataIntegrityViolation` (Task 2) signatures match their usage in
Tasks 3-5's services and the `GlobalExceptionHandler` itself.

---

## Next Plan

**Plan 3 of 4** (wallets & holdings CRUD) needs new Liquibase changesets before it can be written:
`finances.wallet_totals` (per-wallet aggregates) and the rebuilt `finances.holdings_overview`
(plain view, `entries` jsonb column) — both from the spec's "Schema changes" section, items 2 and
4. Those changesets must land and `./gradlew generateJooq` must run before Plan 3's tasks can
reference exact jOOQ field names (especially `entries`/`JSONB`). Plan 4 (holdings read &
aggregates) additionally needs `finances.portfolio_summary` and `finances.investment_events`
(items 3 and 5).
