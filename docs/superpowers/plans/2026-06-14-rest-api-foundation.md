# REST API Foundation & Profile Endpoint Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Lay the cross-cutting foundation for the server's REST API — user preference columns, dev-data seeding, current-user resolution, path-segment API versioning under `/private/v1`, and a global exception handler — then prove it all works end to end with a complete `GET`/`PATCH /private/v1/profile` vertical slice.

**Architecture:** Two new Liquibase changesets extend `system.users` with `accent_color`/`preferred_currency` and seed a `dev-user` row plus base currency rates. A `shared.security` package adds jOOQ-backed `UserRepository` and `CurrentUserProvider` for resolving "the current user" (a fixed dev user, no auth yet). A global `config` package adds `WebMvcConfig` (Spring Framework 7 path-segment API versioning: every `@RestController` is prefixed `/private/{version}`, with `v1` as the only supported/default version) and `GlobalExceptionHandler` (`@RestControllerAdvice` → RFC 7807 `ProblemDetail` for validation errors and unexpected exceptions). A `profile` module (DTOs, service, controller) implements `GET`/`PATCH /profile`, which becomes `/private/v1/profile` via the versioning config.

This is **Plan 1 of 4** covering `docs/superpowers/specs/2026-06-14-server-rest-api-design.md`. Plans 2–4 (type lists & currency rates, wallets & holdings CRUD, holdings read & aggregates) depend on jOOQ types generated from this plan's schema changes and from later schema changes not yet written — they will be authored after this plan is executed and `generateJooq` has run.

**Tech Stack:** Kotlin 2.3.21, Spring Boot 4.1.0 / Spring Framework 7 (WebMVC, path-segment API versioning), jOOQ 3.21 (KotlinGenerator), Liquibase, PostgreSQL 17, JUnit 5 + `kotlin-test-junit5`, Testcontainers, `RestTestClient`.

---

## File Structure

```
server/src/main/resources/db/changelog/
  db.changelog-master.xml                                          [MODIFY]
  changes/2026/06/
    14-1000-add-user-preferences.xml                               [CREATE]
    14-1050-seed-dev-data.xml                                       [CREATE]

server/src/main/kotlin/br/com/investlog/server/
  config/
    WebMvcConfig.kt                                                 [CREATE]
    GlobalExceptionHandler.kt                                       [CREATE]
  shared/security/
    CurrentUser.kt                                                  [CREATE]
    UserRepository.kt                                               [CREATE]
    CurrentUserProvider.kt                                          [CREATE]
  profile/
    rest/dtos/
      ProfileResponse.kt                                            [CREATE]
      ProfileUpdateRequest.kt                                       [CREATE]
    domain/services/
      ProfileService.kt                                             [CREATE]
    rest/controllers/
      ProfileController.kt                                          [CREATE]

server/src/test/kotlin/br/com/investlog/server/
  shared/security/
    CurrentUserProviderTest.kt                                      [CREATE]
  profile/rest/controllers/
    ProfileControllerTest.kt                                        [CREATE]
```

- **`shared/security`**: cross-cutting "who is the current user" concern. `UserRepository` is the only jOOQ access point for `system.users`; `CurrentUserProvider` is the interface every future module depends on to resolve the current user.
- **`config`**: app-wide MVC configuration (`WebMvcConfig`) and error handling (`GlobalExceptionHandler`). Future plans extend `GlobalExceptionHandler` with `@ExceptionHandler` methods for `NotFoundException`/`ConflictException` (404/409) once those exceptions are introduced — not needed yet since nothing throws them.
- **`profile`**: first business module, following the `rest/{controllers,dtos}` + `domain/services` layout. `ProfileService` returns the response DTO directly — there's no separate domain model because the profile is a 1:1 view over `CurrentUser`.

---

## Task 1: Add `accent_color` / `preferred_currency` columns to `system.users`

**Files:**
- Create: `server/src/main/resources/db/changelog/changes/2026/06/14-1000-add-user-preferences.xml`
- Modify: `server/src/main/resources/db/changelog/db.changelog-master.xml`

