# Authentication Foundation (Phase 1) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make InvestLog require a real local-user login (email + password, session-cookie
based) instead of the hardcoded dev user, with role-aware route protection — the first of
four sequential PRs described in `docs/superpowers/specs/2026-06-27-authentication-design.md`.
Mandatory TOTP, self-registration/approval, and Google OAuth2 are explicitly **out of
scope for this plan** — they are Phases 2–4, each with their own plan, landing as separate
PRs on top of this one.

**Architecture:** Spring Security session-cookie auth, manually-populated
`SecurityContext` (no `UserDetailsService`/`AuthenticationProvider` ceremony — a plain
`AuthService` checks the password and writes the `SecurityContext` itself), BCrypt
password hashing. Client gets a Pinia `auth` store, a router guard, and the login screen
ported from the Claude Design source.

**Tech Stack:** Kotlin / Spring Boot 4 (`spring-boot-starter-security`), jOOQ, Liquibase,
Vue 3 `<script setup>` + Pinia + TypeScript.

## Global Constraints

- No abbreviated names anywhere (Kotlin and TypeScript) — see `server/CLAUDE.md` /
  `client/CLAUDE.md`.
- Never edit an existing Liquibase changeset **except** `14-1050-seed-dev-data.xml`,
  which the user explicitly authorized editing for this feature.
- Server REST payloads/services follow the existing `rest/{controllers,payloads}` +
  `domain/{services,repositories}` module layout (see `profile`, `typelists`).
- Client API calls go through `src/api/client.ts`'s `apiClient` axios instance; stores
  live under `src/stores/`, loaded lazily from views via `onMounted`.
- `npm run build` fails on TypeScript errors (`noUnusedLocals`/`noUnusedParameters` are
  on) — keep client code clean of unused imports/locals.

---

## Task 1: Schema — auth columns + seed-admin edit

**Files:**
- Create: `server/src/main/resources/db/changelog/changes/2026/06/28-1000-add-auth-columns-to-users.xml`
- Modify: `server/src/main/resources/db/changelog/db.changelog-master.xml` (add `<include>`)
- Modify: `server/src/main/resources/db/changelog/changes/2026/06/14-1050-seed-dev-data.xml`
  (explicit exception — see Global Constraints)
- Test: none (schema-only; verified by the next task's integration test, which fails to
  even boot if this is wrong)

**Interfaces:**
- Produces: `system.users` columns `password_hash TEXT NULL`, `auth_provider TEXT NOT
  NULL DEFAULT 'GOOGLE'`, `role TEXT NOT NULL DEFAULT 'USER'`, `status TEXT NOT NULL
  DEFAULT 'PENDING'`, `totp_secret TEXT NULL`, `totp_enabled BOOLEAN NOT NULL DEFAULT
  false`; `google_sub` no longer `NOT NULL`. Seeded admin row has `email =
  'admin@admin.com'`, `name = 'Administrador'`, `auth_provider = 'LOCAL'`, `role =
  'ADMIN'`, `status = 'APPROVED'`, `password_hash = NULL`, `google_sub = NULL`.

- [ ] **Step 1: Write the new changeset**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
    xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        https://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-5.0.xsd">

    <changeSet id="28-1000-1" author="${claude}">
        <sql>
            ALTER TABLE system.users ALTER COLUMN google_sub DROP NOT NULL;
            ALTER TABLE system.users ADD COLUMN password_hash TEXT;
            ALTER TABLE system.users ADD COLUMN auth_provider TEXT NOT NULL DEFAULT 'GOOGLE';
            ALTER TABLE system.users ADD COLUMN role TEXT NOT NULL DEFAULT 'USER';
            ALTER TABLE system.users ADD COLUMN status TEXT NOT NULL DEFAULT 'PENDING';
            ALTER TABLE system.users ADD COLUMN totp_secret TEXT;
            ALTER TABLE system.users ADD COLUMN totp_enabled BOOLEAN NOT NULL DEFAULT false;
        </sql>
        <rollback>
            <sql>
                ALTER TABLE system.users DROP COLUMN totp_enabled;
                ALTER TABLE system.users DROP COLUMN totp_secret;
                ALTER TABLE system.users DROP COLUMN status;
                ALTER TABLE system.users DROP COLUMN role;
                ALTER TABLE system.users DROP COLUMN auth_provider;
                ALTER TABLE system.users DROP COLUMN password_hash;
                ALTER TABLE system.users ALTER COLUMN google_sub SET NOT NULL;
            </sql>
        </rollback>
    </changeSet>

</databaseChangeLog>
```

- [ ] **Step 2: Register it in the master changelog**

In `server/src/main/resources/db/changelog/db.changelog-master.xml`, add this line right
after the `14-1050-seed-dev-data.xml` include and before `27-1000-seed-default-type-lists.xml`:

```xml
    <include file="db/changelog/changes/2026/06/28-1000-add-auth-columns-to-users.xml" relativeToChangelogFile="false"/>
```

- [ ] **Step 3: Edit the seed-dev-data changeset**

Replace the `14-1050-1` changeset's `<sql>`/`<rollback>` in
`server/src/main/resources/db/changelog/changes/2026/06/14-1050-seed-dev-data.xml` with:

```xml
    <changeSet id="14-1050-1" author="${claude}">
        <sql>
            INSERT INTO system.users (google_sub, email, name, auth_provider, role, status)
            VALUES (NULL, 'admin@admin.com', 'Administrador', 'LOCAL', 'ADMIN', 'APPROVED');
        </sql>
        <rollback>
            <sql>DELETE FROM system.users WHERE email = 'admin@admin.com';</sql>
        </rollback>
    </changeSet>
```

Update changeset `14-1050-2` (the currency-rates seed) to match on the new email instead
of `google_sub = 'dev-user'`:

```xml
    <changeSet id="14-1050-2" author="${claude}">
        <sql>
            INSERT INTO finances.currency_rates (user_id, currency_code, rate, is_base)
            SELECT u.id, v.currency_code, v.rate, v.is_base
            FROM system.users u
            CROSS JOIN (VALUES
                ('BRL', 1::numeric, true),
                ('USD', 5.42::numeric, false)
            ) AS v(currency_code, rate, is_base)
            WHERE u.email = 'admin@admin.com';
        </sql>
        <rollback>
            <sql>
                DELETE FROM finances.currency_rates
                WHERE user_id = (SELECT id FROM system.users WHERE email = 'admin@admin.com');
            </sql>
        </rollback>
    </changeSet>
