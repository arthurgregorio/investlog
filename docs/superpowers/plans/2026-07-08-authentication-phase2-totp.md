# Authentication Phase 2 — Mandatory TOTP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Every local user must enroll in TOTP (authenticator-app) two-factor authentication on
their first login, and every subsequent local login requires a valid 6-digit code.

**Architecture:** `dev.samstevens.totp` generates a per-user Base32 secret and verifies submitted
codes server-side; the QR code is rendered to a PNG data URI server-side too (via the library's
bundled ZXing generator), so the client just needs an `<img>` tag — no new client dependency.
`POST /auth/login` gains an optional `totpCode` field and now returns one of three outcomes
(session established / enrollment required / code required), never issuing a session until a
valid code has been supplied. Enrollment and verification are their own endpoints, both re-
verifying the password (no partial/pre-authenticated session is ever created — matches "the
response signals needs enrollment instead of a session" in the design spec).

**Tech Stack:** `dev.samstevens.totp:totp:1.7.1` (+ `com.google.zxing:core`/`javase:3.5.4`,
pulled in explicitly since the QR PNG generator needs ZXing's javase `MatrixToImageWriter`,
which is not guaranteed to be a transitive compile dependency of the totp library).

## Global Constraints

- No abbreviated names — full descriptive names everywhere (server: `server/CLAUDE.md`; client:
  `client/CLAUDE.md`).
- Never edit an existing Liquibase changelog file. **Not needed this phase** — Phase 1's
  `28-1000-add-auth-columns-to-users.xml` already added `totp_secret TEXT` (nullable) and
  `totp_enabled BOOLEAN NOT NULL DEFAULT false` to `system.users`; no new migration required.
- Per the repo's root `CLAUDE.md`: server and client changes go in **separate PRs**, each
  targeting the `feature/authentication` branch (not `main`).
  - Server tasks (1-4) go on a new branch `feature/auth-phase2-totp-server`, branched from
    `feature/authentication`.
  - Client tasks (5-6) go on a new branch `feature/auth-phase2-totp-client`, branched from
    `feature/authentication`. Client tasks only depend on the *contracts* defined in this plan
    (mocked in tests, exactly like Phase 1's `LoginView.test.ts`/`auth.test.ts`), so they can be
    implemented before, after, or in parallel with the server tasks.
  - Both PRs should reference `Refs #2` (the Phase 2 tracking issue) in their description; close
    issue #2 manually once both have merged.
- TOTP parameters: SHA1 algorithm, 6 digits, 30-second period (the RFC 6238 / Google Authenticator
  / Microsoft Authenticator compatible defaults).
- No backup codes (explicit out-of-scope in the design spec — device-loss recovery is an admin
  reset).
- **Scope change from issue #2 as originally filed:** `PATCH /users/{id}/totp-reset` is deferred
  to Phase 3. There is no way to address a user by id from the client until Phase 3's "Usuários
  locais" admin page exists to list users, and issue #3 already anticipated this
  ("delegated from Phase 2 endpoint if already done there"). After this plan is approved, update
  issue #2 to remove the totp-reset bullet and issue #3 to own it outright (no longer
  conditional).

## Design decisions (read before implementing)

**Why enroll/verify re-send the password instead of using a session:** the design spec is
explicit that a user who hasn't enrolled yet gets a response, not a session
("if totp_enabled = false, the response signals 'needs enrollment' instead of a session"). Rather
than inventing a partial/pre-authenticated Spring Security session (extra complexity: a new
authority, extra `authorizeHttpRequests` rules, and a "what if the user reloads mid-enrollment"
edge case), `POST /auth/totp/enroll` and `POST /auth/totp/verify` both take `{email, password}`
and re-verify credentials, exactly like `/auth/login` does. This is simpler to reason about,
simpler to test, and has no session-state edge cases. The client already has `email`/`password`
in memory at this point (the user just typed them), so this costs nothing in UX.

**The three-step login flow:**
1. User submits email + password to `POST /auth/login` (no `totpCode`).
2. Server verifies the password. If `totp_enabled == false` → **202** `{"status":"needs_enrollment"}`,
   no session. If `totp_enabled == true` → falls through to step 3's code check with no code
   present → **401** `{"error":"totp_required"}`.
3a. (First-time path) Client calls `POST /auth/totp/enroll` with `{email, password}` → gets back
   `{secretKey, qrCodeDataUri}` → shows the QR code + a 6-digit input → client calls
   `POST /auth/totp/verify` with `{email, password, code}` → on success, `totp_enabled` flips to
   `true` and a session is established (same response shape as a successful login).
3b. (Returning-user path) Client shows a 6-digit input and resubmits `POST /auth/login` with
   `{email, password, totpCode}` → on a valid code, session established; on an invalid code,
   **401** `{"error":"invalid_totp_code"}` (client keeps showing the code input); on a *missing*
   code (shouldn't happen from the UI, but the server enforces it independently) **401**
   `{"error":"totp_required"}`.

**Distinguishing error responses:** `InvalidCredentialsException` (wrong email/password) and the
two new TOTP exceptions all map to HTTP 401 via `ProblemDetail`, but the two new ones set an
`error` custom property (`"totp_required"` / `"invalid_totp_code"`) so the client can tell "your
password was wrong, start over" from "your password was fine, now enter/retry your code" without
leaking which case applies to an *unauthenticated* attacker (both still return 401, only the
body's `error` field differs, and that field is only meaningful once the password has already
been accepted).

## File Structure

**Server** (`br.com.investlog.server`):
- `auth/domain/services/TotpService.kt` — new. Wraps `dev.samstevens.totp`: generate a secret,
  verify a code, render a QR code data URI.
- `auth/domain/services/AuthService.kt` — modified. Login now checks `totpEnabled` and gates on
  a code; adds `enrollTotp`/`verifyTotp`; shared `verifyCredentials`/`establishSession` helpers
  extracted to avoid duplicating the password check and session-establishment logic three times.
- `auth/rest/controllers/AuthController.kt` — modified. `login` returns `ResponseEntity<Any>`
  (200 session or 202 enrollment-required); two new endpoints for enroll/verify.
- `auth/rest/payloads/` — `LoginRequest.kt` modified (add `totpCode`); four new files:
  `TotpEnrollRequest.kt`, `TotpEnrollResponse.kt`, `TotpVerifyRequest.kt`,
  `TotpEnrollmentRequiredResponse.kt`.
- `shared/exceptions/` — three new files: `TotpRequiredException.kt`, `InvalidTotpCodeException.kt`,
  `TotpAlreadyEnabledException.kt`. The last one guards `enrollTotp`: without it, anyone who knows
  a user's password (but not their authenticator device) could call `/auth/totp/enroll` again,
  overwrite the stored secret, and complete `/auth/totp/verify` themselves — defeating the entire
  point of 2FA and locking the real user out. Enrollment must be rejected once `totp_enabled` is
  already `true`; only an admin TOTP reset (Phase 3) can clear it back to unenrolled.
- `shared/security/CurrentUser.kt` — modified, add `totpEnabled: Boolean`.
- `shared/security/UserRepository.kt` — modified, add `findTotpSecretByEmail`,
  `updateTotpSecret`, `enableTotp`; map `totpEnabled` in `toCurrentUser()`.
- `config/SecurityConfig.kt` — modified, permit the two new TOTP endpoints unauthenticated.
- `config/GlobalExceptionHandler.kt` — modified, map the two new exceptions.
- `build.gradle.kts` — modified, add the TOTP + ZXing dependencies.
- Tests: `TotpServiceTest.kt` (new, plain unit test), `UserRepositoryTest.kt` (modified, append
  two tests), `AuthControllerTest.kt` (modified, full rewrite of the login-flow tests plus new
  enroll/verify tests), `TestcontainersConfiguration.kt` (modified, fix the shared admin-login
  test interceptor to complete the enroll+verify dance).

**Client** (`client/src`):
- `types.ts` — modified, add `TotpEnrollResponse`.
- `api/auth.ts` — modified, `login` now returns a `LoginOutcome` discriminated union instead of a
  raw `SessionResponse`; add `enroll`/`verify`.
- `stores/auth.ts` — modified, `login` returns a `LoginStatus` string; add `enrollTotp`/
  `verifyTotp` actions.
- `stores/auth.test.ts` — modified, update the login mock shape, add enroll/verify coverage.
- `views/LoginView.vue` — modified, three-step form (credentials / QR enrollment / code entry).
- `views/LoginView.test.ts` — modified, cover all three steps.
- `assets/styles.css` — modified, add `.auth-totp-qr`.
- `api/client.ts` — modified, suppress the global error toast for the two TOTP step-transition
  401s (`totp_required`/`invalid_totp_code`) so they don't flash a red toast on every login for
  an already-enrolled user.

---

## Task 1: TOTP dependencies, `TotpService`, and domain plumbing

**Branch:** `feature/auth-phase2-totp-server` (create from `feature/authentication`)

**Files:**
- Modify: `server/build.gradle.kts`
- Create: `server/src/main/kotlin/br/com/investlog/server/auth/domain/services/TotpService.kt`
- Modify: `server/src/main/kotlin/br/com/investlog/server/shared/security/CurrentUser.kt`
- Modify: `server/src/main/kotlin/br/com/investlog/server/shared/security/UserRepository.kt`
- Test: `server/src/test/kotlin/br/com/investlog/server/auth/domain/services/TotpServiceTest.kt`
- Test: `server/src/test/kotlin/br/com/investlog/server/shared/security/UserRepositoryTest.kt`

**Interfaces:**
- Produces: `TotpService.generateSecret(): String`, `TotpService.isCodeValid(secret: String, code: String): Boolean`,
  `TotpService.qrCodeDataUri(email: String, secret: String): String` — consumed by Task 3.
- Produces: `CurrentUser.totpEnabled: Boolean` — consumed by Task 3.
- Produces: `UserRepository.findTotpSecretByEmail(email: String): String?`,
  `UserRepository.updateTotpSecret(userId: Long, secret: String)`,
  `UserRepository.enableTotp(userId: Long, secret: String)` — consumed by Task 3.

- [ ] **Step 1: Add the TOTP + ZXing dependencies**

In `server/build.gradle.kts`, in the `dependencies { }` block, add these three lines right after
the existing `implementation("org.springframework.boot:spring-boot-starter-security")` line:

```kotlin
    implementation("dev.samstevens.totp:totp:1.7.1")
    implementation("com.google.zxing:core:3.5.4")
    implementation("com.google.zxing:javase:3.5.4")
```

- [ ] **Step 2: Write the failing TotpService test**

Create `server/src/test/kotlin/br/com/investlog/server/auth/domain/services/TotpServiceTest.kt`:

```kotlin
package br.com.investlog.server.auth.domain.services

import dev.samstevens.totp.code.DefaultCodeGenerator
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TotpServiceTest {

    private val totpService = TotpService()

    @Test
    fun `accepts a code generated for the current time from the same secret`() {
        val secret = totpService.generateSecret()
        val validCode = DefaultCodeGenerator().generate(secret, System.currentTimeMillis() / 1000L / 30L)

        assertTrue(totpService.isCodeValid(secret, validCode))
    }

    @Test
    fun `rejects a code that does not match the secret`() {
        val secret = totpService.generateSecret()

        assertFalse(totpService.isCodeValid(secret, "000000"))
    }

    @Test
    fun `builds a data URI for the QR code image`() {
        val secret = totpService.generateSecret()

        val dataUri = totpService.qrCodeDataUri("user@example.com", secret)

        assertTrue(dataUri.startsWith("data:image/png;base64,"))
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `cd server && ./gradlew test --tests "br.com.investlog.server.auth.domain.services.TotpServiceTest"`
Expected: FAIL — `TotpService` does not exist yet (compilation error).

- [ ] **Step 4: Implement TotpService**

Create `server/src/main/kotlin/br/com/investlog/server/auth/domain/services/TotpService.kt`:

```kotlin
package br.com.investlog.server.auth.domain.services

import dev.samstevens.totp.code.CodeVerifier
import dev.samstevens.totp.code.DefaultCodeGenerator
import dev.samstevens.totp.code.DefaultCodeVerifier
import dev.samstevens.totp.code.HashingAlgorithm
import dev.samstevens.totp.qr.QrData
import dev.samstevens.totp.qr.QrGenerator
import dev.samstevens.totp.qr.ZxingPngQrGenerator
import dev.samstevens.totp.secret.DefaultSecretGenerator
import dev.samstevens.totp.secret.SecretGenerator
import dev.samstevens.totp.time.SystemTimeProvider
import dev.samstevens.totp.util.Utils.getDataUriForImage
import org.springframework.stereotype.Service

@Service
class TotpService {

    private val secretGenerator: SecretGenerator = DefaultSecretGenerator()
    private val codeVerifier: CodeVerifier = DefaultCodeVerifier(DefaultCodeGenerator(), SystemTimeProvider())
    private val qrGenerator: QrGenerator = ZxingPngQrGenerator()

    fun generateSecret(): String = secretGenerator.generate()

    fun isCodeValid(secret: String, code: String): Boolean = codeVerifier.isValidCode(secret, code)

    fun qrCodeDataUri(email: String, secret: String): String {
        val data = QrData.Builder()
            .label(email)
            .secret(secret)
            .issuer("InvestLog")
            .algorithm(HashingAlgorithm.SHA1)
            .digits(6)
            .period(30)
            .build()

        val imageData = qrGenerator.generate(data)
        return getDataUriForImage(imageData, qrGenerator.imageMimeType)
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd server && ./gradlew test --tests "br.com.investlog.server.auth.domain.services.TotpServiceTest"`
Expected: PASS (3 tests).

- [ ] **Step 6: Add `totpEnabled` to `CurrentUser`**

Replace the full contents of `server/src/main/kotlin/br/com/investlog/server/shared/security/CurrentUser.kt`:

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
    val totpEnabled: Boolean,
)
```

- [ ] **Step 7: Write the failing UserRepository tests**

Append these two tests inside the `UserRepositoryTest` class in
`server/src/test/kotlin/br/com/investlog/server/shared/security/UserRepositoryTest.kt`, right
after the existing `` `the admin bootstrap runner sets a non-null password hash` `` test (before
the closing `}` of the class):

```kotlin

    @Test
    fun `stores and retrieves a totp secret`() {
        val user = userRepository.findByEmail("admin@admin.com")!!
        userRepository.updateTotpSecret(user.id, "JBSWY3DPEHPK3PXP")

        assertEquals("JBSWY3DPEHPK3PXP", userRepository.findTotpSecretByEmail("admin@admin.com"))
    }

    @Test
    fun `enabling totp sets both the secret and the enabled flag`() {
        val user = userRepository.findByEmail("admin@admin.com")!!
        userRepository.enableTotp(user.id, "KRSXG5CTMVRXEZLU")

        val updated = userRepository.findByEmail("admin@admin.com")!!
        assertEquals(true, updated.totpEnabled)
        assertEquals("KRSXG5CTMVRXEZLU", userRepository.findTotpSecretByEmail("admin@admin.com"))
    }
```

- [ ] **Step 8: Run the tests to verify they fail**

Run: `cd server && ./gradlew test --tests "br.com.investlog.server.shared.security.UserRepositoryTest"`
Expected: FAIL — compilation error (`findTotpSecretByEmail`/`updateTotpSecret`/`enableTotp`/
`totpEnabled` don't exist yet).

- [ ] **Step 9: Implement the UserRepository changes**

Replace the full contents of
`server/src/main/kotlin/br/com/investlog/server/shared/security/UserRepository.kt`:

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

    fun findTotpSecretByEmail(email: String): String? {
        return dsl.select(USERS.TOTP_SECRET)
            .from(USERS)
            .where(USERS.EMAIL.eq(email))
            .fetchOne(USERS.TOTP_SECRET)
    }

    fun updateTotpSecret(userId: Long, secret: String) {
        dsl.update(USERS)
            .set(USERS.TOTP_SECRET, secret)
            .set(USERS.UPDATED_AT, OffsetDateTime.now())
            .where(USERS.ID.eq(userId))
            .execute()
    }

    fun enableTotp(userId: Long, secret: String) {
        dsl.update(USERS)
            .set(USERS.TOTP_SECRET, secret)
            .set(USERS.TOTP_ENABLED, true)
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
        totpEnabled = totpEnabled!!,
    )
}
```

- [ ] **Step 10: Run the tests to verify they pass**

Run: `cd server && ./gradlew test --tests "br.com.investlog.server.shared.security.UserRepositoryTest"`
Expected: PASS (5 tests).

- [ ] **Step 11: Run the full server suite to confirm nothing else broke**

Run: `cd server && ./gradlew test`
Expected: PASS, same count as before this task plus the 6 new tests (3 `TotpServiceTest` + 2 new
`UserRepositoryTest` cases; note `CurrentUser` gained a required constructor field, so double
check the build compiles — every other `CurrentUser(...)` call site is inside
`UserRepository.toCurrentUser()`, which this step already updated, so there should be no other
call sites to fix).

- [ ] **Step 12: Commit**

```bash
git add server/build.gradle.kts \
  server/src/main/kotlin/br/com/investlog/server/auth/domain/services/TotpService.kt \
  server/src/main/kotlin/br/com/investlog/server/shared/security/CurrentUser.kt \
  server/src/main/kotlin/br/com/investlog/server/shared/security/UserRepository.kt \
  server/src/test/kotlin/br/com/investlog/server/auth/domain/services/TotpServiceTest.kt \
  server/src/test/kotlin/br/com/investlog/server/shared/security/UserRepositoryTest.kt
git commit -m "feat(server): add the TOTP library, TotpService, and totp domain plumbing"
```

---

## Task 2: TOTP exceptions and their `GlobalExceptionHandler` mappings

**Files:**
- Create: `server/src/main/kotlin/br/com/investlog/server/shared/exceptions/TotpRequiredException.kt`
- Create: `server/src/main/kotlin/br/com/investlog/server/shared/exceptions/InvalidTotpCodeException.kt`
- Create: `server/src/main/kotlin/br/com/investlog/server/shared/exceptions/TotpAlreadyEnabledException.kt`
- Modify: `server/src/main/kotlin/br/com/investlog/server/config/GlobalExceptionHandler.kt`

**Interfaces:**
- Consumes: nothing from Task 1.
- Produces: `TotpRequiredException(message: String)`, `InvalidTotpCodeException(message: String)`,
  `TotpAlreadyEnabledException(message: String)` — consumed by Task 3's `AuthService`.

This task has no dedicated test file — the exception→response mapping is exercised end-to-end by
Task 3's `AuthControllerTest` cases (`login without a totp code is rejected`, etc.), following the
same pattern as the existing `InvalidCredentialsException` (which also has no standalone handler
test, only integration coverage via `AuthControllerTest`).

- [ ] **Step 1: Create the three exception classes**

Create `server/src/main/kotlin/br/com/investlog/server/shared/exceptions/TotpRequiredException.kt`:

```kotlin
package br.com.investlog.server.shared.exceptions

class TotpRequiredException(message: String) : RuntimeException(message)
```

Create `server/src/main/kotlin/br/com/investlog/server/shared/exceptions/InvalidTotpCodeException.kt`:

```kotlin
package br.com.investlog.server.shared.exceptions

class InvalidTotpCodeException(message: String) : RuntimeException(message)
```

Create `server/src/main/kotlin/br/com/investlog/server/shared/exceptions/TotpAlreadyEnabledException.kt`:

```kotlin
package br.com.investlog.server.shared.exceptions

class TotpAlreadyEnabledException(message: String) : RuntimeException(message)
```

- [ ] **Step 2: Add the handler mappings**

In `server/src/main/kotlin/br/com/investlog/server/config/GlobalExceptionHandler.kt`, add these
three imports alongside the existing `InvalidCredentialsException`/`NotFoundException` imports:

```kotlin
import br.com.investlog.server.shared.exceptions.InvalidTotpCodeException
import br.com.investlog.server.shared.exceptions.TotpAlreadyEnabledException
import br.com.investlog.server.shared.exceptions.TotpRequiredException
```

The 409 status constant (`HttpStatus.CONFLICT`) is already imported in this file (used by
`handleDataIntegrityViolation`) — no new import needed for it.

Then add these three handler methods right after the existing `handleInvalidCredentials` method:

```kotlin
    @ExceptionHandler(TotpRequiredException::class)
    fun handleTotpRequired(ex: TotpRequiredException): ProblemDetail {

        val problemDetail = ProblemDetail.forStatusAndDetail(UNAUTHORIZED, ex.message ?: "TOTP code required")
        problemDetail.setProperty("error", "totp_required")
        problemDetail.setProperty("timestamp", Instant.now())

        return problemDetail
    }

    @ExceptionHandler(InvalidTotpCodeException::class)
    fun handleInvalidTotpCode(ex: InvalidTotpCodeException): ProblemDetail {

        val problemDetail = ProblemDetail.forStatusAndDetail(UNAUTHORIZED, ex.message ?: "Invalid TOTP code")
        problemDetail.setProperty("error", "invalid_totp_code")
        problemDetail.setProperty("timestamp", Instant.now())

        return problemDetail
    }

    @ExceptionHandler(TotpAlreadyEnabledException::class)
    fun handleTotpAlreadyEnabled(ex: TotpAlreadyEnabledException): ProblemDetail {

        val problemDetail = ProblemDetail.forStatusAndDetail(CONFLICT, ex.message ?: "TOTP is already enabled")
        problemDetail.setProperty("timestamp", Instant.now())

        return problemDetail
    }
```

- [ ] **Step 3: Compile to confirm there are no errors**

Run: `cd server && ./gradlew compileKotlin compileTestKotlin`
Expected: BUILD SUCCESSFUL (these classes aren't exercised by any test until Task 3, so this step
is a compile-only sanity check, not a test run).

- [ ] **Step 4: Commit**

```bash
git add server/src/main/kotlin/br/com/investlog/server/shared/exceptions/TotpRequiredException.kt \
  server/src/main/kotlin/br/com/investlog/server/shared/exceptions/InvalidTotpCodeException.kt \
  server/src/main/kotlin/br/com/investlog/server/shared/exceptions/TotpAlreadyEnabledException.kt \
  server/src/main/kotlin/br/com/investlog/server/config/GlobalExceptionHandler.kt
git commit -m "feat(server): add TOTP-specific exceptions and their status-code mappings"
```

---

## Task 3: TOTP enroll/verify endpoints and the login TOTP gate

This is the core behavioral change. It **intentionally breaks** every other controller test class
that relies on `TestcontainersConfiguration`'s shared admin-login test interceptor (that
interceptor performs a plain login and expects an immediate 200 — after this task, the seeded
admin has `totp_enabled = false`, so that plain login now returns 202). This mirrors the Phase 1
Task 5→6 handoff (documented in `.superpowers/sdd/progress.md`): the very next task (Task 4)
fixes the interceptor and restores the full suite to green. Do not attempt to fix the interceptor
in this task — that is Task 4's sole job, so it gets its own isolated review.

**Files:**
- Modify: `server/src/main/kotlin/br/com/investlog/server/auth/rest/payloads/LoginRequest.kt`
- Create: `server/src/main/kotlin/br/com/investlog/server/auth/rest/payloads/TotpEnrollRequest.kt`
- Create: `server/src/main/kotlin/br/com/investlog/server/auth/rest/payloads/TotpEnrollResponse.kt`
- Create: `server/src/main/kotlin/br/com/investlog/server/auth/rest/payloads/TotpVerifyRequest.kt`
- Create: `server/src/main/kotlin/br/com/investlog/server/auth/rest/payloads/TotpEnrollmentRequiredResponse.kt`
- Modify: `server/src/main/kotlin/br/com/investlog/server/auth/domain/services/AuthService.kt`
- Modify: `server/src/main/kotlin/br/com/investlog/server/auth/rest/controllers/AuthController.kt`
- Modify: `server/src/main/kotlin/br/com/investlog/server/config/SecurityConfig.kt`
- Test: `server/src/test/kotlin/br/com/investlog/server/auth/rest/controllers/AuthControllerTest.kt`

**Interfaces:**
- Consumes: `TotpService` (Task 1), `UserRepository.findTotpSecretByEmail`/`updateTotpSecret`/
  `enableTotp` (Task 1), `CurrentUser.totpEnabled` (Task 1), `TotpRequiredException`/
  `InvalidTotpCodeException`/`TotpAlreadyEnabledException` (Task 2).
- Produces: `AuthService.enrollTotp(request: TotpEnrollRequest): TotpEnrollResponse`,
  `AuthService.verifyTotp(request: TotpVerifyRequest, servletRequest: HttpServletRequest, servletResponse: HttpServletResponse): SessionResponse`,
  `sealed interface LoginResult` with `Authenticated(session: SessionResponse)` and
  `EnrollmentRequired` variants — none of these are consumed by later tasks in this plan (the
  client only ever talks to the HTTP layer), listed for completeness.

- [ ] **Step 1: Write the failing tests — replace `AuthControllerTest.kt` in full**

Replace the full contents of
`server/src/test/kotlin/br/com/investlog/server/auth/rest/controllers/AuthControllerTest.kt`:

```kotlin
package br.com.investlog.server.auth.rest.controllers

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.auth.rest.payloads.SessionResponse
import br.com.investlog.server.auth.rest.payloads.TotpEnrollResponse
import dev.samstevens.totp.code.DefaultCodeGenerator
import org.junit.jupiter.api.Order
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.returnResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
    fun `enroll returns a secret and a QR code data URI for the admin`() {
        val response = restTestClient.post()
            .uri("/private/v1/auth/totp/enroll")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"email":"admin@admin.com","password":"admin"}""")
            .exchange()
            .expectStatus().isOk()
            .returnResult<TotpEnrollResponse>()
            .responseBody

        assertTrue(response!!.secretKey.isNotBlank())
        assertTrue(response.qrCodeDataUri.startsWith("data:image/png;base64,"))
    }

    @Test
    @Order(4)
    fun `enroll rejects the wrong password`() {
        restTestClient.post()
            .uri("/private/v1/auth/totp/enroll")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"email":"admin@admin.com","password":"wrong"}""")
            .exchange()
            .expectStatus().isUnauthorized()
    }

    @Test
    @Order(5)
    fun `verify rejects an incorrect code, leaving totp disabled`() {
        restTestClient.post()
            .uri("/private/v1/auth/totp/verify")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"email":"admin@admin.com","password":"admin","code":"000000"}""")
            .exchange()
            .expectStatus().isUnauthorized()
    }

    @Test
    @Order(6)
    fun `login returns needs_enrollment while totp is not yet enabled`() {
        restTestClient.post()
            .uri("/private/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"email":"admin@admin.com","password":"admin"}""")
            .exchange()
            .expectStatus().isEqualTo(202)
    }

    @Test
    @Order(7)
    fun `completing verification with the correct code enables totp and establishes a session`() {
        val secret = restTestClient.post()
            .uri("/private/v1/auth/totp/enroll")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"email":"admin@admin.com","password":"admin"}""")
            .exchange()
            .expectStatus().isOk()
            .returnResult<TotpEnrollResponse>()
            .responseBody
            ?.secretKey
            ?: error("Enroll did not return a secret")

        adminTotpSecret = secret

        val response = restTestClient.post()
            .uri("/private/v1/auth/totp/verify")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"email":"admin@admin.com","password":"admin","code":"${currentTotpCode(secret)}"}""")
            .exchange()
            .expectStatus().isOk()
            .returnResult<SessionResponse>()
            .responseBody

        assertEquals("Administrador", response?.name)
        assertEquals("admin@admin.com", response?.email)
    }

    @Test
    @Order(8)
    fun `enroll rejects a request once totp is already enabled`() {
        restTestClient.post()
            .uri("/private/v1/auth/totp/enroll")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"email":"admin@admin.com","password":"admin"}""")
            .exchange()
            .expectStatus().isEqualTo(409)
    }

    @Test
    @Order(9)
    fun `login without a totp code is rejected once totp is enabled`() {
        restTestClient.post()
            .uri("/private/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"email":"admin@admin.com","password":"admin"}""")
            .exchange()
            .expectStatus().isUnauthorized()
    }

    @Test
    @Order(10)
    fun `login with an incorrect totp code is rejected`() {
        restTestClient.post()
            .uri("/private/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"email":"admin@admin.com","password":"admin","totpCode":"000000"}""")
            .exchange()
            .expectStatus().isUnauthorized()
    }

    @Test
    @Order(11)
    fun `login with the correct totp code establishes a session`() {
        val response = restTestClient.post()
            .uri("/private/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"email":"admin@admin.com","password":"admin","totpCode":"${currentTotpCode(adminTotpSecret)}"}""")
            .exchange()
            .expectStatus().isOk()
            .returnResult<SessionResponse>()
            .responseBody

        assertEquals("admin@admin.com", response?.email)
    }

    @Test
    @Order(12)
    fun `session reflects the cookie set by login, and logout clears it`() {
        val cookie = restTestClient.post()
            .uri("/private/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"email":"admin@admin.com","password":"admin","totpCode":"${currentTotpCode(adminTotpSecret)}"}""")
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

    companion object {
        private lateinit var adminTotpSecret: String

        private fun currentTotpCode(secret: String): String =
            DefaultCodeGenerator().generate(secret, System.currentTimeMillis() / 1000L / 30L)
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd server && ./gradlew test --tests "br.com.investlog.server.auth.rest.controllers.AuthControllerTest"`
Expected: FAIL — compilation errors (`TotpEnrollResponse` doesn't exist, `/totp/enroll` and
`/totp/verify` don't exist, login doesn't return 202 for an unenrolled user).

- [ ] **Step 3: Add `totpCode` to `LoginRequest`**

Replace the full contents of
`server/src/main/kotlin/br/com/investlog/server/auth/rest/payloads/LoginRequest.kt`:

```kotlin
package br.com.investlog.server.auth.rest.payloads

data class LoginRequest(
    val email: String,
    val password: String,
    val totpCode: String? = null,
)
```

- [ ] **Step 4: Create the new payload classes**

Create `server/src/main/kotlin/br/com/investlog/server/auth/rest/payloads/TotpEnrollRequest.kt`:

```kotlin
package br.com.investlog.server.auth.rest.payloads

data class TotpEnrollRequest(
    val email: String,
    val password: String,
)
```

Create `server/src/main/kotlin/br/com/investlog/server/auth/rest/payloads/TotpEnrollResponse.kt`:

```kotlin
package br.com.investlog.server.auth.rest.payloads

data class TotpEnrollResponse(
    val secretKey: String,
    val qrCodeDataUri: String,
)
```

Create `server/src/main/kotlin/br/com/investlog/server/auth/rest/payloads/TotpVerifyRequest.kt`:

```kotlin
package br.com.investlog.server.auth.rest.payloads

data class TotpVerifyRequest(
    val email: String,
    val password: String,
    val code: String,
)
```

Create `server/src/main/kotlin/br/com/investlog/server/auth/rest/payloads/TotpEnrollmentRequiredResponse.kt`:

```kotlin
package br.com.investlog.server.auth.rest.payloads

data class TotpEnrollmentRequiredResponse(
    val status: String = "needs_enrollment",
)
```

- [ ] **Step 5: Rewrite AuthService**

Replace the full contents of
`server/src/main/kotlin/br/com/investlog/server/auth/domain/services/AuthService.kt`:

```kotlin
package br.com.investlog.server.auth.domain.services

import br.com.investlog.server.auth.rest.payloads.LoginRequest
import br.com.investlog.server.auth.rest.payloads.SessionResponse
import br.com.investlog.server.auth.rest.payloads.TotpEnrollRequest
import br.com.investlog.server.auth.rest.payloads.TotpEnrollResponse
import br.com.investlog.server.auth.rest.payloads.TotpVerifyRequest
import br.com.investlog.server.shared.exceptions.InvalidCredentialsException
import br.com.investlog.server.shared.exceptions.InvalidTotpCodeException
import br.com.investlog.server.shared.exceptions.TotpAlreadyEnabledException
import br.com.investlog.server.shared.exceptions.TotpRequiredException
import br.com.investlog.server.shared.security.CurrentUser
import br.com.investlog.server.shared.security.UserRepository
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.stereotype.Service

sealed interface LoginResult {
    data class Authenticated(val session: SessionResponse) : LoginResult
    data object EnrollmentRequired : LoginResult
}

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val totpService: TotpService,
) {

    fun login(request: LoginRequest, servletRequest: HttpServletRequest, servletResponse: HttpServletResponse): LoginResult {

        val user = verifyCredentials(request.email, request.password)

        if (!user.totpEnabled) {
            return LoginResult.EnrollmentRequired
        }

        val code = request.totpCode
            ?: throw TotpRequiredException("A TOTP code is required to complete login")

        val secret = userRepository.findTotpSecretByEmail(request.email)
            ?: throw InvalidTotpCodeException("Invalid TOTP code")

        if (!totpService.isCodeValid(secret, code)) {
            throw InvalidTotpCodeException("Invalid TOTP code")
        }

        return LoginResult.Authenticated(establishSession(user, servletRequest, servletResponse))
    }

    fun enrollTotp(request: TotpEnrollRequest): TotpEnrollResponse {

        val user = verifyCredentials(request.email, request.password)

        if (user.totpEnabled) {
            throw TotpAlreadyEnabledException("TOTP is already enabled for this account")
        }

        val secret = totpService.generateSecret()
        userRepository.updateTotpSecret(user.id, secret)

        return TotpEnrollResponse(
            secretKey = secret,
            qrCodeDataUri = totpService.qrCodeDataUri(user.email, secret),
        )
    }

    fun verifyTotp(request: TotpVerifyRequest, servletRequest: HttpServletRequest, servletResponse: HttpServletResponse): SessionResponse {

        val user = verifyCredentials(request.email, request.password)

        val secret = userRepository.findTotpSecretByEmail(request.email)
            ?: throw InvalidTotpCodeException("Invalid TOTP code")

        if (!totpService.isCodeValid(secret, request.code)) {
            throw InvalidTotpCodeException("Invalid TOTP code")
        }

        userRepository.enableTotp(user.id, secret)

        return establishSession(user.copy(totpEnabled = true), servletRequest, servletResponse)
    }

    fun currentSession(): SessionResponse {
        val user = SecurityContextHolder.getContext().authentication?.principal as? CurrentUser
            ?: throw InvalidCredentialsException("Not authenticated")
        return SessionResponse(name = user.name, email = user.email, role = user.role)
    }

    fun logout(servletRequest: HttpServletRequest) {
        servletRequest.getSession(false)?.invalidate()
        SecurityContextHolder.clearContext()
    }

    private fun verifyCredentials(email: String, password: String): CurrentUser {
        val user = userRepository.findByEmail(email)
            ?: throw InvalidCredentialsException("Invalid email or password")

        val passwordHash = userRepository.findPasswordHashByEmail(email)
            ?: throw InvalidCredentialsException("Invalid email or password")

        if (!passwordEncoder.matches(password, passwordHash)) {
            throw InvalidCredentialsException("Invalid email or password")
        }

        return user
    }

    private fun establishSession(user: CurrentUser, servletRequest: HttpServletRequest, servletResponse: HttpServletResponse): SessionResponse {

        servletRequest.getSession(true)
        servletRequest.changeSessionId()

        val authorities = listOf(SimpleGrantedAuthority("ROLE_${user.role}"))
        val authentication = UsernamePasswordAuthenticationToken(user, null, authorities)

        val context = SecurityContextHolder.createEmptyContext()
        context.authentication = authentication
        SecurityContextHolder.setContext(context)

        HttpSessionSecurityContextRepository().saveContext(context, servletRequest, servletResponse)

        return SessionResponse(name = user.name, email = user.email, role = user.role)
    }
}
```

- [ ] **Step 6: Rewrite AuthController**

Replace the full contents of
`server/src/main/kotlin/br/com/investlog/server/auth/rest/controllers/AuthController.kt`:

```kotlin
package br.com.investlog.server.auth.rest.controllers

import br.com.investlog.server.auth.domain.services.AuthService
import br.com.investlog.server.auth.domain.services.LoginResult
import br.com.investlog.server.auth.rest.payloads.LoginRequest
import br.com.investlog.server.auth.rest.payloads.SessionResponse
import br.com.investlog.server.auth.rest.payloads.TotpEnrollRequest
import br.com.investlog.server.auth.rest.payloads.TotpEnrollResponse
import br.com.investlog.server.auth.rest.payloads.TotpEnrollmentRequiredResponse
import br.com.investlog.server.auth.rest.payloads.TotpVerifyRequest
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/auth")
class AuthController(private val authService: AuthService) {

    @PostMapping("/login")
    fun login(
        @RequestBody request: LoginRequest,
        servletRequest: HttpServletRequest,
        servletResponse: HttpServletResponse,
    ): ResponseEntity<Any> =
        when (val result = authService.login(request, servletRequest, servletResponse)) {
            is LoginResult.Authenticated -> ResponseEntity.ok(result.session)
            is LoginResult.EnrollmentRequired -> ResponseEntity.status(HttpStatus.ACCEPTED).body(TotpEnrollmentRequiredResponse())
        }

    @PostMapping("/totp/enroll")
    fun enrollTotp(@RequestBody request: TotpEnrollRequest): TotpEnrollResponse =
        authService.enrollTotp(request)

    @PostMapping("/totp/verify")
    fun verifyTotp(
        @RequestBody request: TotpVerifyRequest,
        servletRequest: HttpServletRequest,
        servletResponse: HttpServletResponse,
    ): SessionResponse =
        authService.verifyTotp(request, servletRequest, servletResponse)

    @GetMapping("/session")
    fun session(): SessionResponse = authService.currentSession()

    @PostMapping("/logout")
    fun logout(servletRequest: HttpServletRequest) = authService.logout(servletRequest)
}
```

- [ ] **Step 7: Permit the two new endpoints in SecurityConfig**

In `server/src/main/kotlin/br/com/investlog/server/config/SecurityConfig.kt`, change:

```kotlin
                authorize("/private/v1/auth/login", permitAll)
                authorize(anyRequest, authenticated)
```

to:

```kotlin
                authorize("/private/v1/auth/login", permitAll)
                authorize("/private/v1/auth/totp/enroll", permitAll)
                authorize("/private/v1/auth/totp/verify", permitAll)
                authorize(anyRequest, authenticated)
```

- [ ] **Step 8: Run AuthControllerTest to verify it passes**

Run: `cd server && ./gradlew test --tests "br.com.investlog.server.auth.rest.controllers.AuthControllerTest"`
Expected: PASS (12 tests).

- [ ] **Step 9: Run the full suite and confirm the expected, documented breakage**

Run: `cd server && ./gradlew test`
Expected: `AuthControllerTest` and `TotpServiceTest`/`UserRepositoryTest` pass; every other
controller test class that uses the shared `restTestClient` fails, because
`TestcontainersConfiguration`'s `AdminSessionCookieInterceptor` still performs a plain login and
asserts an immediate 200. **This is expected and will be fixed by Task 4 — do not attempt to fix
it here.** Confirm in your task report which test classes are red and that the failure reason in
each is the interceptor's `check(response.statusCode() == 200)` line, not something else.

- [ ] **Step 10: Commit**

```bash
git add server/src/main/kotlin/br/com/investlog/server/auth \
  server/src/main/kotlin/br/com/investlog/server/config/SecurityConfig.kt \
  server/src/test/kotlin/br/com/investlog/server/auth/rest/controllers/AuthControllerTest.kt
git commit -m "feat(server): add TOTP enroll/verify endpoints and gate login on a valid code"
```

---

## Task 4: Fix the shared test admin-login interceptor

Restores the full suite to green by teaching `TestcontainersConfiguration`'s
`AdminSessionCookieInterceptor` to complete the enroll+verify dance (computing a valid code
in-process via the same TOTP library) when a plain login comes back 202.

**Files:**
- Modify: `server/src/test/kotlin/br/com/investlog/server/TestcontainersConfiguration.kt`

**Interfaces:**
- Consumes: `POST /auth/totp/enroll`, `POST /auth/totp/verify` (Task 3, HTTP contract only — this
  file talks to them over raw HTTP, not Kotlin types).

- [ ] **Step 1: Replace TestcontainersConfiguration.kt in full**

Replace the full contents of
`server/src/test/kotlin/br/com/investlog/server/TestcontainersConfiguration.kt`:

```kotlin
package br.com.investlog.server

import dev.samstevens.totp.code.DefaultCodeGenerator
import org.springframework.boot.resttestclient.autoconfigure.RestTestClientBuilderCustomizer
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    fun postgresContainer(): PostgreSQLContainer = PostgreSQLContainer(DockerImageName.parse(POSTGRES_IMAGE))

    /**
     * Registers an interceptor on the autoconfigured [org.springframework.test.web.servlet.client.RestTestClient]
     * builder so that every request issued through any test class's `@Autowired restTestClient` field
     * (there are ~13 of them, none of which can be edited for this task) automatically carries an
     * authenticated admin session cookie. The admin session is established lazily, on first use, by
     * issuing raw login/enroll/verify calls against the same host/port as the outgoing request, since
     * the embedded web server is not yet listening while this `@TestConfiguration`'s beans are being
     * created. Mandatory TOTP means a plain login now returns 202 "needs_enrollment" for a fresh admin
     * row, so this interceptor completes the enroll+verify dance itself, computing a valid code with
     * the same TOTP library the server uses.
     */
    @Bean
    fun adminSessionRestTestClientBuilderCustomizer(): RestTestClientBuilderCustomizer =
        RestTestClientBuilderCustomizer { builder -> builder.requestInterceptor(AdminSessionCookieInterceptor()) }

    private class AdminSessionCookieInterceptor : ClientHttpRequestInterceptor {

        @Volatile
        private var adminSessionCookie: String? = null

        override fun intercept(
            request: org.springframework.http.HttpRequest,
            body: ByteArray,
            execution: org.springframework.http.client.ClientHttpRequestExecution,
        ): org.springframework.http.client.ClientHttpResponse {
            val isAuthEndpoint = request.method == HttpMethod.POST &&
                (request.uri.path.endsWith(LOGIN_PATH) || request.uri.path.endsWith(ENROLL_PATH) || request.uri.path.endsWith(VERIFY_PATH))
            if (!isAuthEndpoint && request.headers.getFirst(HttpHeaders.COOKIE) == null) {
                request.headers.set(HttpHeaders.COOKIE, adminSessionCookieFor(request.uri))
            }
            return execution.execute(request, body)
        }

        private fun adminSessionCookieFor(requestUri: URI): String =
            adminSessionCookie ?: synchronized(this) {
                adminSessionCookie ?: loginAsAdmin(requestUri).also { adminSessionCookie = it }
            }

        private fun loginAsAdmin(requestUri: URI): String {
            val httpClient = HttpClient.newHttpClient()

            val loginResponse = postJson(httpClient, requestUri, LOGIN_PATH, ADMIN_CREDENTIALS_JSON)

            if (loginResponse.statusCode() == 200) {
                return loginResponse.setCookie()
            }

            check(loginResponse.statusCode() == 202) {
                "Admin login failed with status ${loginResponse.statusCode()} while authenticating the shared test RestTestClient"
            }

            val enrollResponse = postJson(httpClient, requestUri, ENROLL_PATH, ADMIN_CREDENTIALS_JSON)
            check(enrollResponse.statusCode() == 200) {
                "Admin TOTP enrollment failed with status ${enrollResponse.statusCode()}"
            }

            val secret = extractJsonField(enrollResponse.body(), "secretKey")
            val code = DefaultCodeGenerator().generate(secret, System.currentTimeMillis() / 1000L / 30L)

            val verifyResponse = postJson(
                httpClient,
                requestUri,
                VERIFY_PATH,
                """{"email":"admin@admin.com","password":"admin","code":"$code"}""",
            )
            check(verifyResponse.statusCode() == 200) {
                "Admin TOTP verification failed with status ${verifyResponse.statusCode()}"
            }

            return verifyResponse.setCookie()
        }

        private fun postJson(httpClient: HttpClient, requestUri: URI, path: String, jsonBody: String): HttpResponse<String> {
            val targetUri = URI(requestUri.scheme, null, requestUri.host, requestUri.port, path, null, null)
            val httpRequest = HttpRequest.newBuilder(targetUri)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build()
            return httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())
        }

        private fun HttpResponse<String>.setCookie(): String =
            headers().firstValue("Set-Cookie")
                .orElseThrow { IllegalStateException("Expected a session cookie in the response from $uri()") }
                .substringBefore(";")

        private fun extractJsonField(json: String, field: String): String {
            val regex = """"$field"\s*:\s*"([^"]+)"""".toRegex()
            return regex.find(json)?.groupValues?.get(1)
                ?: error("Field \"$field\" not found in response body: $json")
        }

        companion object {
            private const val LOGIN_PATH = "/private/v1/auth/login"
            private const val ENROLL_PATH = "/private/v1/auth/totp/enroll"
            private const val VERIFY_PATH = "/private/v1/auth/totp/verify"
            private const val ADMIN_CREDENTIALS_JSON = """{"email":"admin@admin.com","password":"admin"}"""
        }
    }

    companion object {
        const val POSTGRES_IMAGE = "postgres:18-alpine"
    }
}
```

- [ ] **Step 2: Run the full server suite to confirm everything is green again**

Run: `cd server && ./gradlew test`
Expected: BUILD SUCCESSFUL, every test class passes — including all the previously-red classes
from Task 3's Step 9, and `AuthControllerTest`'s own 12 tests (which manage the admin session
directly and don't go through this interceptor at all, so they're unaffected by this change).

- [ ] **Step 3: Commit**

```bash
git add server/src/test/kotlin/br/com/investlog/server/TestcontainersConfiguration.kt
git commit -m "fix(server): complete TOTP enrollment in the shared test admin-login interceptor"
```

- [ ] **Step 4: Push the branch and open the server PR**

```bash
git push -u origin feature/auth-phase2-totp-server
```

Open a PR from `feature/auth-phase2-totp-server` into `feature/authentication` (not `main`).
Title: `feat(auth): Phase 2 — mandatory TOTP for local logins (server)`. Body should summarize the
four tasks, note the intentional Task 3→4 breakage/fix handoff, and reference `Refs #2`.