- [ ] **Step 1: Create the changeset file**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
    xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        https://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-5.0.xsd">

    <changeSet id="14-1000-1" author="${claude}">
        <sql>
            ALTER TABLE system.users
                ADD COLUMN accent_color TEXT NOT NULL DEFAULT 'teal'
                    CHECK (accent_color IN ('blue', 'indigo', 'teal', 'green')),
                ADD COLUMN preferred_currency TEXT NOT NULL DEFAULT 'BRL';
        </sql>
        <rollback>
            <sql>
                ALTER TABLE system.users
                    DROP COLUMN accent_color,
                    DROP COLUMN preferred_currency;
            </sql>
        </rollback>
    </changeSet>

</databaseChangeLog>
```

- [ ] **Step 2: Register the changeset in the master changelog**

In `server/src/main/resources/db/changelog/db.changelog-master.xml`, add a new `<include>` line after the existing six:

```xml
    <include file="db/changelog/changes/2026/06/12-1050-create-holdings-overview-view.xml" relativeToChangelogFile="false"/>

    <include file="db/changelog/changes/2026/06/14-1000-add-user-preferences.xml" relativeToChangelogFile="false"/>

</databaseChangeLog>
```

- [ ] **Step 3: Run jOOQ codegen to verify the changeset applies and regenerates types**

Run: `./gradlew generateJooq`
Expected: `BUILD SUCCESSFUL`. The Testcontainer-backed Liquibase run applies the new changeset without error.

- [ ] **Step 4: Confirm the generated `Users` table picked up the new columns**

Open `server/build/generated-sources/jooq/main/br/com/investlog/server/jooq/system/tables/Users.kt` and confirm it now declares:

```kotlin
val ACCENT_COLOR: TableField<UsersRecord, String?> = createField(DSL.name("accent_color"), SQLDataType.CLOB.nullable(false).defaultValue(DSL.field(DSL.raw("'teal'"), SQLDataType.CLOB)), this, "")

val PREFERRED_CURRENCY: TableField<UsersRecord, String?> = createField(DSL.name("preferred_currency"), SQLDataType.CLOB.nullable(false).defaultValue(DSL.field(DSL.raw("'BRL'"), SQLDataType.CLOB)), this, "")
```

and that `server/build/generated-sources/jooq/main/br/com/investlog/server/jooq/system/tables/records/UsersRecord.kt` now declares `accentColor: String?` and `preferredCurrency: String?` properties.

- [ ] **Step 5: Commit**

```bash
git add server/src/main/resources/db/changelog/changes/2026/06/14-1000-add-user-preferences.xml server/src/main/resources/db/changelog/db.changelog-master.xml
git commit -m "Add accent_color and preferred_currency columns to system.users"
```

---

## Task 2: Seed the dev user and base currency rates

**Files:**
- Create: `server/src/main/resources/db/changelog/changes/2026/06/14-1050-seed-dev-data.xml`
- Modify: `server/src/main/resources/db/changelog/db.changelog-master.xml`

- [ ] **Step 1: Create the seed changeset**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
    xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        https://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-5.0.xsd">

    <changeSet id="14-1050-1" author="${claude}">
        <sql>
            INSERT INTO system.users (google_sub, email, name)
            VALUES ('dev-user', 'arthurshakal@gmail.com', 'Arthur Gregorio');
        </sql>
        <rollback>
            <sql>DELETE FROM system.users WHERE google_sub = 'dev-user';</sql>
        </rollback>
    </changeSet>

    <changeSet id="14-1050-2" author="${claude}">
        <sql>
            INSERT INTO finances.currency_rates (user_id, currency_code, rate, is_base)
            SELECT u.id, v.currency_code, v.rate, v.is_base
            FROM system.users u
            CROSS JOIN (VALUES
                ('BRL', 1::numeric, true),
                ('USD', 5.42::numeric, false),
                ('EUR', 5.88::numeric, false)
            ) AS v(currency_code, rate, is_base)
            WHERE u.google_sub = 'dev-user';
        </sql>
        <rollback>
            <sql>
                DELETE FROM finances.currency_rates
                WHERE user_id = (SELECT id FROM system.users WHERE google_sub = 'dev-user');
            </sql>
        </rollback>
    </changeSet>

</databaseChangeLog>
```