```

- [ ] **Step 4: Regenerate jOOQ sources and confirm the build sees the new columns**

Run: `cd server && ./gradlew jooqCodegen`
Expected: BUILD SUCCESSFUL — this applies all changelogs (including your edited one) to
a throwaway Testcontainers Postgres and regenerates `build/generated-sources/jooq/main`.
If it fails, the SQL above has a typo — fix and re-run.

- [ ] **Step 5: Commit**

```bash
git add server/src/main/resources/db/changelog
git commit -m "feat(server): add auth columns to system.users, seed a local admin user"
```

---

## Task 2: Security dependency + inert config + password encoder

Adds Spring Security to the classpath with a **permit-all** filter chain so every
existing controller test still passes unauthenticated — this task only wires the
infrastructure; lock-down happens in Task 6 once login exists.

**Files:**
- Modify: `server/build.gradle.kts`
- Create: `server/src/main/kotlin/br/com/investlog/server/config/SecurityConfig.kt`
- Test: `server/src/test/kotlin/br/com/investlog/server/config/SecurityConfigTest.kt`

**Interfaces:**
- Produces: a `PasswordEncoder` bean (`BCryptPasswordEncoder`) other tasks inject; a
  `SecurityFilterChain` bean (currently `permitAll()`).

- [ ] **Step 1: Add the dependency**

In `server/build.gradle.kts`, in the `dependencies { ... }` block, add this line right
after `implementation("org.springframework.boot:spring-boot-starter-mail")`:

```kotlin
    implementation("org.springframework.boot:spring-boot-starter-security")
```

And in the `testImplementation` block, right after the matching `mail-test` line:

```kotlin
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
```

- [ ] **Step 2: Write the failing test**

```kotlin
package br.com.investlog.server.config