---

## Task 5: Client — auth API and store support for the TOTP flow

**Branch:** `feature/auth-phase2-totp-client` (create from `feature/authentication`)

**Files:**
- Modify: `client/src/types.ts`
- Modify: `client/src/api/auth.ts`
- Modify: `client/src/stores/auth.ts`
- Modify: `client/src/stores/auth.test.ts`

**Interfaces:**
- Consumes (HTTP contract only, defined in Task 3): `POST /auth/login` returns 200
  `SessionResponse` or 202 `{"status":"needs_enrollment"}` or 401 `{"error":"totp_required"|"invalid_totp_code", "detail": string}`;
  `POST /auth/totp/enroll` returns 200 `{secretKey, qrCodeDataUri}`; `POST /auth/totp/verify`
  returns 200 `SessionResponse`.
- Produces: `authApi.login(email, password, totpCode?): Promise<LoginOutcome>`,
  `authApi.enroll(email, password): Promise<TotpEnrollResponse>`,
  `authApi.verify(email, password, code): Promise<SessionResponse>`,
  `useAuthStore().login(email, password, totpCode?): Promise<LoginStatus>`,
  `useAuthStore().enrollTotp(email, password): Promise<TotpEnrollResponse>`,
  `useAuthStore().verifyTotp(email, password, code): Promise<void>` — all consumed by Task 6.