- [ ] **Step 2: Register the changeset in the master changelog**

In `server/src/main/resources/db/changelog/db.changelog-master.xml`, add the include after the one from Task 1:

```xml
    <include file="db/changelog/changes/2026/06/14-1000-add-user-preferences.xml" relativeToChangelogFile="false"/>

    <include file="db/changelog/changes/2026/06/14-1050-seed-dev-data.xml" relativeToChangelogFile="false"/>

</databaseChangeLog>
```

- [ ] **Step 3: Run jOOQ codegen to verify the seed changeset applies cleanly**

Run: `./gradlew generateJooq`
Expected: `BUILD SUCCESSFUL`. The dev user row (`google_sub='dev-user'`) and its three `currency_rates` rows insert without FK or constraint errors.

- [ ] **Step 4: Commit**

```bash
git add server/src/main/resources/db/changelog/changes/2026/06/14-1050-seed-dev-data.xml server/src/main/resources/db/changelog/db.changelog-master.xml
git commit -m "Seed dev user and base currency rates for local development"
```

---

## Task 3: Add `CurrentUserProvider` for resolving the dev user

**Files:**
- Create: `server/src/test/kotlin/br/com/investlog/server/shared/security/CurrentUserProviderTest.kt`
- Create: `server/src/main/kotlin/br/com/investlog/server/shared/security/CurrentUser.kt`
- Create: `server/src/main/kotlin/br/com/investlog/server/shared/security/UserRepository.kt`
- Create: `server/src/main/kotlin/br/com/investlog/server/shared/security/CurrentUserProvider.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package br.com.investlog.server.shared.security

import br.com.investlog.server.TestcontainersConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class CurrentUserProviderTest {

    @Autowired
    lateinit var currentUserProvider: CurrentUserProvider

    @Test
    fun `resolves the seeded dev user`() {
        val user = currentUserProvider.getCurrentUser()

        assertEquals("arthurshakal@gmail.com", user.email)
        assertEquals("Arthur Gregorio", user.name)
        assertEquals("teal", user.accentColor)
        assertEquals("BRL", user.preferredCurrency)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests "br.com.investlog.server.shared.security.CurrentUserProviderTest"`
Expected: `BUILD FAILED` — compilation error, `unresolved reference: CurrentUserProvider` (and `CurrentUser`).

- [ ] **Step 3: Create the `CurrentUser` domain model**

```kotlin
package br.com.investlog.server.shared.security

import java.util.UUID

data class CurrentUser(
    val id: Long,
    val externalId: UUID,
    val name: String,
    val email: String,
    val avatarUrl: String?,
    val accentColor: String,
    val preferredCurrency: String,
)
```

- [ ] **Step 4: Create `UserRepository`**

```kotlin
package br.com.investlog.server.shared.security

import br.com.investlog.server.jooq.system.tables.records.UsersRecord
import br.com.investlog.server.jooq.system.tables.references.USERS
import java.time.OffsetDateTime
import org.jooq.DSLContext
import org.springframework.stereotype.Repository

@Repository
class UserRepository(private val dsl: DSLContext) {

    fun findByGoogleSub(googleSub: String): CurrentUser? {
        return dsl.selectFrom(USERS)
            .where(USERS.GOOGLE_SUB.eq(googleSub))
            .fetchOne()
            ?.toCurrentUser()
    }

    fun updatePreferences(userId: Long, accentColor: String, preferredCurrency: String): CurrentUser {
        return dsl.update(USERS)
            .set(USERS.ACCENT_COLOR, accentColor)
            .set(USERS.PREFERRED_CURRENCY, preferredCurrency)
            .set(USERS.UPDATED_AT, OffsetDateTime.now())
            .where(USERS.ID.eq(userId))
            .returning()
            .fetchSingle()
            .toCurrentUser()
    }

    private fun UsersRecord.toCurrentUser() = CurrentUser(
        id = id!!,
        externalId = externalId!!,
        name = name!!,
        email = email!!,
        avatarUrl = avatarUrl,
        accentColor = accentColor!!,
        preferredCurrency = preferredCurrency!!,
    )
}
```