import br.com.investlog.server.BaseIntegrationTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.servlet.client.RestTestClient
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SecurityConfigTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTestClient: RestTestClient

    @Autowired
    lateinit var passwordEncoder: PasswordEncoder

    @Test
    fun `existing endpoints remain reachable without authentication for now`() {
        restTestClient.get()
            .uri("/private/v1/profile")
            .exchange()
            .expectStatus().isOk()
    }

    @Test
    fun `password encoder hashes and verifies a raw password`() {
        val hash = passwordEncoder.encode("admin")

        assertNotEquals("admin", hash)
        assertTrue(passwordEncoder.matches("admin", hash))
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `cd server && ./gradlew test --tests "br.com.investlog.server.config.SecurityConfigTest"`
Expected: FAIL to compile (`SecurityConfig`/`PasswordEncoder` not found) — `SecurityConfig.kt`
doesn't exist yet.

- [ ] **Step 4: Write the minimal implementation**

```kotlin
package br.com.investlog.server.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain

@Configuration
class SecurityConfig {

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http {
            csrf { disable() }
            authorizeHttpRequests {
                authorize(anyRequest, permitAll)
            }
        }
        return http.build()
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd server && ./gradlew test --tests "br.com.investlog.server.config.SecurityConfigTest"`
Expected: PASS (2 tests)

- [ ] **Step 6: Run the full test suite to confirm nothing else broke**

Run: `cd server && ./gradlew test`
Expected: BUILD SUCCESSFUL — every existing controller test still passes unauthenticated,
because the filter chain still permits everything.

- [ ] **Step 7: Commit**

```bash
git add server/build.gradle.kts server/src/main/kotlin/br/com/investlog/server/config/SecurityConfig.kt server/src/test/kotlin/br/com/investlog/server/config/SecurityConfigTest.kt
git commit -m "feat(server): add Spring Security with a permissive filter chain and a password encoder"
```

---

## Task 3: Domain — CurrentUser/UserRepository auth fields + AdminBootstrapRunner

Extends the user domain model with the auth columns, and seeds the admin's password from
an env var on first boot (since a Liquibase `<sql>` changeset can't call `BCryptPasswordEncoder`).

**Files:**
- Modify: `server/src/main/kotlin/br/com/investlog/server/shared/security/CurrentUser.kt`
- Modify: `server/src/main/kotlin/br/com/investlog/server/shared/security/UserRepository.kt`
- Create: `server/src/main/kotlin/br/com/investlog/server/shared/security/UserRole.kt`
- Create: `server/src/main/kotlin/br/com/investlog/server/shared/security/UserStatus.kt`
- Create: `server/src/main/kotlin/br/com/investlog/server/shared/security/AuthProvider.kt`
- Create: `server/src/main/kotlin/br/com/investlog/server/shared/security/AdminBootstrapRunner.kt`
- Modify: `server/src/test/kotlin/br/com/investlog/server/shared/security/CurrentUserProviderTest.kt`
- Test: `server/src/test/kotlin/br/com/investlog/server/shared/security/AdminBootstrapRunnerTest.kt`
- Modify: `server/src/test/resources/application-test.yml`

**Interfaces:**
- Consumes: `PasswordEncoder` from Task 2.
- Produces: `UserRole` enum (`ADMIN`, `USER`), `UserStatus` enum (`PENDING`, `APPROVED`),
  `AuthProvider` enum (`LOCAL`, `GOOGLE`); `CurrentUser` gains `role: UserRole`, `status:
  UserStatus`, `authProvider: AuthProvider`; `UserRepository.findByEmail(email: String):
  CurrentUser?` and `UserRepository.findPasswordHashByEmail(email: String): String?` for
  Task 5's login check.

- [ ] **Step 1: Write the three enums**

```kotlin
package br.com.investlog.server.shared.security

enum class UserRole { ADMIN, USER }
```

```kotlin
package br.com.investlog.server.shared.security

enum class UserStatus { PENDING, APPROVED }
```

```kotlin
package br.com.investlog.server.shared.security

enum class AuthProvider { LOCAL, GOOGLE }
```

- [ ] **Step 2: Extend `CurrentUser`**

Replace the full contents of `CurrentUser.kt`:

```kotlin
package br.com.investlog.server.shared.security

import br.com.investlog.server.profile.rest.payloads.AccentColor
import java.util.UUID

data class CurrentUser(
    val id: Long,
    val externalId: UUID,
    val name: String,
    val email: String,
    val avatarUrl: String?,
    val accentColor: AccentColor,
    val preferredCurrency: String,
    val role: UserRole,
    val status: UserStatus,
    val authProvider: AuthProvider,
)
```

- [ ] **Step 3: Write the failing test for the new repository methods**

Add to `CurrentUserProviderTest.kt` — replace its full contents (the old test called the
provider with no security context, which no longer makes sense once Task 6 wires
`SecurityContextHolder`; for now we test the repository directly):

```kotlin
package br.com.investlog.server.shared.security

import br.com.investlog.server.BaseIntegrationTest
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class UserRepositoryTest : BaseIntegrationTest() {

    @Autowired
    lateinit var userRepository: UserRepository

    @Test
    fun `finds the seeded admin by email`() {
        val user = userRepository.findByEmail("admin@admin.com")

        assertNotNull(user)
        assertEquals("Administrador", user.name)
        assertEquals(UserRole.ADMIN, user.role)
        assertEquals(UserStatus.APPROVED, user.status)
        assertEquals(AuthProvider.LOCAL, user.authProvider)
    }

    @Test
    fun `returns null for an unknown email`() {
        assertNull(userRepository.findByEmail("nobody@example.com"))
    }

    @Test
    fun `the admin bootstrap runner sets a non-null password hash`() {
        assertNotNull(userRepository.findPasswordHashByEmail("admin@admin.com"))
    }
}
```

Delete the old `CurrentUserProviderTest.kt` file (rename to `UserRepositoryTest.kt` above
— same package, this is the file you just rewrote).

- [ ] **Step 4: Run test to verify it fails**

Run: `cd server && ./gradlew test --tests "br.com.investlog.server.shared.security.UserRepositoryTest"`
Expected: FAIL to compile — `findByEmail`/`findPasswordHashByEmail` don't exist yet, and
`CurrentUser` doesn't have `role`/`status`/`authProvider` mapped from a record yet.

- [ ] **Step 5: Implement the repository changes**

Replace the full contents of `UserRepository.kt`:

```kotlin
package br.com.investlog.server.shared.security

import br.com.investlog.server.jooq.system.tables.records.UsersRecord
import br.com.investlog.server.jooq.system.tables.references.USERS
import br.com.investlog.server.profile.rest.payloads.AccentColor
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
class UserRepository(private val dsl: DSLContext) {

    fun findByGoogleSub(googleSub: String): CurrentUser? {
        return dsl.selectFrom(USERS)
            .where(USERS.GOOGLE_SUB.eq(googleSub))
            .fetchOne()
            ?.toCurrentUser()
    }

    fun findByEmail(email: String): CurrentUser? {
        return dsl.selectFrom(USERS)
            .where(USERS.EMAIL.eq(email))
            .fetchOne()
            ?.toCurrentUser()
    }

    fun findPasswordHashByEmail(email: String): String? {
        return dsl.select(USERS.PASSWORD_HASH)
            .from(USERS)
            .where(USERS.EMAIL.eq(email))
            .fetchOne(USERS.PASSWORD_HASH)
    }

    fun updatePasswordHash(userId: Long, passwordHash: String) {
        dsl.update(USERS)
            .set(USERS.PASSWORD_HASH, passwordHash)
            .set(USERS.UPDATED_AT, OffsetDateTime.now())
            .where(USERS.ID.eq(userId))
            .execute()
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
        accentColor = AccentColor.fromText(accentColor),
        preferredCurrency = preferredCurrency!!,
        role = UserRole.valueOf(role!!),
        status = UserStatus.valueOf(status!!),
        authProvider = AuthProvider.valueOf(authProvider!!),
    )
}
```

- [ ] **Step 6: Write the admin bootstrap runner**

```kotlin
package br.com.investlog.server.shared.security

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

@Component
class AdminBootstrapRunner(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    @Value("\${investlog.admin-default-password:admin}") private val adminDefaultPassword: String,
) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        val admin = userRepository.findByEmail(ADMIN_EMAIL) ?: return
        val existingHash = userRepository.findPasswordHashByEmail(ADMIN_EMAIL)
        if (existingHash != null) return

        log.warn { "Setting the seeded admin's password from investlog.admin-default-password — change it after first login." }
        userRepository.updatePasswordHash(admin.id, passwordEncoder.encode(adminDefaultPassword))
    }

    companion object {
        private const val ADMIN_EMAIL = "admin@admin.com"
    }
}
```

- [ ] **Step 7: Wire the env var into `application.yaml` and `.env.example`**

Add to `server/src/main/resources/application.yaml` (top-level, alongside `spring:`):

```yaml
investlog:
  admin-default-password: ${ADMIN_DEFAULT_PASSWORD:admin}
```

Add to `.env.example` (repo root) — append:

```
ADMIN_DEFAULT_PASSWORD=admin
```

- [ ] **Step 8: Run test to verify it passes**

Run: `cd server && ./gradlew test --tests "br.com.investlog.server.shared.security.UserRepositoryTest"`
Expected: PASS (3 tests) — `AdminBootstrapRunner` runs on `@SpringBootTest` context
startup, so by the time the test runs, the seeded admin already has a password hash.

- [ ] **Step 9: Run the full suite**

Run: `cd server && ./gradlew test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 10: Commit**

```bash
git add server/src/main/kotlin/br/com/investlog/server/shared/security server/src/test/kotlin/br/com/investlog/server/shared/security server/src/main/resources/application.yaml .env.example
git commit -m "feat(server): extend the user domain with role/status/auth-provider and bootstrap the admin password"
```

---

## Task 4: AuthService + AuthController (login / logout / session)

**Files:**
- Create: `server/src/main/kotlin/br/com/investlog/server/auth/domain/services/AuthService.kt`
- Create: `server/src/main/kotlin/br/com/investlog/server/auth/rest/controllers/AuthController.kt`
- Create: `server/src/main/kotlin/br/com/investlog/server/auth/rest/payloads/LoginRequest.kt`
- Create: `server/src/main/kotlin/br/com/investlog/server/auth/rest/payloads/SessionResponse.kt`
- Create: `server/src/main/kotlin/br/com/investlog/server/shared/exceptions/InvalidCredentialsException.kt`
- Modify: `server/src/main/kotlin/br/com/investlog/server/config/GlobalExceptionHandler.kt`
- Test: `server/src/test/kotlin/br/com/investlog/server/auth/rest/controllers/AuthControllerTest.kt`

**Interfaces:**
- Consumes: `UserRepository.findByEmail`/`findPasswordHashByEmail` (Task 3),
  `PasswordEncoder` (Task 2).
- Produces: `POST /private/v1/auth/login` (body `LoginRequest(email, password)`, returns
  `SessionResponse(name, email, role)` and sets the session cookie), `POST
  /private/v1/auth/logout`, `GET /private/v1/auth/session` (current session, 401 if none)
  — all still reachable without auth for now because Task 2's filter chain is permissive;
  Task 6 locks it down.

- [ ] **Step 1: Write the exception type**

```kotlin
package br.com.investlog.server.shared.exceptions

class InvalidCredentialsException(message: String) : RuntimeException(message)
```

- [ ] **Step 2: Write the payloads**

```kotlin
package br.com.investlog.server.auth.rest.payloads

data class LoginRequest(
    val email: String,
    val password: String,
)
```

```kotlin
package br.com.investlog.server.auth.rest.payloads

import br.com.investlog.server.shared.security.UserRole

data class SessionResponse(
    val name: String,
    val email: String,
    val role: UserRole,
)
```

- [ ] **Step 3: Write the failing controller test**

```kotlin
package br.com.investlog.server.auth.rest.controllers

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.auth.rest.payloads.SessionResponse
import org.junit.jupiter.api.Order
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.returnResult
import kotlin.test.Test
import kotlin.test.assertEquals

class AuthControllerTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTestClient: RestTestClient

    @Test
    @Order(1)
    fun `rejects an unknown email`() {
        restTestClient.post()
            .uri("/private/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"email":"nobody@example.com","password":"whatever"}""")
            .exchange()
            .expectStatus().isUnauthorized()
    }

    @Test
    @Order(2)
    fun `rejects the wrong password`() {
        restTestClient.post()
            .uri("/private/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"email":"admin@admin.com","password":"wrong"}""")
            .exchange()
            .expectStatus().isUnauthorized()
    }

    @Test
    @Order(3)
    fun `logs in with the seeded admin's credentials and reports the session`() {
        val response = restTestClient.post()
            .uri("/private/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"email":"admin@admin.com","password":"admin"}""")
            .exchange()
            .expectStatus().isOk()
            .returnResult<SessionResponse>()
            .responseBody

        assertEquals("Administrador", response?.name)
        assertEquals("admin@admin.com", response?.email)
    }
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `cd server && ./gradlew test --tests "br.com.investlog.server.auth.rest.controllers.AuthControllerTest"`
Expected: FAIL — 404, no `AuthController` yet.

- [ ] **Step 5: Implement `AuthService`**

```kotlin
package br.com.investlog.server.auth.domain.services

import br.com.investlog.server.auth.rest.payloads.LoginRequest
import br.com.investlog.server.auth.rest.payloads.SessionResponse
import br.com.investlog.server.shared.exceptions.InvalidCredentialsException
import br.com.investlog.server.shared.security.UserRepository
import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.stereotype.Service

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
) {

    fun login(request: LoginRequest, servletRequest: HttpServletRequest): SessionResponse {

        val user = userRepository.findByEmail(request.email)
            ?: throw InvalidCredentialsException("Invalid email or password")

        val passwordHash = userRepository.findPasswordHashByEmail(request.email)
            ?: throw InvalidCredentialsException("Invalid email or password")

        if (!passwordEncoder.matches(request.password, passwordHash)) {
            throw InvalidCredentialsException("Invalid email or password")
        }

        val authorities = listOf(SimpleGrantedAuthority("ROLE_${user.role}"))
        val authentication = UsernamePasswordAuthenticationToken(user, null, authorities)

        val context = SecurityContextHolder.createEmptyContext()
        context.authentication = authentication
        SecurityContextHolder.setContext(context)

        HttpSessionSecurityContextRepository().saveContext(
            context,
            servletRequest,
            null,
        )

        return SessionResponse(name = user.name, email = user.email, role = user.role)
    }
}
```

> Note: `HttpSessionSecurityContextRepository.saveContext` requires an `HttpServletResponse`
> too in some Spring Security versions — if the compiler complains about the signature,
> change the controller method to take `HttpServletResponse` as well and pass it through;
> the test only asserts on the JSON body and status code, not on the exact persistence
> mechanism.

- [ ] **Step 6: Implement `AuthController`**

```kotlin
package br.com.investlog.server.auth.rest.controllers

import br.com.investlog.server.auth.domain.services.AuthService
import br.com.investlog.server.auth.rest.payloads.LoginRequest
import br.com.investlog.server.auth.rest.payloads.SessionResponse
import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/auth")
class AuthController(private val authService: AuthService) {

    @PostMapping("/login")
    fun login(@RequestBody request: LoginRequest, servletRequest: HttpServletRequest): SessionResponse =
        authService.login(request, servletRequest)
}
```

- [ ] **Step 7: Map `InvalidCredentialsException` to 401**

Add to `GlobalExceptionHandler.kt` (new import `br.com.investlog.server.shared.exceptions.InvalidCredentialsException`
and `org.springframework.http.HttpStatus.UNAUTHORIZED`, plus this handler method next to
`handleNotFound`):

```kotlin
    @ExceptionHandler(InvalidCredentialsException::class)
    fun handleInvalidCredentials(ex: InvalidCredentialsException): ProblemDetail {

        val problemDetail = ProblemDetail.forStatusAndDetail(UNAUTHORIZED, ex.message ?: "Invalid credentials")
        problemDetail.setProperty("timestamp", Instant.now())

        return problemDetail
    }
```

- [ ] **Step 8: Run test to verify it passes**

Run: `cd server && ./gradlew test --tests "br.com.investlog.server.auth.rest.controllers.AuthControllerTest"`
Expected: PASS (3 tests)

- [ ] **Step 9: Run the full suite**

Run: `cd server && ./gradlew test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 10: Commit**

```bash
git add server/src/main/kotlin/br/com/investlog/server/auth server/src/main/kotlin/br/com/investlog/server/shared/exceptions/InvalidCredentialsException.kt server/src/main/kotlin/br/com/investlog/server/config/GlobalExceptionHandler.kt server/src/test/kotlin/br/com/investlog/server/auth
git commit -m "feat(server): add local login endpoint and session establishment"
```

---

## Task 5: Session endpoint, logout, and `CurrentUserProvider` via `SecurityContext`

**Files:**
- Modify: `server/src/main/kotlin/br/com/investlog/server/auth/rest/controllers/AuthController.kt`
- Modify: `server/src/main/kotlin/br/com/investlog/server/auth/domain/services/AuthService.kt`
- Modify: `server/src/main/kotlin/br/com/investlog/server/shared/security/CurrentUserProvider.kt`
- Test: `server/src/test/kotlin/br/com/investlog/server/auth/rest/controllers/AuthControllerTest.kt`

**Interfaces:**
- Produces: `GET /private/v1/auth/session` (200 + `SessionResponse` if authenticated, 401
  otherwise), `POST /private/v1/auth/logout` (invalidates the session); `CurrentUserProvider`
  now reads `SecurityContextHolder.getContext().authentication.principal as CurrentUser`
  instead of the fixed dev-user lookup.

- [ ] **Step 1: Add the failing tests**

Append to `AuthControllerTest.kt`:

```kotlin
    @Test
    @Order(4)
    fun `session reflects the cookie set by login, and logout clears it`() {
        val cookie = restTestClient.post()
            .uri("/private/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"email":"admin@admin.com","password":"admin"}""")
            .exchange()
            .expectStatus().isOk()
            .returnResult<SessionResponse>()
            .responseHeaders
            .getFirst("Set-Cookie")
            ?.substringBefore(";")
            ?: error("Login did not set a session cookie")

        restTestClient.get()
            .uri("/private/v1/auth/session")
            .header("Cookie", cookie)
            .exchange()
            .expectStatus().isOk()
            .returnResult<SessionResponse>()
            .responseBody
            .let { assertEquals("admin@admin.com", it?.email) }

        restTestClient.post()
            .uri("/private/v1/auth/logout")
            .header("Cookie", cookie)
            .exchange()
            .expectStatus().isOk()

        restTestClient.get()
            .uri("/private/v1/auth/session")
            .header("Cookie", cookie)
            .exchange()
            .expectStatus().isUnauthorized()
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd server && ./gradlew test --tests "br.com.investlog.server.auth.rest.controllers.AuthControllerTest"`
Expected: FAIL — no `/session` or `/logout` mappings yet.

- [ ] **Step 3: Extend `AuthService`**

Add these two methods to `AuthService.kt` (keep `login` as-is):

```kotlin
    fun currentSession(): SessionResponse {
        val user = SecurityContextHolder.getContext().authentication?.principal as? CurrentUser
            ?: throw InvalidCredentialsException("Not authenticated")
        return SessionResponse(name = user.name, email = user.email, role = user.role)
    }

    fun logout(servletRequest: HttpServletRequest) {
        servletRequest.getSession(false)?.invalidate()
        SecurityContextHolder.clearContext()
    }
```

Add the matching import: `br.com.investlog.server.shared.security.CurrentUser`.

- [ ] **Step 4: Extend `AuthController`**

Add to `AuthController.kt`:

```kotlin
    @GetMapping("/session")
    fun session(): SessionResponse = authService.currentSession()

    @PostMapping("/logout")
    fun logout(servletRequest: HttpServletRequest) = authService.logout(servletRequest)
```

Add the matching import: `org.springframework.web.bind.annotation.GetMapping`.

- [ ] **Step 5: Replace `CurrentUserProvider`**

Replace the full contents of `CurrentUserProvider.kt`:

```kotlin
package br.com.investlog.server.shared.security

import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

interface CurrentUserProvider {
    fun getCurrentUser(): CurrentUser
}

@Component
class SecurityContextCurrentUserProvider : CurrentUserProvider {

    override fun getCurrentUser(): CurrentUser =
        SecurityContextHolder.getContext().authentication?.principal as? CurrentUser
            ?: error("No authenticated user in the current security context")
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `cd server && ./gradlew test --tests "br.com.investlog.server.auth.rest.controllers.AuthControllerTest"`
Expected: PASS (4 tests)

- [ ] **Step 7: Run the full suite**

Run: `cd server && ./gradlew test`
Expected: BUILD SUCCESSFUL — `ProfileControllerTest` and friends still pass because the
filter chain still permits everything unauthenticated, and their underlying services
don't call `CurrentUserProvider` through an authenticated HTTP request in those tests
(they exercise `/profile` directly, which Task 6 has not locked down yet).

> If `ProfileControllerTest` (or others) now fail because they *do* call
> `CurrentUserProvider` internally and there is no `SecurityContext` on an unauthenticated
> request: this is expected to surface here rather than in Task 6. Fix it now, before
> tightening the filter chain, by leaving those controllers as the dependency boundary —
> do **not** add a global authenticated-by-default principal. Task 6 deliberately swaps
> `BaseIntegrationTest` to authenticate every test, which is the real fix; if you hit this
> failure, pull the `BaseIntegrationTest` change from Task 6 forward into this task instead
> of inventing a workaround.

- [ ] **Step 8: Commit**

```bash
git add server/src/main/kotlin/br/com/investlog/server/auth server/src/main/kotlin/br/com/investlog/server/shared/security/CurrentUserProvider.kt server/src/test/kotlin/br/com/investlog/server/auth
git commit -m "feat(server): add session/logout endpoints and resolve the current user from the security context"
```

---

## Task 6: Lock down `/private/**` and authenticate `BaseIntegrationTest`

This is the task that makes every other controller require a real session. It touches
`BaseIntegrationTest` once so none of the other ~10 existing test classes need editing.

**Files:**
- Modify: `server/src/main/kotlin/br/com/investlog/server/config/SecurityConfig.kt`
- Modify: `server/src/test/kotlin/br/com/investlog/server/BaseIntegrationTest.kt`

**Interfaces:**
- Consumes: `POST /private/v1/auth/login` (Task 4).
- Produces: every `/private/**` request except `/private/v1/auth/login` now requires an
  authenticated session; `BaseIntegrationTest` logs in as the seeded admin once per test
  class and exposes the resulting session cookie so subclasses' existing `restTestClient`
  calls keep working unmodified.

- [ ] **Step 1: Tighten `SecurityConfig`**

Replace the `securityFilterChain` bean in `SecurityConfig.kt`:

```kotlin
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http {
            csrf { disable() }
            authorizeHttpRequests {
                authorize("/private/v1/auth/login", permitAll)
                authorize(anyRequest, authenticated)
            }
        }
        return http.build()
    }
```

- [ ] **Step 2: Make `BaseIntegrationTest` authenticate and carry the cookie**

Replace the full contents of `BaseIntegrationTest.kt`:

```kotlin
package br.com.investlog.server

import br.com.investlog.server.BaseIntegrationTest.Companion.ACTIVE_PROFILE
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.client.RestTestClient

@AutoConfigureRestTestClient
@ActiveProfiles(ACTIVE_PROFILE)
@Import(value = [TestcontainersConfiguration::class])
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
abstract class BaseIntegrationTest internal constructor() {

    @Autowired
    private lateinit var rawRestTestClient: RestTestClient

    /** A [RestTestClient] that already carries an authenticated admin session cookie. */
    protected lateinit var restTestClient: RestTestClient

    @BeforeEach
    fun authenticateAsAdmin() {
        val cookie = rawRestTestClient.post()
            .uri("/private/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"email":"admin@admin.com","password":"admin"}""")
            .exchange()
            .expectStatus().isOk()
            .returnResult(String::class.java)
            .responseHeaders
            .getFirst("Set-Cookie")
            ?.substringBefore(";")
            ?: error("Login did not set a session cookie")

        restTestClient = rawRestTestClient.mutate()
            .defaultHeader("Cookie", cookie)
            .build()
    }

    companion object {
        const val ACTIVE_PROFILE = "test"
    }
}
```

> `AuthControllerTest` declares its own `restTestClient` field via `@Autowired` — that
> shadows the protected one from the base class for that file's purposes, so its
> unauthenticated-login assertions (Tasks 4/5) keep working unchanged. Every *other*
> existing test class (`ProfileControllerTest`, `WalletControllerTest`, etc.) does **not**
> declare its own `restTestClient` field, so it now inherits the pre-authenticated one
> from `BaseIntegrationTest` automatically — no per-file edits needed.
>
> If `RestTestClient` has no `.mutate()`/`.defaultHeader()` API in this Spring Boot
> version, the compiler error will say so plainly — fall back to building a fresh
> `RestTestClient.bindToServer(...)` with a default header, using whatever base URL the
> autoconfigured instance was bound to (check `RestTestClientAutoConfiguration` if needed).
> Either way, the goal is unchanged: `restTestClient` in the base class must attach the
> admin's session cookie to every request.

- [ ] **Step 3: Run the full suite**

Run: `cd server && ./gradlew test`
Expected: BUILD SUCCESSFUL. If a specific existing test class fails because it asserts on
something the admin-as-current-user change affects (unlikely, since the seeded user's
domain data didn't change — only its auth columns did), fix that assertion, not the
security model.

- [ ] **Step 4: Commit**

```bash
git add server/src/main/kotlin/br/com/investlog/server/config/SecurityConfig.kt server/src/test/kotlin/br/com/investlog/server/BaseIntegrationTest.kt
git commit -m "feat(server): require an authenticated session for all private endpoints"
```

---

## Task 7: Client — auth API + Pinia store

**Files:**
- Create: `client/src/api/auth.ts`
- Create: `client/src/stores/auth.ts`
- Modify: `client/src/types.ts`
- Test: `client/src/stores/auth.test.ts`

**Interfaces:**
- Produces: `authApi.login(email, password): Promise<SessionResponse>`,
  `authApi.logout(): Promise<void>`, `authApi.fetchSession(): Promise<SessionResponse>`
  (rejects with a 401 axios error if unauthenticated); `useAuthStore()` exposing
  `session: Ref<SessionResponse | null>`, `loading: Ref<boolean>`, `login(email,
  password)`, `logout()`, `restoreSession(): Promise<void>` (silently leaves `session`
  null on 401, used at app boot).

- [ ] **Step 1: Add the `SessionResponse` type**

In `client/src/types.ts`, add:

```typescript
export type UserRole = 'ADMIN' | 'USER'

export interface SessionResponse {
  name: string
  email: string
  role: UserRole
}
```

- [ ] **Step 2: Write `authApi`**

```typescript
import { apiClient } from './client'
import type { SessionResponse } from '@/types'

export const authApi = {
  login(email: string, password: string): Promise<SessionResponse> {
    return apiClient.post<SessionResponse>('/auth/login', { email, password }).then((response) => response.data)
  },
  logout(): Promise<void> {
    return apiClient.post('/auth/logout').then(() => undefined)
  },
  fetchSession(): Promise<SessionResponse> {
    return apiClient.get<SessionResponse>('/auth/session').then((response) => response.data)
  },
}
```

- [ ] **Step 3: Write the failing store test**

```typescript
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from './auth'
import { authApi } from '@/api/auth'

vi.mock('@/api/auth', () => ({
  authApi: {
    login: vi.fn(),
    logout: vi.fn(),
    fetchSession: vi.fn(),
  },
}))

describe('auth store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('sets the session after a successful login', async () => {
    vi.mocked(authApi.login).mockResolvedValue({ name: 'Administrador', email: 'admin@admin.com', role: 'ADMIN' })

    const store = useAuthStore()
    await store.login('admin@admin.com', 'admin')

    expect(store.session).toEqual({ name: 'Administrador', email: 'admin@admin.com', role: 'ADMIN' })
  })

  it('clears the session on logout', async () => {
    vi.mocked(authApi.login).mockResolvedValue({ name: 'Administrador', email: 'admin@admin.com', role: 'ADMIN' })
    vi.mocked(authApi.logout).mockResolvedValue(undefined)

    const store = useAuthStore()
    await store.login('admin@admin.com', 'admin')
    await store.logout()

    expect(store.session).toBeNull()
  })

  it('restoreSession leaves the session null when unauthenticated', async () => {
    vi.mocked(authApi.fetchSession).mockRejectedValue({ response: { status: 401 } })

    const store = useAuthStore()
    await store.restoreSession()

    expect(store.session).toBeNull()
  })

  it('restoreSession populates the session when authenticated', async () => {
    vi.mocked(authApi.fetchSession).mockResolvedValue({ name: 'Administrador', email: 'admin@admin.com', role: 'ADMIN' })

    const store = useAuthStore()
    await store.restoreSession()

    expect(store.session?.email).toBe('admin@admin.com')
  })
})
```

- [ ] **Step 4: Run test to verify it fails**

Run: `cd client && npm run test -- src/stores/auth.test.ts`
Expected: FAIL — `./auth` (the store) doesn't exist yet.

- [ ] **Step 5: Implement the store**

```typescript
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { authApi } from '@/api/auth'
import type { SessionResponse } from '@/types'

export const useAuthStore = defineStore('auth', () => {
  const session = ref<SessionResponse | null>(null)
  const loading = ref(false)

  async function login(email: string, password: string) {
    session.value = await authApi.login(email, password)
  }

  async function logout() {
    await authApi.logout()
    session.value = null
  }

  async function restoreSession() {
    loading.value = true
    try {
      session.value = await authApi.fetchSession()
    } catch {
      session.value = null
    } finally {
      loading.value = false
    }
  }

  return { session, loading, login, logout, restoreSession }
})
```

- [ ] **Step 6: Run test to verify it passes**

Run: `cd client && npm run test -- src/stores/auth.test.ts`
Expected: PASS (4 tests)

- [ ] **Step 7: Commit**

```bash
git add client/src/api/auth.ts client/src/stores/auth.ts client/src/stores/auth.test.ts client/src/types.ts
git commit -m "feat(client): add the auth API client and Pinia store"
```

---

## Task 8: Client — `LoginView.vue` + ported `.auth-*` styles + `LogoMark`

**Files:**
- Create: `client/src/components/icons/LogoMark.vue`
- Create: `client/src/views/LoginView.vue`
- Modify: `client/src/assets/styles.css`
- Test: `client/src/views/LoginView.test.ts`

**Interfaces:**
- Consumes: `useAuthStore()` (Task 7).
- Produces: `<LogoMark :size="19" />` component (the "ascending ledger" brand mark);
  `LoginView` emits nothing — on successful login it calls `router.push({ name:
  'overview' })` itself.

- [ ] **Step 1: Port the `LogoMark` SVG**

```vue
<script setup lang="ts">
defineProps<{ size?: number }>()
</script>

<template>
  <svg :width="size ?? 24" :height="size ?? 24" viewBox="0 0 24 24" fill="currentColor">
    <rect x="3.6" y="17.7" width="16.8" height="2.2" rx="1.1" opacity=".5" />
    <rect x="5.4" y="12.4" width="3.1" height="5.6" rx="1.2" />
    <rect x="10.45" y="8.2" width="3.1" height="9.8" rx="1.2" />
    <rect x="15.5" y="3.6" width="3.1" height="14.4" rx="1.2" />
    <circle cx="17.05" cy="3.6" r="2.05" />
  </svg>
</template>
```

- [ ] **Step 2: Port the `.auth-*` CSS block**

Append to `client/src/assets/styles.css` (verbatim from the design source — paste at the
end of the file):

```css
/* ===== Auth / login ===== */
.auth-root{display:flex;height:100vh;overflow:hidden;background:var(--bg);}
.auth-aside{position:relative;width:46%;max-width:540px;flex-shrink:0;overflow:hidden;
  display:flex;flex-direction:column;padding:44px 48px;color:#fff;
  background:linear-gradient(160deg,var(--primary) 0%,var(--primary-d) 78%);}
.auth-aside-top{flex-shrink:0;}
.auth-logo{display:flex;align-items:center;gap:11px;}
.auth-logo-mark{width:42px;height:42px;border-radius:12px;background:rgba(255,255,255,.16);
  display:grid;place-items:center;backdrop-filter:blur(4px);}
.auth-logo-name{font-size:21px;font-weight:700;letter-spacing:-.02em;}
.auth-logo-name b{font-weight:800;}
.auth-aside-mid{flex:1;display:flex;flex-direction:column;justify-content:center;gap:18px;position:relative;z-index:2;}
.auth-headline{font-size:38px;line-height:1.12;font-weight:800;letter-spacing:-.035em;margin:0;}
.auth-tagline{font-size:15px;line-height:1.6;color:rgba(255,255,255,.85);max-width:380px;margin:0;}
.auth-points{list-style:none;margin:0 0 4px;padding:0;display:flex;flex-direction:column;gap:11px;position:relative;z-index:2;}
.auth-points li{display:flex;align-items:center;gap:11px;font-size:14px;color:rgba(255,255,255,.9);font-weight:500;}
.ap-dot{width:7px;height:7px;border-radius:50%;background:#fff;flex-shrink:0;box-shadow:0 0 0 4px rgba(255,255,255,.18);}
.auth-deco{position:absolute;right:-10px;bottom:-2px;display:flex;align-items:flex-end;gap:10px;height:160px;opacity:.16;z-index:1;}
.auth-deco span{width:22px;border-radius:6px 6px 0 0;background:#fff;}
.auth-main{flex:1;display:flex;align-items:center;justify-content:center;padding:32px 24px;overflow-y:auto;}
.auth-card{width:100%;max-width:392px;background:var(--surface);border:1px solid var(--border);
  border-radius:16px;box-shadow:0 10px 40px rgba(15,23,42,.08);padding:32px 30px;}
[data-theme="dark"] .auth-card{box-shadow:0 12px 44px rgba(0,0,0,.32);}
.auth-card-brand{display:none;align-items:center;gap:10px;margin-bottom:22px;}
.auth-card-brand .brand-name{font-size:18px;font-weight:700;letter-spacing:-.02em;}
.auth-card-brand .brand-name b{color:var(--primary);}
.auth-head{margin-bottom:22px;}
.auth-title{font-size:23px;font-weight:800;letter-spacing:-.03em;margin:0;}
.auth-sub{font-size:13.5px;color:var(--text-2);margin:6px 0 0;line-height:1.5;}
.auth-pw-row{display:flex;align-items:baseline;justify-content:space-between;gap:10px;}
.auth-forgot{font-size:12px;}
.auth-submit{margin-top:4px;height:44px;font-size:14.5px;}
.auth-divider{display:flex;align-items:center;gap:12px;color:var(--text-muted);font-size:12px;font-weight:600;margin:2px 0;}
.auth-divider::before,.auth-divider::after{content:"";height:1px;background:var(--border);flex:1;}
.auth-g{font-weight:800;font-size:15px;color:#4285f4;}
.auth-foot{text-align:center;font-size:13px;color:var(--text-2);margin:22px 0 0;}
.auth-error{font-size:13px;color:var(--down);background:var(--down-soft);border-radius:8px;padding:10px 12px;margin-bottom:4px;}
@media(max-width:860px){
  .auth-aside{display:none;}
  .auth-card-brand{display:flex;}
}
```

> Note: this plan's scope is login-only (no signup tab yet — that's Phase 3), so the
> `.auth-seg`/`.auth-seg-btn` rules from the design source are intentionally **not**
> ported here; add them in the Phase 3 plan alongside the signup tab itself. The
> `.auth-error` rule above is new (not in the design source) for surfacing the "invalid
> credentials" message.

- [ ] **Step 3: Write the failing component test**

```typescript
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import LoginView from './LoginView.vue'
import { useAuthStore } from '@/stores/auth'

vi.mock('@/api/auth', () => ({
  authApi: { login: vi.fn(), logout: vi.fn(), fetchSession: vi.fn() },
}))

describe('LoginView', () => {
  let router: ReturnType<typeof createRouter>

  beforeEach(() => {
    setActivePinia(createPinia())
    router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/', name: 'overview', component: { template: '<div />' } },
        { path: '/login', name: 'login', component: LoginView },
      ],
    })
  })

  it('logs in and navigates to overview on submit', async () => {
    const store = useAuthStore()
    const loginSpy = vi.spyOn(store, 'login').mockResolvedValue()
    router.push('/login')
    await router.isReady()

    const wrapper = mount(LoginView, { global: { plugins: [router] } })
    await wrapper.find('input[type="email"]').setValue('admin@admin.com')
    await wrapper.find('input[type="password"]').setValue('admin')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(loginSpy).toHaveBeenCalledWith('admin@admin.com', 'admin')
    expect(router.currentRoute.value.name).toBe('overview')
  })
})

function flushPromises() {
  return new Promise((resolve) => setTimeout(resolve, 0))
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `cd client && npm run test -- src/views/LoginView.test.ts`
Expected: FAIL — `./LoginView.vue` doesn't exist yet.

- [ ] **Step 5: Implement `LoginView.vue`**

```vue
<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import LogoMark from '@/components/icons/LogoMark.vue'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()

const email = ref('')
const password = ref('')
const error = ref('')
const submitting = ref(false)

async function submit() {
  error.value = ''
  submitting.value = true
  try {
    await auth.login(email.value, password.value)
    await router.push({ name: 'overview' })
  } catch {
    error.value = 'E-mail ou senha inválidos.'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="auth-root">
    <aside class="auth-aside">
      <div class="auth-aside-top">
        <div class="auth-logo">
          <span class="auth-logo-mark"><LogoMark :size="26" /></span>
          <span class="auth-logo-name">Invest<b>Log</b></span>
        </div>
      </div>
      <div class="auth-aside-mid">
        <h2 class="auth-headline">Seu diário de<br />investimentos,<br />sempre em dia.</h2>
        <p class="auth-tagline">
          Registre aportes, acompanhe carteiras em várias moedas e veja seu patrimônio
          evoluir — tudo em um só lugar.
        </p>
      </div>
      <ul class="auth-points">
        <li><span class="ap-dot" />Carteiras multi-moeda consolidadas</li>
        <li><span class="ap-dot" />Resultado por ativo, tipo e período</li>
        <li><span class="ap-dot" />Histórico completo de cada aporte</li>
      </ul>
      <div class="auth-deco" aria-hidden="true">
        <span style="height: 34%" /><span style="height: 52%" />
        <span style="height: 46%" /><span style="height: 68%" />
        <span style="height: 60%" /><span style="height: 84%" />
        <span style="height: 76%" /><span style="height: 100%" />
      </div>
    </aside>

    <main class="auth-main">
      <form class="auth-card" @submit.prevent="submit">
        <div class="auth-card-brand">
          <span class="brand-mark"><LogoMark :size="20" /></span>
          <span class="brand-name">Invest<b>Log</b></span>
        </div>

        <div class="auth-head">
          <h1 class="auth-title">Bem-vindo de volta</h1>
          <p class="auth-sub">Entre para acompanhar seus investimentos.</p>
        </div>

        <p v-if="error" class="auth-error">{{ error }}</p>

        <div class="form-stack">
          <div class="field">
            <label class="field-label">E-mail</label>
            <input v-model="email" class="inp" type="email" placeholder="voce@email.com" required />
          </div>
          <div class="field">
            <div class="auth-pw-row">
              <label class="field-label">Senha</label>
            </div>
            <input v-model="password" class="inp" type="password" placeholder="••••••••" required />
          </div>

          <button class="btn btn-primary btn-md btn-full auth-submit" type="submit" :disabled="submitting">
            Entrar
          </button>
        </div>
      </form>
    </main>
  </div>
</template>
```

- [ ] **Step 6: Run test to verify it passes**

Run: `cd client && npm run test -- src/views/LoginView.test.ts`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add client/src/components/icons/LogoMark.vue client/src/views/LoginView.vue client/src/views/LoginView.test.ts client/src/assets/styles.css
git commit -m "feat(client): add the login view, brand mark, and ported auth styles"
```

---

## Task 9: Router guard, `App.vue` wiring, navbar logout/logo

**Files:**
- Modify: `client/src/router/index.ts`
- Modify: `client/src/App.vue`
- Modify: `client/src/components/layout/TheNavbar.vue`
- Modify: `client/src/api/client.ts`
- Test: `client/src/router/index.test.ts`

**Interfaces:**
- Consumes: `useAuthStore()` (Task 7).
- Produces: unauthenticated navigation to any route other than `/login` redirects to
  `/login`; a successful axios 401 response anywhere triggers the same redirect (covers
  session expiry mid-session); `TheNavbar` gets a logout icon button and the `LogoMark`
  brand mark.

- [ ] **Step 1: Write the failing router test**

```typescript
import { describe, expect, it, beforeEach, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

vi.mock('@/api/auth', () => ({
  authApi: { login: vi.fn(), logout: vi.fn(), fetchSession: vi.fn() },
}))

describe('router auth guard', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('redirects to /login when there is no session', async () => {
    const { router } = await import('./index')
    router.push('/wallets')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('login')
  })

  it('allows navigation when a session exists', async () => {
    const auth = useAuthStore()
    auth.session = { name: 'Administrador', email: 'admin@admin.com', role: 'ADMIN' }

    const { router } = await import('./index')
    router.push('/wallets')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('wallets')
  })
})
```

> This test uses dynamic `import('./index')` per case so each test gets a fresh router
> module instance (the guard closes over the Pinia store at import time); if your Vitest
> config caches modules across `it()` blocks within a file, add `vi.resetModules()` at the
> top of each `it()` before the dynamic import.

- [ ] **Step 2: Run test to verify it fails**

Run: `cd client && npm run test -- src/router/index.test.ts`
Expected: FAIL — no guard exists yet, both navigations resolve to their literal routes.

- [ ] **Step 3: Add the guard to the router**

Replace the full contents of `client/src/router/index.ts`:

```typescript
import { createRouter, createWebHistory } from 'vue-router'

import OverviewView from '@/views/OverviewView.vue'
import WalletsView from '@/views/WalletsView.vue'
import InvestmentsView from '@/views/InvestmentsView.vue'
import SettingsView from '@/views/SettingsView.vue'
import LoginView from '@/views/LoginView.vue'
import { useAuthStore } from '@/stores/auth'

export const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', redirect: { name: 'overview' } },
    { path: '/login', name: 'login', component: LoginView },
    { path: '/overview', name: 'overview', component: OverviewView },
    { path: '/wallets', name: 'wallets', component: WalletsView },
    // ?filter=stocks|crypto|funds preselects the segmented tab
    { path: '/investments', name: 'investments', component: InvestmentsView },
    { path: '/settings', name: 'settings', component: SettingsView },
  ],
  scrollBehavior() {
    return { top: 0 }
  },
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.name !== 'login' && !auth.session) {
    return { name: 'login' }
  }
  if (to.name === 'login' && auth.session) {
    return { name: 'overview' }
  }
  return true
})
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd client && npm run test -- src/router/index.test.ts`
Expected: PASS

- [ ] **Step 5: Wire session restoration and the 401 redirect into the app shell**

In `client/src/api/client.ts`, replace the error interceptor's body to also redirect on
401 (keep the existing toast behavior for every other error):

```typescript
import axios from 'axios'
import { ToastProgrammatic } from 'buefy'
import { router } from '@/router'

const toast = new ToastProgrammatic()

export const apiClient = axios.create({
  baseURL: '/private/v1',
  headers: { 'Content-Type': 'application/json' },
})

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401 && router.currentRoute.value.name !== 'login') {
      router.push({ name: 'login' })
      return Promise.reject(error)
    }
    const message: string =
      error.response?.data?.detail ??
      error.response?.data?.message ??
      'Erro ao comunicar com o servidor.'
    toast.open({ message, type: 'is-danger', duration: 4000 })
    return Promise.reject(error)
  },
)
```

In `client/src/App.vue`, restore the session on mount before anything else renders. Add
to the `<script setup>` block (after the existing imports/state):

```typescript
import { onMounted } from 'vue'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
onMounted(() => {
  auth.restoreSession()
})
```

- [ ] **Step 6: Add logout + brand mark to `TheNavbar.vue`**

In `client/src/components/layout/TheNavbar.vue`, add the import and store:

```typescript
import LogoMark from '@/components/icons/LogoMark.vue'
import { useAuthStore } from '@/stores/auth'
import { useRouter } from 'vue-router'

const auth = useAuthStore()
const router = useRouter()

async function logout() {
  await auth.logout()
  router.push({ name: 'login' })
}
```

Replace the brand `<span class="brand-mark">` icon and add a logout button at the end of
`.navbar-user`:

```html
      <RouterLink to="/" class="brand">
        <span class="brand-mark"><LogoMark :size="19" /></span>
        <span class="brand-name">Invest<b>Log</b></span>
      </RouterLink>
```

```html
      <div class="navbar-user">
        <Avatar :initials="profile ? initials(profile.name) : '?'" />
        <div class="nu-meta">
          <div class="nu-name">{{ profile?.name ?? '...' }}</div>
          <div class="nu-sub">{{ profile?.email ?? '' }}</div>
        </div>
        <button type="button" class="icon-btn" aria-label="Sair" title="Sair" @click="logout">
          <b-icon icon="logout" size="is-small" />
        </button>
      </div>
```

- [ ] **Step 7: Run the full client test suite and the build**

Run: `cd client && npm run test`
Expected: all tests pass.

Run: `cd client && npm run build`
Expected: BUILD SUCCESSFUL (no TypeScript errors, no unused imports).

- [ ] **Step 8: Commit**

```bash
git add client/src/router/index.ts client/src/router/index.test.ts client/src/App.vue client/src/components/layout/TheNavbar.vue client/src/api/client.ts
git commit -m "feat(client): guard routes behind a session, restore it on boot, add logout"
```

---

## Task 10: README + `.env.example` documentation

**Files:**
- Modify: `README.md`
- Modify: `.env.example`

**Interfaces:** none (documentation only).

- [ ] **Step 1: Document the default admin login**

In `README.md`, in the `## Run locally` section, after the line "Open
**http://localhost:8081**.", add:

```markdown
Log in with the seeded admin account: `admin@admin.com` / `admin` (from
`ADMIN_DEFAULT_PASSWORD`, see below) — **change this password immediately** in a real
deployment.
```

- [ ] **Step 2: Add the new row to the Configuration table**

In the `## Configuration` table in `README.md`, add a row:

```markdown
| `ADMIN_DEFAULT_PASSWORD` | `admin` | Password set on the seeded admin account on first boot |
```

- [ ] **Step 3: Confirm `.env.example` has the variable**

Verify the line added in Task 3 Step 7 is present:

```
ADMIN_DEFAULT_PASSWORD=admin
```

- [ ] **Step 4: Commit**

```bash
git add README.md .env.example
git commit -m "docs: document the seeded admin login and ADMIN_DEFAULT_PASSWORD"
```

---

## Done state for this plan

After Task 10: the app requires a local login; the seeded admin (`admin@admin.com`) can
log in and reach every screen; logging out and refreshing returns to `/login`; all
existing server and client tests pass under the new security model. TOTP enforcement,
self-registration/approval, the local-users admin page, and Google OAuth2 are deliberately
not yet present — those are Phases 2–4, each getting their own plan once this one's PR
(against `feature/authentication`) is reviewed and merged.