- [ ] **Step 1: Write the failing store tests**

Replace the full contents of `client/src/stores/auth.test.ts`:

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
    enroll: vi.fn(),
    verify: vi.fn(),
  },
}))

describe('auth store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('sets the session after a successful login', async () => {
    vi.mocked(authApi.login).mockResolvedValue({
      status: 'authenticated',
      session: { name: 'Administrador', email: 'admin@admin.com', role: 'ADMIN' },
    })

    const store = useAuthStore()
    const status = await store.login('admin@admin.com', 'admin')

    expect(status).toBe('authenticated')
    expect(store.session).toEqual({ name: 'Administrador', email: 'admin@admin.com', role: 'ADMIN' })
  })

  it('does not set a session when enrollment is required', async () => {
    vi.mocked(authApi.login).mockResolvedValue({ status: 'needs_enrollment' })

    const store = useAuthStore()
    const status = await store.login('admin@admin.com', 'admin')

    expect(status).toBe('needs_enrollment')
    expect(store.session).toBeNull()
  })

  it('clears the session on logout', async () => {
    vi.mocked(authApi.login).mockResolvedValue({
      status: 'authenticated',
      session: { name: 'Administrador', email: 'admin@admin.com', role: 'ADMIN' },
    })
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

  it('enrollTotp delegates to the API and returns the enrollment payload', async () => {
    vi.mocked(authApi.enroll).mockResolvedValue({ secretKey: 'JBSWY3DPEHPK3PXP', qrCodeDataUri: 'data:image/png;base64,abc' })

    const store = useAuthStore()
    const enrollment = await store.enrollTotp('admin@admin.com', 'admin')

    expect(authApi.enroll).toHaveBeenCalledWith('admin@admin.com', 'admin')
    expect(enrollment.secretKey).toBe('JBSWY3DPEHPK3PXP')
  })

  it('verifyTotp sets the session on success', async () => {
    vi.mocked(authApi.verify).mockResolvedValue({ name: 'Administrador', email: 'admin@admin.com', role: 'ADMIN' })

    const store = useAuthStore()
    await store.verifyTotp('admin@admin.com', 'admin', '123456')

    expect(authApi.verify).toHaveBeenCalledWith('admin@admin.com', 'admin', '123456')
    expect(store.session?.email).toBe('admin@admin.com')
  })
})
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd client && npm run test -- src/stores/auth.test.ts`
Expected: FAIL — `authApi.enroll`/`authApi.verify` don't exist, `login` doesn't return an outcome
object.

- [ ] **Step 3: Add `TotpEnrollResponse` and `LoginOutcome` to types.ts**

In `client/src/types.ts`, add this block right after the existing `SessionResponse` interface:

```typescript
export interface TotpEnrollResponse {
  secretKey: string
  qrCodeDataUri: string
}