- [ ] **Step 5: Create `CurrentUserProvider`**

```kotlin
package br.com.investlog.server.shared.security

import org.springframework.stereotype.Component

interface CurrentUserProvider {
    fun getCurrentUser(): CurrentUser
}

@Component
class FixedCurrentUserProvider(
    private val userRepository: UserRepository,
) : CurrentUserProvider {

    companion object {
        private const val DEV_USER_GOOGLE_SUB = "dev-user"
    }

    override fun getCurrentUser(): CurrentUser =
        userRepository.findByGoogleSub(DEV_USER_GOOGLE_SUB)
            ?: error("Dev user '$DEV_USER_GOOGLE_SUB' not found — check the 14-1050-seed-dev-data.xml changeset")
}
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `./gradlew test --tests "br.com.investlog.server.shared.security.CurrentUserProviderTest"`
Expected: `BUILD SUCCESSFUL`, 1 test passed.

- [ ] **Step 7: Commit**

```bash
git add server/src/main/kotlin/br/com/investlog/server/shared/security server/src/test/kotlin/br/com/investlog/server/shared/security
git commit -m "Add CurrentUserProvider for resolving the dev user from system.users"
```

---

## Task 4: Configure path-segment API versioning under `/private/v1`

**Files:**
- Create: `server/src/main/kotlin/br/com/investlog/server/config/WebMvcConfig.kt`

This task has no dedicated test — Spring Framework 7's path-segment versioning only becomes observable once a `@RestController` exists. Task 7's `ProfileControllerTest` exercises it end to end (it calls `/private/v1/profile`, which only resolves if this config is correct).

- [ ] **Step 1: Create `WebMvcConfig`**

```kotlin
package br.com.investlog.server.config

import org.springframework.context.annotation.Configuration
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.method.HandlerTypePredicate
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebMvcConfig : WebMvcConfigurer {

    override fun configurePathMatch(configurer: PathMatchConfigurer) {
        configurer.addPathPrefix(
            "/private/{version}",
            HandlerTypePredicate.forAnnotation(RestController::class.java),
        )
    }

    override fun configureApiVersioning(configurer: ApiVersionConfigurer) {
        configurer.usePathSegment(1)
            .addSupportedVersions("v1")
            .setDefaultVersion("v1")
    }
}
```

- [ ] **Step 2: Verify the project compiles**

Run: `./gradlew compileKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add server/src/main/kotlin/br/com/investlog/server/config/WebMvcConfig.kt
git commit -m "Configure Spring MVC path-segment API versioning under /private/{version}"
```

---

## Task 5: Add `GlobalExceptionHandler`

**Files:**
- Create: `server/src/main/kotlin/br/com/investlog/server/config/GlobalExceptionHandler.kt`

Like Task 4, this has no dedicated test in isolation — Task 7's `ProfileControllerTest` exercises the validation-error path (`handleMethodArgumentNotValid`) via `PATCH /private/v1/profile` with an invalid `accentColor`.

- [ ] **Step 1: Create `GlobalExceptionHandler`**

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

- [ ] **Step 2: Verify the project compiles**

Run: `./gradlew compileKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add server/src/main/kotlin/br/com/investlog/server/config/GlobalExceptionHandler.kt
git commit -m "Add GlobalExceptionHandler for validation and unexpected errors"
```

---

## Task 6: Add Profile DTOs

**Files:**
- Create: `server/src/main/kotlin/br/com/investlog/server/profile/rest/dtos/ProfileResponse.kt`
- Create: `server/src/main/kotlin/br/com/investlog/server/profile/rest/dtos/ProfileUpdateRequest.kt`

- [ ] **Step 1: Create `ProfileResponse`**

```kotlin
package br.com.investlog.server.profile.rest.dtos

data class ProfileResponse(
    val name: String,
    val email: String,
    val avatarUrl: String?,
    val accentColor: String,
    val preferredCurrency: String,
)
```

- [ ] **Step 2: Create `ProfileUpdateRequest`**

```kotlin
package br.com.investlog.server.profile.rest.dtos