export type LoginOutcome =
  | { status: 'authenticated'; session: SessionResponse }
  | { status: 'needs_enrollment' }
  | { status: 'totp_required' }
  | { status: 'invalid_totp_code' }
```

- [ ] **Step 4: Rewrite api/auth.ts**

Replace the full contents of `client/src/api/auth.ts`:

```typescript
import { isAxiosError } from 'axios'
import { apiClient } from './client'
import type { LoginOutcome, SessionResponse, TotpEnrollResponse } from '@/types'

export const authApi = {
  async login(email: string, password: string, totpCode?: string): Promise<LoginOutcome> {
    try {
      const response = await apiClient.post<SessionResponse>('/auth/login', { email, password, totpCode })
      if (response.status === 202) {
        return { status: 'needs_enrollment' }
      }
      return { status: 'authenticated', session: response.data }
    } catch (error) {
      if (isAxiosError(error) && error.response?.data?.error === 'totp_required') {
        return { status: 'totp_required' }
      }
      if (isAxiosError(error) && error.response?.data?.error === 'invalid_totp_code') {
        return { status: 'invalid_totp_code' }
      }
      throw error
    }
  },
  logout(): Promise<void> {
    return apiClient.post('/auth/logout').then(() => undefined)
  },
  fetchSession(): Promise<SessionResponse> {
    return apiClient.get<SessionResponse>('/auth/session').then((response) => response.data)
  },
  enroll(email: string, password: string): Promise<TotpEnrollResponse> {
    return apiClient.post<TotpEnrollResponse>('/auth/totp/enroll', { email, password }).then((response) => response.data)
  },
  verify(email: string, password: string, code: string): Promise<SessionResponse> {
    return apiClient.post<SessionResponse>('/auth/totp/verify', { email, password, code }).then((response) => response.data)
  },
}
```

- [ ] **Step 5: Rewrite stores/auth.ts**

Replace the full contents of `client/src/stores/auth.ts`:

```typescript
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { authApi } from '@/api/auth'
import type { SessionResponse, TotpEnrollResponse } from '@/types'

export type LoginStatus = 'authenticated' | 'needs_enrollment' | 'totp_required' | 'invalid_totp_code'

export const useAuthStore = defineStore('auth', () => {
  const session = ref<SessionResponse | null>(null)
  const loading = ref(false)

  async function login(email: string, password: string, totpCode?: string): Promise<LoginStatus> {
    const outcome = await authApi.login(email, password, totpCode)
    if (outcome.status === 'authenticated') {
      session.value = outcome.session
    }
    return outcome.status
  }

  async function enrollTotp(email: string, password: string): Promise<TotpEnrollResponse> {
    return authApi.enroll(email, password)
  }

  async function verifyTotp(email: string, password: string, code: string): Promise<void> {
    session.value = await authApi.verify(email, password, code)
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

  return { session, loading, login, enrollTotp, verifyTotp, logout, restoreSession }
})
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `cd client && npm run test -- src/stores/auth.test.ts`
Expected: PASS (7 tests).

- [ ] **Step 7: Run the full client suite and the build**

Run: `cd client && npm run test`
Expected: PASS, except the 2 pre-existing unrelated `useFormat.spec.ts` failures if that fix (PR
#6) hasn't merged into this branch's base yet — note in your task report which failures you saw
and confirm each one is pre-existing/unrelated, not a regression from this task.

Run: `cd client && npm run build`
Expected: no TypeScript errors.

- [ ] **Step 8: Commit**

```bash
git add client/src/types.ts client/src/api/auth.ts client/src/stores/auth.ts client/src/stores/auth.test.ts
git commit -m "feat(client): add TOTP enroll/verify to the auth API and store"
```

---

## Task 6: Client — three-step LoginView (credentials / QR enrollment / code entry)

**Files:**
- Modify: `client/src/views/LoginView.vue`
- Modify: `client/src/views/LoginView.test.ts`
- Modify: `client/src/assets/styles.css`

**Interfaces:**
- Consumes: `useAuthStore().login(email, password, totpCode?): Promise<LoginStatus>`,
  `useAuthStore().enrollTotp(email, password): Promise<TotpEnrollResponse>`,
  `useAuthStore().verifyTotp(email, password, code): Promise<void>` (Task 5).

- [ ] **Step 1: Write the failing component tests**

Replace the full contents of `client/src/views/LoginView.test.ts`:

```typescript
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import Buefy from 'buefy'
import LoginView from './LoginView.vue'
import { useAuthStore } from '@/stores/auth'

vi.mock('@/api/auth', () => ({
  authApi: { login: vi.fn(), logout: vi.fn(), fetchSession: vi.fn(), enroll: vi.fn(), verify: vi.fn() },
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

  it('logs in and navigates to overview on submit when already enrolled', async () => {
    const store = useAuthStore()
    const loginSpy = vi.spyOn(store, 'login').mockResolvedValue('authenticated')
    router.push('/login')
    await router.isReady()

    const wrapper = mount(LoginView, { global: { plugins: [router, Buefy] } })
    await wrapper.find('input[type="email"]').setValue('admin@admin.com')
    await wrapper.find('input[type="password"]').setValue('admin')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(loginSpy).toHaveBeenCalledWith('admin@admin.com', 'admin')
    expect(router.currentRoute.value.name).toBe('overview')
  })

  it('shows the QR enrollment step when login needs enrollment, then verifies to log in', async () => {
    const store = useAuthStore()
    vi.spyOn(store, 'login').mockResolvedValue('needs_enrollment')
    const enrollSpy = vi.spyOn(store, 'enrollTotp').mockResolvedValue({
      secretKey: 'JBSWY3DPEHPK3PXP',
      qrCodeDataUri: 'data:image/png;base64,abc',
    })
    const verifySpy = vi.spyOn(store, 'verifyTotp').mockResolvedValue()
    router.push('/login')
    await router.isReady()

    const wrapper = mount(LoginView, { global: { plugins: [router, Buefy] } })
    await wrapper.find('input[type="email"]').setValue('admin@admin.com')
    await wrapper.find('input[type="password"]').setValue('admin')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(enrollSpy).toHaveBeenCalledWith('admin@admin.com', 'admin')
    const qrImage = wrapper.find('img.auth-totp-qr')
    expect(qrImage.exists()).toBe(true)
    expect(qrImage.attributes('src')).toBe('data:image/png;base64,abc')

    await wrapper.find('input[maxlength="6"]').setValue('123456')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(verifySpy).toHaveBeenCalledWith('admin@admin.com', 'admin', '123456')
    expect(router.currentRoute.value.name).toBe('overview')
  })

  it('shows the code step when login requires a totp code, then logs in with it', async () => {
    const store = useAuthStore()
    const loginSpy = vi
      .spyOn(store, 'login')
      .mockResolvedValueOnce('totp_required')
      .mockResolvedValueOnce('authenticated')
    router.push('/login')
    await router.isReady()

    const wrapper = mount(LoginView, { global: { plugins: [router, Buefy] } })
    await wrapper.find('input[type="email"]').setValue('admin@admin.com')
    await wrapper.find('input[type="password"]').setValue('admin')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.find('img.auth-totp-qr').exists()).toBe(false)
    expect(wrapper.find('input[maxlength="6"]').exists()).toBe(true)

    await wrapper.find('input[maxlength="6"]').setValue('654321')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(loginSpy).toHaveBeenLastCalledWith('admin@admin.com', 'admin', '654321')
    expect(router.currentRoute.value.name).toBe('overview')
  })
})

function flushPromises() {
  return new Promise((resolve) => setTimeout(resolve, 0))
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd client && npm run test -- src/views/LoginView.test.ts`
Expected: FAIL — `LoginView` doesn't yet render a QR image or a 6-digit code step, and the mocked
`login` call is asserted with a third `undefined` argument the current component doesn't pass.

- [ ] **Step 3: Rewrite LoginView.vue**

Replace the full contents of `client/src/views/LoginView.vue`:

```vue
<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import LogoMark from '@/components/icons/LogoMark.vue'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()

type Step = 'credentials' | 'enroll' | 'totp'

const step = ref<Step>('credentials')
const email = ref('')
const password = ref('')
const totpCode = ref('')
const qrCodeDataUri = ref('')
const error = ref('')
const submitting = ref(false)

const title = computed(() => {
  if (step.value === 'enroll') return 'Configure a autenticação em duas etapas'
  if (step.value === 'totp') return 'Confirme o código de autenticação'
  return 'Bem-vindo de volta'
})

const subtitle = computed(() => {
  if (step.value === 'enroll') return 'Escaneie o QR code com um aplicativo autenticador e digite o código gerado.'
  if (step.value === 'totp') return 'Digite o código do seu aplicativo autenticador.'
  return 'Entre para acompanhar seus investimentos.'
})

async function submitCredentials() {
  error.value = ''
  submitting.value = true
  try {
    const status = await auth.login(email.value, password.value)
    if (status === 'authenticated') {
      await router.push({ name: 'overview' })
      return
    }
    if (status === 'needs_enrollment') {
      const enrollment = await auth.enrollTotp(email.value, password.value)
      qrCodeDataUri.value = enrollment.qrCodeDataUri
      step.value = 'enroll'
      return
    }
    if (status === 'totp_required') {
      step.value = 'totp'
      return
    }
    error.value = 'E-mail ou senha inválidos.'
  } catch {
    error.value = 'E-mail ou senha inválidos.'
  } finally {
    submitting.value = false
  }
}

async function submitEnrollment() {
  error.value = ''
  submitting.value = true
  try {
    await auth.verifyTotp(email.value, password.value, totpCode.value)
    await router.push({ name: 'overview' })
  } catch {
    error.value = 'Código inválido. Tente novamente.'
  } finally {
    submitting.value = false
  }
}

async function submitTotpCode() {
  error.value = ''
  submitting.value = true
  try {
    const status = await auth.login(email.value, password.value, totpCode.value)
    if (status === 'authenticated') {
      await router.push({ name: 'overview' })
      return
    }
    error.value = 'Código inválido. Tente novamente.'
  } catch {
    error.value = 'Código inválido. Tente novamente.'
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
      <div class="auth-card">
        <div class="auth-card-brand">
          <span class="brand-mark"><LogoMark :size="20" /></span>
          <span class="brand-name">Invest<b>Log</b></span>
        </div>

        <div class="auth-head">
          <h1 class="auth-title">{{ title }}</h1>
          <p class="auth-sub">{{ subtitle }}</p>
        </div>

        <p v-if="error" class="auth-error">{{ error }}</p>

        <form v-if="step === 'credentials'" class="form-stack" @submit.prevent="submitCredentials">
          <b-field label="E-mail">
            <b-input v-model="email" type="email" placeholder="voce@email.com" required />
          </b-field>
          <b-field label="Senha">
            <b-input v-model="password" type="password" placeholder="••••••••" required />
          </b-field>
          <b-button type="is-primary" expanded native-type="submit" :loading="submitting" class="auth-submit">
            Entrar
          </b-button>
        </form>

        <form v-else-if="step === 'enroll'" class="form-stack" @submit.prevent="submitEnrollment">
          <img
            :src="qrCodeDataUri"
            alt="QR code para configurar a autenticação em duas etapas"
            class="auth-totp-qr"
          />
          <b-field label="Código de 6 dígitos">
            <b-input v-model="totpCode" maxlength="6" placeholder="000000" required />
          </b-field>
          <b-button type="is-primary" expanded native-type="submit" :loading="submitting" class="auth-submit">
            Confirmar
          </b-button>
        </form>

        <form v-else class="form-stack" @submit.prevent="submitTotpCode">
          <b-field label="Código de 6 dígitos">
            <b-input v-model="totpCode" maxlength="6" placeholder="000000" required />
          </b-field>
          <b-button type="is-primary" expanded native-type="submit" :loading="submitting" class="auth-submit">
            Entrar
          </b-button>
        </form>
      </div>
    </main>
  </div>
</template>
```

- [ ] **Step 4: Add the `.auth-totp-qr` style**

In `client/src/assets/styles.css`, add this line right after the existing `.auth-error{...}` rule
(and before the `@media(max-width:860px){` block) in the `/* ===== Auth / login ===== */` section:

```css
.auth-totp-qr{display:block;width:200px;height:200px;margin:0 auto 4px;border-radius:12px;border:1px solid var(--border);background:#fff;padding:10px;}
```

- [ ] **Step 5: Suppress the global error toast for expected TOTP step-transition responses**

`client/src/api/client.ts`'s response interceptor shows a red `is-danger` toast on every non-401
error and on 401s while already on `/login`. Since `totp_required` and `invalid_totp_code` are
both 401s, and the code-entry step is reached by simply logging in normally as an already-enrolled
user, *every single login* for an enrolled user would otherwise flash a red English error toast
while the component quietly transitions to the code step — the component already shows its own
inline `.auth-error` message when the code is actually wrong, so the toast is redundant noise on
the expected path, not a real failure. Suppress it for these two cases.

In `client/src/api/client.ts`, change:

```typescript
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

to:

```typescript
const SILENT_ERROR_CODES = ['totp_required', 'invalid_totp_code']

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401 && router.currentRoute.value.name !== 'login') {
      router.push({ name: 'login' })
      return Promise.reject(error)
    }
    if (SILENT_ERROR_CODES.includes(error.response?.data?.error)) {
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

There is no existing test file for `client.ts`'s interceptor (it has no automated coverage today),
so verify this manually once the server is running: log in as an enrolled user with a deliberately
wrong code and confirm only the inline `.auth-error` message appears, with no red toast.

- [ ] **Step 6: Run the tests to verify they pass**

Run: `cd client && npm run test -- src/views/LoginView.test.ts`
Expected: PASS (3 tests).

- [ ] **Step 7: Run the full client suite and the build**

Run: `cd client && npm run test`
Expected: PASS, except the 2 pre-existing unrelated `useFormat.spec.ts` failures if PR #6 hasn't
merged into this branch's base yet.

Run: `cd client && npm run build`
Expected: no TypeScript errors.

- [ ] **Step 8: Commit**

```bash
git add client/src/views/LoginView.vue client/src/views/LoginView.test.ts client/src/assets/styles.css client/src/api/client.ts
git commit -m "feat(client): add the TOTP enrollment and code-entry steps to LoginView"
```

- [ ] **Step 9: Push the branch and open the client PR**

```bash
git push -u origin feature/auth-phase2-totp-client
```

Open a PR from `feature/auth-phase2-totp-client` into `feature/authentication` (not `main`).
Title: `feat(auth): Phase 2 — mandatory TOTP for local logins (client)`. Body should summarize the
two tasks and reference `Refs #2`.