import jakarta.validation.constraints.Pattern

data class ProfileUpdateRequest(
    @field:Pattern(regexp = "blue|indigo|teal|green", message = "accentColor must be one of: blue, indigo, teal, green")
    val accentColor: String? = null,

    @field:Pattern(regexp = "[A-Z]{3}", message = "preferredCurrency must be a 3-letter ISO currency code")
    val preferredCurrency: String? = null,
)
```

- [ ] **Step 3: Verify the project compiles**

Run: `./gradlew compileKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add server/src/main/kotlin/br/com/investlog/server/profile/rest/dtos
git commit -m "Add profile response and update request DTOs"
```

---

## Task 7: Add the `/profile` REST endpoint

**Files:**
- Create: `server/src/test/kotlin/br/com/investlog/server/profile/rest/controllers/ProfileControllerTest.kt`
- Create: `server/src/main/kotlin/br/com/investlog/server/profile/domain/services/ProfileService.kt`
- Create: `server/src/main/kotlin/br/com/investlog/server/profile/rest/controllers/ProfileController.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package br.com.investlog.server.profile.rest.controllers

import br.com.investlog.server.TestcontainersConfiguration
import br.com.investlog.server.profile.rest.dtos.ProfileResponse
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
class ProfileControllerTest {

    @Autowired
    lateinit var restTestClient: RestTestClient

    @Test
    @Order(1)
    fun `returns the current user's profile`() {
        val response = restTestClient.get()
            .uri("/private/v1/profile")
            .exchange()
            .expectStatus().isOk()
            .returnResult(ProfileResponse::class.java)
            .responseBody

        assertEquals("Arthur Gregorio", response?.name)
        assertEquals("arthurshakal@gmail.com", response?.email)
        assertEquals("teal", response?.accentColor)
        assertEquals("BRL", response?.preferredCurrency)
    }

    @Test
    @Order(2)
    fun `updates accent color and preserves preferred currency`() {
        val response = restTestClient.patch()
            .uri("/private/v1/profile")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"accentColor":"indigo"}""")
            .exchange()
            .expectStatus().isOk()
            .returnResult(ProfileResponse::class.java)
            .responseBody

        assertEquals("indigo", response?.accentColor)
        assertEquals("BRL", response?.preferredCurrency)
    }

    @Test
    @Order(3)
    fun `rejects an invalid accent color`() {
        restTestClient.patch()
            .uri("/private/v1/profile")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"accentColor":"purple"}""")
            .exchange()
            .expectStatus().isBadRequest()
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests "br.com.investlog.server.profile.rest.controllers.ProfileControllerTest"`
Expected: `BUILD FAILED`. Either the first test fails with a 404 Not Found (since `/private/v1/profile` has no mapping yet), or compilation fails with an unresolved reference for `RestTestClient`/`AutoConfigureRestTestClient`/`ProfileResponse`/`ProfileService`/`ProfileController`. If it's the latter, that's expected too — those types don't exist until Steps 3–4, and `RestTestClient` support comes from the `spring-boot-starter-webmvc-test` dependency, not from anything created in this task.

- [ ] **Step 3: Create `ProfileService`**

```kotlin
package br.com.investlog.server.profile.domain.services

import br.com.investlog.server.profile.rest.dtos.ProfileResponse
import br.com.investlog.server.profile.rest.dtos.ProfileUpdateRequest
import br.com.investlog.server.shared.security.CurrentUser
import br.com.investlog.server.shared.security.CurrentUserProvider
import br.com.investlog.server.shared.security.UserRepository
import org.springframework.stereotype.Service

@Service
class ProfileService(
    private val currentUserProvider: CurrentUserProvider,
    private val userRepository: UserRepository,
) {

    fun getProfile(): ProfileResponse =
        currentUserProvider.getCurrentUser().toResponse()

    fun updateProfile(request: ProfileUpdateRequest): ProfileResponse {
        val user = currentUserProvider.getCurrentUser()

        return userRepository.updatePreferences(
            userId = user.id,
            accentColor = request.accentColor ?: user.accentColor,
            preferredCurrency = request.preferredCurrency ?: user.preferredCurrency,
        ).toResponse()
    }

    private fun CurrentUser.toResponse() = ProfileResponse(
        name = name,
        email = email,
        avatarUrl = avatarUrl,
        accentColor = accentColor,
        preferredCurrency = preferredCurrency,
    )
}
```

- [ ] **Step 4: Create `ProfileController`**

```kotlin
package br.com.investlog.server.profile.rest.controllers

import br.com.investlog.server.profile.domain.services.ProfileService
import br.com.investlog.server.profile.rest.dtos.ProfileResponse
import br.com.investlog.server.profile.rest.dtos.ProfileUpdateRequest
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/profile")
class ProfileController(private val profileService: ProfileService) {

    @GetMapping
    fun getProfile(): ProfileResponse = profileService.getProfile()

    @PatchMapping
    fun updateProfile(@Valid @RequestBody request: ProfileUpdateRequest): ProfileResponse =
        profileService.updateProfile(request)
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew test --tests "br.com.investlog.server.profile.rest.controllers.ProfileControllerTest"`
Expected: `BUILD SUCCESSFUL`, 3 tests passed. If the first test still 404s at this point, the controller mapping exists but the path-segment version condition isn't matching — `ProfileController`'s mappings declare no `version`, which should match any resolved version (including the `v1` default from `WebMvcConfig`); the request already reaching a handler rules out `addPathPrefix` as the cause, so look at `configureApiVersioning` instead.

- [ ] **Step 6: Run the full test suite**

Run: `./gradlew test`
Expected: `BUILD SUCCESSFUL` — `ServerApplicationTests`, `CurrentUserProviderTest`, and `ProfileControllerTest` all pass.

- [ ] **Step 7: Commit**

```bash
git add server/src/main/kotlin/br/com/investlog/server/profile server/src/test/kotlin/br/com/investlog/server/profile
git commit -m "Add /profile REST endpoint for reading and updating user preferences"
```

---

## Self-Review

**Spec coverage** (against `docs/superpowers/specs/2026-06-14-server-rest-api-design.md`):
- API versioning & path structure (`/private/{version}` + `usePathSegment(1)` + `v1`) → Task 4.
- Error handling (`@RestControllerAdvice` → `ProblemDetail`, 400 validation) → Task 5, exercised by Task 7 Step 1's third test.
- `CurrentUserProvider` resolution of the fixed dev user → Task 3.
- Seed data (`system.users` dev row, `finances.currency_rates` BRL/USD/EUR) → Task 2.
- `accent_color`/`preferred_currency` columns → Task 1.
- Profile endpoints (`GET`/`PATCH /profile` → `{name, email, avatarUrl, accentColor, preferredCurrency}`) → Task 6, 7.
- 404/409 handling, pagination, `JsonMapper`/jsonb, and all non-profile endpoint groups are out of scope for this plan — they depend on schema/view changes and jOOQ types that Plans 2–4 will introduce.

**Placeholder scan:** No TBD/TODO, no "similar to Task N", no unshown code — every step has complete file contents or exact commands.

**Type consistency:** `CurrentUser` (Task 3) is used identically in `UserRepository`, `CurrentUserProvider`, and `ProfileService` (Task 7). `ProfileResponse`/`ProfileUpdateRequest` (Task 6) field names (`accentColor`, `preferredCurrency`, `name`, `email`, `avatarUrl`) match `CurrentUser`'s properties and the spec's JSON shape exactly.

---

## Next Plans

Plans 2–4 (type lists & currency rates, wallets & holdings CRUD, holdings read & aggregates) will be written **after** this plan is executed, since they depend on:
- The `spring-data-commons` dependency (`Pageable`/`Page`/`PagedModel`) — first needed by Plan 2.
- New Liquibase changesets for `finances.wallet_totals`, the rebuilt `finances.holdings_overview`, `finances.portfolio_summary`, and `finances.investment_events` — whose jOOQ-generated types (especially the `entries` jsonb column) must exist before those plans can reference exact field names.
