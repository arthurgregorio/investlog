# Authentication Phase 3 — Self-Registration and Admin Approval Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Anyone can self-register a local account; every new account (and every not-yet-approved
existing account) is gated behind an admin approval step before it can use anything beyond
checking its own status and logging out. Admins get a "Usuários locais" page to approve, reject,
promote/demote, reset TOTP for, and delete users.

**Architecture:** Registration and approval status are orthogonal to the mandatory-TOTP gate
Phase 2 already built — a newly registered user still enrolls in TOTP like anyone else, and
separately carries a `PENDING`/`APPROVED`/`REJECTED` status that the security filter chain checks
on every request except session-check and logout. Status is baked into the session's authorities
at login time (`STATUS_${status}`, alongside the existing `ROLE_${role}`), the same mechanism
Phase 1 already established for role. A new `usersadmin` server module (mirroring the existing
`typelists` module's `rest/{controllers,payloads}` + `domain/{services,repositories}` layout)
exposes the admin-only user-management endpoints.

**Tech Stack:** No new dependencies. Reuses `spring-boot-starter-security`'s
`AccessDeniedHandler` for the 403-with-reason response, and the existing jOOQ/Liquibase/Pinia/
Buefy stack throughout.

## Global Constraints

- No abbreviated names — full descriptive names everywhere (server: `server/CLAUDE.md`; client:
  `client/CLAUDE.md`).
- No new Liquibase migration needed. `UserStatus` gains a `REJECTED` value, but the `status`
  column is plain `TEXT` with no DB-level check constraint — this is a Kotlin-enum-only change.
- Per the repo's root `CLAUDE.md`: server and client changes go in separate PRs, each targeting
  `feature/auth-phase2-totp-server` / `feature/auth-phase2-totp-client` respectively (branched
  from those tips, not from `feature/authentication`, since Phase 2's PRs are still open and
  Phase 3 touches the same files Phase 2 just changed — `SecurityConfig.kt`, `AuthController.kt`,
  `AuthService.kt`, `CurrentUser.kt` is untouched but `SessionResponse.kt`/`UserRepository.kt` are
  extended, etc.). Server tasks (1-3) branch `feature/auth-phase3-registration-server` from
  `feature/auth-phase2-totp-server`; client tasks (4-7) branch
  `feature/auth-phase3-registration-client` from `feature/auth-phase2-totp-client`. Each PR
  targets the branch it was cut from (a stacked PR), not `feature/authentication` directly.
- Every commit and PR references `Refs #3` (this phase's tracking issue). Since more than one PR
  addresses it, none of them use `Closes #3` — close the issue manually once all of this phase's
  PRs have merged.
- Every PR gets the `feature` label and is assigned to `arthurgregorio`.
- TOTP enrollment stays mandatory and unaffected by approval status — a `PENDING` user still
  enrolls/verifies TOTP exactly like an `APPROVED` one; the two gates are independent.

## Design decisions (read before implementing)

**Why a distinct `REJECTED` status instead of resetting to `PENDING`:** the design spec left this
open ("could be a separate rejected status; decision at implementation time"). Resetting a
rejected applicant back to `PENDING` is indistinguishable from "not yet reviewed" — an admin
scanning the list can't tell a fresh signup from someone they already turned down, and the
rejected person's own pending-approval screen would misleadingly suggest their request is still
under consideration. A distinct `REJECTED` value costs nothing (no migration — see above) and
admins can still move a rejected user back to `APPROVED` later via the same approve action if
they change their mind.

**Why login still succeeds (establishes a session) for a `PENDING`/`REJECTED` user:** this
mirrors how Phase 4 (Google OAuth, not yet built) is designed to work — a first-time Google login
also establishes a real session and lets the SPA's own session check decide whether to show the
pending-approval screen, rather than refusing to authenticate at all. Keeping local login
consistent with that means one authorization mechanism (the security filter chain's
`STATUS_APPROVED` requirement, with `/auth/session` and `/auth/logout` explicitly exempted) covers
both, instead of local and Google needing different early-rejection logic.

**Approve/role changes take effect on the affected user's *next* login, not immediately — but
reject/delete are enforced on their very *next action*, not left open for the rest of the
session:** Spring Security's authorities are computed once at login time and stored in the
session; there is no per-request re-authorization of the filter chain against the database (this
is a pre-existing characteristic of the session model Phase 1 built, not something this phase
introduces — it already applied to role before this phase added status). Left alone, this would
mean a rejected or deleted user keeps full access for the entire remaining life of their
already-open session, since the filter chain's `hasAuthority("STATUS_APPROVED")` check only ever
consults the cached session authorities — that is a containment gap, not a mere convenience lag,
and it is closed by a second, independent check: `SecurityContextCurrentUserProvider.getCurrentUser()`
already re-fetches the user's row from the database on every call (it has to, to pick up profile
changes — see its existing doc comment); Task 1 adds a status check there too, so it throws
`UserNotApprovedException` (mapped to the same `{"error":"pending_approval",...}` 403 shape as the
filter-chain gate) the moment a no-longer-`APPROVED` user's session touches any business service —
wallets, holdings, profile, typelists, currency rates, all resolve the current user through this
provider. `/auth/session` and `/auth/logout` deliberately don't call `getCurrentUser()` (a
pending/rejected user can still check their own status and log out), so `GET /auth/session`
itself can keep echoing the cached-at-login status until the next login — a residual, cosmetic
consequence: a just-rejected user's client won't proactively redirect to the pending-approval
screen until their first subsequent data request 403s. That first real request is always blocked,
which is the security-relevant property; the smoother "notice immediately and redirect" UX is
accepted as out of scope for this phase. Approve and role-change still only take effect on next
login, same as before — only rejection/deletion needed the tighter guarantee, since only they are
a containment concern.

**Accepted edge case — `/private/v1/users/**` is gated on `ROLE_ADMIN` only, not also
`STATUS_APPROVED`:** in the unusual case where an admin's own status is somehow not `APPROVED`
(reachable only if another admin changes their role before approving them — an admin action, not
something the affected user can trigger), that admin could still use the `/users/**` endpoints
while being blocked from every other endpoint. This is a narrow, self-hosted-app-appropriate risk
(the state can only be reached by another admin's deliberate action) rather than a path an
outside attacker can exploit; documented rather than special-cased.

**Self-action guard:** `changeRole` and `delete` on `UsersAdminController` both reject a request
where the target user is the caller themselves (400). Without this, the last/only admin could
demote or delete their own account and permanently lock everyone (including themselves) out of
user management, with no recovery path short of direct database access. `approve`/`reject` don't
need the same guard — an admin's own status is always already `APPROVED` (set at bootstrap), so
self-targeting those actions is a no-op, not a lockout risk.

## File Structure

**Server** (`br.com.investlog.server`):
- `shared/security/UserStatus.kt` — modified, add `REJECTED`.
- `shared/security/UserRepository.kt` — modified, add `createLocalUser`.
- `shared/security/CurrentUserProvider.kt` — modified, `SecurityContextCurrentUserProvider` throws
  `UserNotApprovedException` when the freshly re-fetched user is no longer `APPROVED` — this is
  what actually revokes a rejected/deleted user's access within their already-open session (see
  Design decisions above), rather than waiting for their next login.
- `shared/exceptions/UserNotApprovedException.kt` — new, maps to 403 with `error: pending_approval`.
- `auth/rest/payloads/RegisterRequest.kt` — new.
- `auth/rest/payloads/SessionResponse.kt` — modified, add `status: UserStatus`.
- `auth/domain/services/AuthService.kt` — modified, add `register`; `establishSession` and
  `currentSession` both include `status`; `establishSession`'s authorities gain
  `STATUS_${user.status}`.
- `auth/rest/controllers/AuthController.kt` — modified, add `POST /register`.
- `config/SecurityConfig.kt` — modified, permit `/auth/register`; explicit `authenticated` rules
  for `/auth/session`/`/auth/logout` (so a non-approved user can still use them); admin-only
  `/users/**`; `anyRequest` now requires `STATUS_APPROVED` instead of just `authenticated`; a new
  `AccessDeniedHandler` bean that reports `pending_approval` vs a generic forbidden reason.
- `config/GlobalExceptionHandler.kt` — modified (across Tasks 1 and 3): maps
  `UserNotApprovedException` (Task 1) and `SelfActionNotAllowedException` (Task 3).
- `shared/exceptions/SelfActionNotAllowedException.kt` — new, maps to 400.
- `usersadmin/rest/payloads/UserAdminResponse.kt`, `RoleUpdateRequest.kt` — new.
- `usersadmin/rest/controllers/UsersAdminController.kt` — new (built across Tasks 2 and 3).
- `usersadmin/domain/services/UsersAdminService.kt` — new (built across Tasks 2 and 3).
- `usersadmin/domain/repositories/UsersAdminRepository.kt` — new (built across Tasks 2 and 3).
- Tests: `RegistrationControllerTest.kt` (new), `UsersAdminControllerTest.kt` (new, built across
  Tasks 2 and 3).

**Client** (`client/src`):
- `types.ts` — modified: `UserStatus` type; `SessionResponse` gains `status`; new
  `UserAdminResponse`, `RoleUpdateRequest`-equivalent inline params.
- `api/auth.ts` — modified, add `register`.
- `api/usersAdmin.ts` — new.
- `stores/auth.ts` — modified, add `isAdmin` computed getter (no other logic change — `status`
  flows through the existing `SessionResponse` type automatically).
- `stores/usersAdmin.ts` — new.
- `views/LoginView.vue` — modified, add a `'register'` step + credentials/register toggle link.
- `views/PendingApprovalView.vue` — new.
- `views/SettingsView.vue` — modified, add the "Usuários locais" card section.
- `views/WalletsView.vue`, `components/investments/HoldingDetailPanel.vue` — modified, hide
  delete actions for non-admins.
- `components/layout/TheTopNav.vue` — modified, hide the "Configurações" nav item for non-admins.
- `router/index.ts` — modified, add the `/pending-approval` route; redirect non-`APPROVED`
  sessions there (except `/login`/`/pending-approval`/`/auth` themselves); redirect non-admins
  away from `/settings`.
- Tests: `stores/auth.test.ts`, `stores/usersAdmin.test.ts` (new), `views/LoginView.test.ts`,
  `views/PendingApprovalView.test.ts` (new), `router/index.test.ts` — all modified/created to
  match.

---

## Task 1: Registration endpoint and the approval status gate

**Branch:** `feature/auth-phase3-registration-server` (create from `feature/auth-phase2-totp-server`)

**Files:**
- Modify: `server/src/main/kotlin/br/com/investlog/server/shared/security/UserStatus.kt`
- Modify: `server/src/main/kotlin/br/com/investlog/server/shared/security/UserRepository.kt`
- Modify: `server/src/main/kotlin/br/com/investlog/server/shared/security/CurrentUserProvider.kt`
- Create: `server/src/main/kotlin/br/com/investlog/server/shared/exceptions/UserNotApprovedException.kt`
- Modify: `server/src/main/kotlin/br/com/investlog/server/config/GlobalExceptionHandler.kt`
- Create: `server/src/main/kotlin/br/com/investlog/server/auth/rest/payloads/RegisterRequest.kt`
- Modify: `server/src/main/kotlin/br/com/investlog/server/auth/rest/payloads/SessionResponse.kt`
- Modify: `server/src/main/kotlin/br/com/investlog/server/auth/domain/services/AuthService.kt`
- Modify: `server/src/main/kotlin/br/com/investlog/server/auth/rest/controllers/AuthController.kt`
- Modify: `server/src/main/kotlin/br/com/investlog/server/config/SecurityConfig.kt`
- Test: `server/src/test/kotlin/br/com/investlog/server/auth/rest/controllers/RegistrationControllerTest.kt`

**Interfaces:**
- Produces: `AuthService.register(request: RegisterRequest)`, `SessionResponse.status: UserStatus`
  — consumed by later tasks and by the client.
- Produces: `AccessDeniedHandler` bean in `SecurityConfig` writing `{"error":"pending_approval",...}`
  on a 403 caused by a missing `STATUS_APPROVED` authority, or `{"error":"forbidden",...}`
  otherwise — consumed by Task 4 (client).
- Produces: `SecurityContextCurrentUserProvider.getCurrentUser()` now throws
  `UserNotApprovedException` (mapped to the same `{"error":"pending_approval",...}` 403 shape) when
  the current user's freshly re-fetched status is no longer `APPROVED` — every business service
  that resolves the current user through `CurrentUserProvider` picks this up automatically. Task 3
  reuses `UserNotApprovedException`'s presence in `GlobalExceptionHandler` as the pattern for its
  own `SelfActionNotAllowedException` mapping in the same file.

- [ ] **Step 1: Add `REJECTED` to `UserStatus`**

Replace the full contents of
`server/src/main/kotlin/br/com/investlog/server/shared/security/UserStatus.kt`:

```kotlin
package br.com.investlog.server.shared.security

enum class UserStatus { PENDING, APPROVED, REJECTED }
```

- [ ] **Step 2: Write the failing registration/status-gate tests**

Create `server/src/test/kotlin/br/com/investlog/server/auth/rest/controllers/RegistrationControllerTest.kt`:

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

class RegistrationControllerTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTestClient: RestTestClient

    @Test
    @Order(1)
    fun `register creates a pending local user`() {
        restTestClient.post()
            .uri("/private/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Nova Usuária","email":"nova@example.com","password":"senha123"}""")
            .exchange()
            .expectStatus().isCreated()
    }

    @Test
    @Order(2)
    fun `duplicate email registration is rejected`() {
        restTestClient.post()
            .uri("/private/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Outra Pessoa","email":"nova@example.com","password":"outrasenha"}""")
            .exchange()
            .expectStatus().isEqualTo(409)
    }

    @Test
    @Order(3)
    fun `a pending user can log in after totp enrollment but is forbidden from private endpoints`() {
        restTestClient.post()
            .uri("/private/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"email":"nova@example.com","password":"senha123"}""")
            .exchange()
            .expectStatus().isEqualTo(202)

        val secret = restTestClient.post()
            .uri("/private/v1/auth/totp/enroll")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"email":"nova@example.com","password":"senha123"}""")
            .exchange()
            .expectStatus().isOk()
            .returnResult<TotpEnrollResponse>()
            .responseBody
            ?.secretKey
            ?: error("Enroll did not return a secret")

        val code = DefaultCodeGenerator().generate(secret, System.currentTimeMillis() / 1000L / 30L)

        val cookie = restTestClient.post()
            .uri("/private/v1/auth/totp/verify")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"email":"nova@example.com","password":"senha123","code":"$code"}""")
            .exchange()
            .expectStatus().isOk()
            .returnResult<SessionResponse>()
            .responseHeaders
            .getFirst("Set-Cookie")
            ?.substringBefore(";")
            ?: error("Verify did not set a session cookie")

        pendingUserCookie = cookie

        restTestClient.get()
            .uri("/private/v1/profile")
            .header("Cookie", cookie)
            .exchange()
            .expectStatus().isEqualTo(403)
            .returnResult<Map<String, Any?>>()
            .responseBody
            .let { assertEquals("pending_approval", it?.get("error")) }
    }

    @Test
    @Order(4)
    fun `a pending user can still check their session and log out`() {
        restTestClient.get()
            .uri("/private/v1/auth/session")
            .header("Cookie", pendingUserCookie)
            .exchange()
            .expectStatus().isOk()
            .returnResult<SessionResponse>()
            .responseBody
            .let { assertEquals("PENDING", it?.status?.name) }

        restTestClient.post()
            .uri("/private/v1/auth/logout")
            .header("Cookie", pendingUserCookie)
            .exchange()
            .expectStatus().isOk()
    }

    companion object {
        private lateinit var pendingUserCookie: String
    }
}
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `cd server && ./gradlew test --tests "br.com.investlog.server.auth.rest.controllers.RegistrationControllerTest"`
Expected: FAIL — compilation error (`/auth/register` doesn't exist yet).

- [ ] **Step 4: Add `RegisterRequest`**

Create `server/src/main/kotlin/br/com/investlog/server/auth/rest/payloads/RegisterRequest.kt`:

```kotlin
package br.com.investlog.server.auth.rest.payloads

import jakarta.validation.constraints.NotBlank

data class RegisterRequest(
    @field:NotBlank(message = "name must not be blank")
    val name: String,
    @field:NotBlank(message = "email must not be blank")
    val email: String,
    @field:NotBlank(message = "password must not be blank")
    val password: String,
)
```

- [ ] **Step 5: Add `status` to `SessionResponse`**

Replace the full contents of
`server/src/main/kotlin/br/com/investlog/server/auth/rest/payloads/SessionResponse.kt`:

```kotlin
package br.com.investlog.server.auth.rest.payloads

import br.com.investlog.server.shared.security.UserRole
import br.com.investlog.server.shared.security.UserStatus

data class SessionResponse(
    val name: String,
    val email: String,
    val role: UserRole,
    val status: UserStatus,
)
```

- [ ] **Step 6: Add `createLocalUser` to `UserRepository`**

In `server/src/main/kotlin/br/com/investlog/server/shared/security/UserRepository.kt`, add this
method to the `UserRepository` class, right after `updatePasswordHash`:

```kotlin

    fun createLocalUser(name: String, email: String, passwordHash: String): CurrentUser =
        dsl.insertInto(USERS)
            .set(USERS.NAME, name)
            .set(USERS.EMAIL, email)
            .set(USERS.PASSWORD_HASH, passwordHash)
            .set(USERS.AUTH_PROVIDER, AuthProvider.LOCAL.name)
            .set(USERS.ROLE, UserRole.USER.name)
            .set(USERS.STATUS, UserStatus.PENDING.name)
            .returning()
            .fetchSingle()
            .toCurrentUser()
```

- [ ] **Step 7: Add `register` to `AuthService`, and `status` to every `SessionResponse` construction**

In `server/src/main/kotlin/br/com/investlog/server/auth/domain/services/AuthService.kt`:

Add this import alongside the existing `br.com.investlog.server.auth.rest.payloads.*` imports:

```kotlin
import br.com.investlog.server.auth.rest.payloads.RegisterRequest
```

Add this method to the `AuthService` class, right after `verifyTotp`:

```kotlin

    fun register(request: RegisterRequest) {
        userRepository.createLocalUser(request.name, request.email, passwordEncoder.encode(request.password))
    }
```

Change `currentSession`:

```kotlin
    fun currentSession(): SessionResponse {
        val user = SecurityContextHolder.getContext().authentication?.principal as? CurrentUser
            ?: throw InvalidCredentialsException("Not authenticated")
        return SessionResponse(name = user.name, email = user.email, role = user.role, status = user.status)
    }
```

Change `establishSession`'s authorities and its returned `SessionResponse`:

```kotlin
    private fun establishSession(user: CurrentUser, servletRequest: HttpServletRequest, servletResponse: HttpServletResponse): SessionResponse {

        servletRequest.getSession(true)
        servletRequest.changeSessionId()

        val authorities = listOf(
            SimpleGrantedAuthority("ROLE_${user.role}"),
            SimpleGrantedAuthority("STATUS_${user.status}"),
        )
        val authentication = UsernamePasswordAuthenticationToken(user, null, authorities)

        val context = SecurityContextHolder.createEmptyContext()
        context.authentication = authentication
        SecurityContextHolder.setContext(context)

        HttpSessionSecurityContextRepository().saveContext(context, servletRequest, servletResponse)

        return SessionResponse(name = user.name, email = user.email, role = user.role, status = user.status)
    }
```

- [ ] **Step 8: Add the `POST /register` endpoint**

In `server/src/main/kotlin/br/com/investlog/server/auth/rest/controllers/AuthController.kt`, add
this import:

```kotlin
import br.com.investlog.server.auth.rest.payloads.RegisterRequest
```

and these two imports:

```kotlin
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.ResponseStatus
```

Add this method right after `logout`:

```kotlin

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    fun register(@Valid @RequestBody request: RegisterRequest) {
        authService.register(request)
    }
```

`AuthController` doesn't currently import `HttpStatus` — add:

```kotlin
import org.springframework.http.HttpStatus
```

- [ ] **Step 9: Update `SecurityConfig` — permit registration, explicit session/logout rules,
  admin-only `/users/**`, and the `STATUS_APPROVED` gate with a distinguishing `AccessDeniedHandler`**

Replace the full contents of `server/src/main/kotlin/br/com/investlog/server/config/SecurityConfig.kt`:

```kotlin
package br.com.investlog.server.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.security.web.authentication.HttpStatusEntryPoint

@Configuration
class SecurityConfig {

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun accessDeniedHandler(): AccessDeniedHandler = AccessDeniedHandler { _, response, _ ->
        val authentication = SecurityContextHolder.getContext().authentication
        val isPendingApproval = authentication?.authorities.orEmpty().none { it.authority == "STATUS_APPROVED" }

        response.status = HttpStatus.FORBIDDEN.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.writer.write(
            if (isPendingApproval) {
                """{"error":"pending_approval","detail":"Your account is pending administrator approval"}"""
            } else {
                """{"error":"forbidden","detail":"You do not have permission to perform this action"}"""
            }
        )
    }

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        val unauthorizedEntryPoint: AuthenticationEntryPoint = HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)
        http {
            csrf { disable() }
            anonymous { disable() }
            authorizeHttpRequests {
                authorize("/private/v1/auth/login", permitAll)
                authorize("/private/v1/auth/register", permitAll)
                authorize("/private/v1/auth/totp/enroll", permitAll)
                authorize("/private/v1/auth/totp/verify", permitAll)
                authorize("/private/v1/auth/session", authenticated)
                authorize("/private/v1/auth/logout", authenticated)
                authorize("/private/v1/users/**", hasAuthority("ROLE_ADMIN"))
                authorize(anyRequest, hasAuthority("STATUS_APPROVED"))
            }
            exceptionHandling {
                authenticationEntryPoint = unauthorizedEntryPoint
                accessDeniedHandler = accessDeniedHandler()
            }
        }
        return http.build()
    }
}
```

- [ ] **Step 10: Close the mid-session revocation gap — add `UserNotApprovedException` and its
  mapping**

The filter-chain's `STATUS_APPROVED` check in Step 9 only ever consults the session's
authorities, which are computed once at login. Left alone, that means an admin rejecting or
deleting a user does not revoke that user's already-open session for the rest of its life. This
step closes that gap at the service layer, which every business endpoint already passes through.

Create `server/src/main/kotlin/br/com/investlog/server/shared/exceptions/UserNotApprovedException.kt`:

```kotlin
package br.com.investlog.server.shared.exceptions

class UserNotApprovedException(message: String) : RuntimeException(message)
```

In `server/src/main/kotlin/br/com/investlog/server/config/GlobalExceptionHandler.kt`:

Add `FORBIDDEN` to the existing `org.springframework.http.HttpStatus.*` imports (alongside
`BAD_REQUEST`, `CONFLICT`, etc.):

```kotlin
import org.springframework.http.HttpStatus.FORBIDDEN
```

Add this import alongside the other `shared.exceptions` imports:

```kotlin
import br.com.investlog.server.shared.exceptions.UserNotApprovedException
```

Add this handler method right after `handleInvalidCredentials`:

```kotlin
    @ExceptionHandler(UserNotApprovedException::class)
    fun handleUserNotApproved(ex: UserNotApprovedException): ProblemDetail {

        val problemDetail = ProblemDetail.forStatusAndDetail(FORBIDDEN, ex.message ?: "Your account is not approved")
        problemDetail.setProperty("error", "pending_approval")
        problemDetail.setProperty("timestamp", Instant.now())

        return problemDetail
    }
```

- [ ] **Step 11: Throw `UserNotApprovedException` from `SecurityContextCurrentUserProvider`**

Replace the full contents of
`server/src/main/kotlin/br/com/investlog/server/shared/security/CurrentUserProvider.kt`:

```kotlin
package br.com.investlog.server.shared.security

import br.com.investlog.server.shared.exceptions.InvalidCredentialsException
import br.com.investlog.server.shared.exceptions.UserNotApprovedException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

interface CurrentUserProvider {
    fun getCurrentUser(): CurrentUser
}

/**
 * Resolves the current user's identity from the session's [Authentication] principal, then
 * re-fetches the row from the database so callers always see the latest persisted preferences
 * (the session principal is set once at login and is never refreshed in place, so reading it
 * directly would return stale `accentColor`/`preferredCurrency` values after a profile update).
 *
 * This re-fetch also enforces approval status on every call: the security filter chain's
 * `STATUS_APPROVED` check only ever consults the session's authorities, which are fixed at login
 * time, so it alone would let a rejected or deleted user keep using every business endpoint for
 * the rest of their already-open session. Throwing here — rather than only at login — is what
 * actually revokes that access, on the user's very next request rather than their next login.
 */
@Component
class SecurityContextCurrentUserProvider(private val userRepository: UserRepository) : CurrentUserProvider {

    override fun getCurrentUser(): CurrentUser {
        val sessionPrincipal = SecurityContextHolder.getContext().authentication?.principal as? CurrentUser
            ?: error("No authenticated user in the current security context")

        val user = userRepository.findByEmail(sessionPrincipal.email)
            ?: throw InvalidCredentialsException("Authenticated user no longer exists")

        if (user.status != UserStatus.APPROVED) {
            throw UserNotApprovedException("User ${user.email} is not approved")
        }

        return user
    }
}
```

- [ ] **Step 12: Run the tests to verify they pass**

Run: `cd server && ./gradlew test --tests "br.com.investlog.server.auth.rest.controllers.RegistrationControllerTest"`
Expected: PASS (4 tests).

- [ ] **Step 13: Run the full server suite**

Run: `cd server && ./gradlew test`
Expected: PASS — the seeded admin already carries `status = APPROVED` (set in Phase 1's seed
data), so it already satisfies the new `STATUS_APPROVED` requirement on every other endpoint, and
`SecurityContextCurrentUserProvider`'s new check passes for the same reason; no other test class
should be affected. Confirm this in your report — if anything unexpected goes red, stop and
report it rather than guessing at a fix.

- [ ] **Step 14: Commit**

```bash
git add server/src/main/kotlin/br/com/investlog/server/shared/security/UserStatus.kt \
  server/src/main/kotlin/br/com/investlog/server/shared/security/UserRepository.kt \
  server/src/main/kotlin/br/com/investlog/server/shared/security/CurrentUserProvider.kt \
  server/src/main/kotlin/br/com/investlog/server/shared/exceptions/UserNotApprovedException.kt \
  server/src/main/kotlin/br/com/investlog/server/config/GlobalExceptionHandler.kt \
  server/src/main/kotlin/br/com/investlog/server/config/SecurityConfig.kt \
  server/src/main/kotlin/br/com/investlog/server/auth \
  server/src/test/kotlin/br/com/investlog/server/auth/rest/controllers/RegistrationControllerTest.kt
git commit -m "feat(server): add self-registration and the admin-approval status gate"
```

---

## Task 2: Users-admin listing, approve, and reject

**Files:**
- Create: `server/src/main/kotlin/br/com/investlog/server/usersadmin/rest/payloads/UserAdminResponse.kt`
- Create: `server/src/main/kotlin/br/com/investlog/server/usersadmin/domain/repositories/UsersAdminRepository.kt`
- Create: `server/src/main/kotlin/br/com/investlog/server/usersadmin/domain/services/UsersAdminService.kt`
- Create: `server/src/main/kotlin/br/com/investlog/server/usersadmin/rest/controllers/UsersAdminController.kt`
- Test: `server/src/test/kotlin/br/com/investlog/server/usersadmin/rest/controllers/UsersAdminControllerTest.kt`

**Interfaces:**
- Consumes: nothing new from Task 1 beyond the schema/enum already in place.
- Produces: `UsersAdminService.findAll(pageable)`, `.approve(externalId)`, `.reject(externalId)` —
  consumed by Task 3, which extends this same file.

- [ ] **Step 1: Write the failing tests**

Create `server/src/test/kotlin/br/com/investlog/server/usersadmin/rest/controllers/UsersAdminControllerTest.kt`:

```kotlin
package br.com.investlog.server.usersadmin.rest.controllers

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.usersadmin.rest.payloads.UserAdminResponse
import org.junit.jupiter.api.Order
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.returnResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UsersAdminControllerTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTestClient: RestTestClient

    @Test
    @Order(1)
    fun `list includes the seeded admin`() {
        val response = restTestClient.get()
            .uri("/private/v1/users?size=200")
            .exchange()
            .expectStatus().isOk()
            .returnResult<Map<String, Any?>>()
            .responseBody

        @Suppress("UNCHECKED_CAST")
        val content = response?.get("content") as List<Map<String, Any?>>
        assertTrue(content.any { it["email"] == "admin@admin.com" })
    }

    @Test
    @Order(2)
    fun `registers two users to approve and reject`() {
        restTestClient.post()
            .uri("/private/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Para Aprovar","email":"aprovar@example.com","password":"senha123"}""")
            .exchange()
            .expectStatus().isCreated()

        restTestClient.post()
            .uri("/private/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Para Rejeitar","email":"rejeitar@example.com","password":"senha123"}""")
            .exchange()
            .expectStatus().isCreated()

        val response = restTestClient.get()
            .uri("/private/v1/users?size=200")
            .exchange()
            .expectStatus().isOk()
            .returnResult<Map<String, Any?>>()
            .responseBody

        @Suppress("UNCHECKED_CAST")
        val content = response?.get("content") as List<Map<String, Any?>>
        approveTargetId = content.single { it["email"] == "aprovar@example.com" }["id"] as String
        rejectTargetId = content.single { it["email"] == "rejeitar@example.com" }["id"] as String
    }

    @Test
    @Order(3)
    fun `approve sets the user's status to APPROVED`() {
        val response = restTestClient.patch()
            .uri("/private/v1/users/$approveTargetId/approve")
            .exchange()
            .expectStatus().isOk()
            .returnResult<UserAdminResponse>()
            .responseBody

        assertEquals("APPROVED", response?.status?.name)
    }

    @Test
    @Order(4)
    fun `reject sets the user's status to REJECTED`() {
        val response = restTestClient.patch()
            .uri("/private/v1/users/$rejectTargetId/reject")
            .exchange()
            .expectStatus().isOk()
            .returnResult<UserAdminResponse>()
            .responseBody

        assertEquals("REJECTED", response?.status?.name)
    }

    @Test
    @Order(5)
    fun `approve on an unknown id returns 404`() {
        restTestClient.patch()
            .uri("/private/v1/users/00000000-0000-0000-0000-000000000000/approve")
            .exchange()
            .expectStatus().isNotFound()
    }

    companion object {
        private lateinit var approveTargetId: String
        private lateinit var rejectTargetId: String
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd server && ./gradlew test --tests "br.com.investlog.server.usersadmin.rest.controllers.UsersAdminControllerTest"`
Expected: FAIL — compilation error (`UserAdminResponse`, `/users` don't exist yet).

- [ ] **Step 3: Create `UserAdminResponse`**

Create `server/src/main/kotlin/br/com/investlog/server/usersadmin/rest/payloads/UserAdminResponse.kt`:

```kotlin
package br.com.investlog.server.usersadmin.rest.payloads

import br.com.investlog.server.shared.security.AuthProvider
import br.com.investlog.server.shared.security.UserRole
import br.com.investlog.server.shared.security.UserStatus
import java.util.UUID

data class UserAdminResponse(
    val id: UUID,
    val name: String,
    val email: String,
    val role: UserRole,
    val status: UserStatus,
    val authProvider: AuthProvider,
    val totpEnabled: Boolean,
)
```

- [ ] **Step 4: Create `UsersAdminRepository`**

Create `server/src/main/kotlin/br/com/investlog/server/usersadmin/domain/repositories/UsersAdminRepository.kt`:

```kotlin
package br.com.investlog.server.usersadmin.domain.repositories

import br.com.investlog.server.jooq.system.tables.records.UsersRecord
import br.com.investlog.server.jooq.system.tables.references.USERS
import br.com.investlog.server.shared.persistence.pagedModelOf
import br.com.investlog.server.shared.security.AuthProvider
import br.com.investlog.server.shared.security.UserRole
import br.com.investlog.server.shared.security.UserStatus
import br.com.investlog.server.usersadmin.rest.payloads.UserAdminResponse
import org.jooq.DSLContext
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.util.UUID

@Repository
class UsersAdminRepository(private val dsl: DSLContext) {

    fun findAll(pageable: Pageable): PagedModel<UserAdminResponse> {
        val content = dsl.selectFrom(USERS)
            .orderBy(USERS.NAME)
            .limit(pageable.pageSize)
            .offset(pageable.offset.toInt())
            .fetch()
            .map { it.toAdminResponse() }

        val total = dsl.fetchCount(dsl.selectFrom(USERS))

        return pagedModelOf(content, pageable, total.toLong())
    }

    fun findByExternalId(externalId: UUID): UsersRecord? =
        dsl.selectFrom(USERS)
            .where(USERS.EXTERNAL_ID.eq(externalId))
            .fetchOne()

    fun updateStatus(userId: Long, status: UserStatus): UserAdminResponse =
        dsl.update(USERS)
            .set(USERS.STATUS, status.name)
            .set(USERS.UPDATED_AT, OffsetDateTime.now())
            .where(USERS.ID.eq(userId))
            .returning()
            .fetchSingle()
            .toAdminResponse()

    private fun UsersRecord.toAdminResponse() = UserAdminResponse(
        id = externalId!!,
        name = name!!,
        email = email!!,
        role = UserRole.valueOf(role!!),
        status = UserStatus.valueOf(status!!),
        authProvider = AuthProvider.valueOf(authProvider!!),
        totpEnabled = totpEnabled!!,
    )
}
```

- [ ] **Step 5: Create `UsersAdminService`**

Create `server/src/main/kotlin/br/com/investlog/server/usersadmin/domain/services/UsersAdminService.kt`:

```kotlin
package br.com.investlog.server.usersadmin.domain.services

import br.com.investlog.server.shared.exceptions.NotFoundException
import br.com.investlog.server.shared.security.UserStatus
import br.com.investlog.server.usersadmin.domain.repositories.UsersAdminRepository
import br.com.investlog.server.usersadmin.rest.payloads.UserAdminResponse
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class UsersAdminService(private val usersAdminRepository: UsersAdminRepository) {

    fun findAll(pageable: Pageable): PagedModel<UserAdminResponse> = usersAdminRepository.findAll(pageable)

    fun approve(externalId: UUID): UserAdminResponse = updateStatus(externalId, UserStatus.APPROVED)

    fun reject(externalId: UUID): UserAdminResponse = updateStatus(externalId, UserStatus.REJECTED)

    private fun updateStatus(externalId: UUID, status: UserStatus): UserAdminResponse {
        val user = usersAdminRepository.findByExternalId(externalId)
            ?: throw NotFoundException("User $externalId not found")

        return usersAdminRepository.updateStatus(user.id!!, status)
    }
}
```

- [ ] **Step 6: Create `UsersAdminController`**

Create `server/src/main/kotlin/br/com/investlog/server/usersadmin/rest/controllers/UsersAdminController.kt`:

```kotlin
package br.com.investlog.server.usersadmin.rest.controllers

import br.com.investlog.server.usersadmin.domain.services.UsersAdminService
import br.com.investlog.server.usersadmin.rest.payloads.UserAdminResponse
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/users")
class UsersAdminController(private val usersAdminService: UsersAdminService) {

    @GetMapping
    fun findAll(pageable: Pageable): PagedModel<UserAdminResponse> = usersAdminService.findAll(pageable)

    @PatchMapping("/{id}/approve")
    fun approve(@PathVariable id: UUID): UserAdminResponse = usersAdminService.approve(id)

    @PatchMapping("/{id}/reject")
    fun reject(@PathVariable id: UUID): UserAdminResponse = usersAdminService.reject(id)
}
```

- [ ] **Step 7: Run the tests to verify they pass**

Run: `cd server && ./gradlew test --tests "br.com.investlog.server.usersadmin.rest.controllers.UsersAdminControllerTest"`
Expected: PASS (5 tests).

- [ ] **Step 8: Run the full server suite**

Run: `cd server && ./gradlew test`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add server/src/main/kotlin/br/com/investlog/server/usersadmin \
  server/src/test/kotlin/br/com/investlog/server/usersadmin
git commit -m "feat(server): add the users-admin list, approve, and reject endpoints"
```

---

## Task 3: Role change, TOTP reset, and delete — with a self-action guard

**Files:**
- Create: `server/src/main/kotlin/br/com/investlog/server/shared/exceptions/SelfActionNotAllowedException.kt`
- Modify: `server/src/main/kotlin/br/com/investlog/server/config/GlobalExceptionHandler.kt`
- Create: `server/src/main/kotlin/br/com/investlog/server/usersadmin/rest/payloads/RoleUpdateRequest.kt`
- Modify: `server/src/main/kotlin/br/com/investlog/server/usersadmin/domain/repositories/UsersAdminRepository.kt`
- Modify: `server/src/main/kotlin/br/com/investlog/server/usersadmin/domain/services/UsersAdminService.kt`
- Modify: `server/src/main/kotlin/br/com/investlog/server/usersadmin/rest/controllers/UsersAdminController.kt`
- Modify: `server/src/test/kotlin/br/com/investlog/server/usersadmin/rest/controllers/UsersAdminControllerTest.kt`

**Interfaces:**
- Consumes: `UsersAdminRepository`/`UsersAdminService`/`UsersAdminController` from Task 2.
- Produces: `UsersAdminService.changeRole(externalId, request)`, `.resetTotp(externalId)`,
  `.delete(externalId)` — no later task consumes these directly (client Task 6 consumes the HTTP
  contract only).

- [ ] **Step 1: Create `SelfActionNotAllowedException` and its mapping**

Create `server/src/main/kotlin/br/com/investlog/server/shared/exceptions/SelfActionNotAllowedException.kt`:

```kotlin
package br.com.investlog.server.shared.exceptions

class SelfActionNotAllowedException(message: String) : RuntimeException(message)
```

In `server/src/main/kotlin/br/com/investlog/server/config/GlobalExceptionHandler.kt`, add this
import alongside the other `shared.exceptions` imports:

```kotlin
import br.com.investlog.server.shared.exceptions.SelfActionNotAllowedException
```

Add this handler method right after `handleInvalidCredentials`:

```kotlin
    @ExceptionHandler(SelfActionNotAllowedException::class)
    fun handleSelfActionNotAllowed(ex: SelfActionNotAllowedException): ProblemDetail {

        val problemDetail = ProblemDetail.forStatusAndDetail(BAD_REQUEST, ex.message ?: "This action cannot target your own account")
        problemDetail.setProperty("timestamp", Instant.now())

        return problemDetail
    }
```

- [ ] **Step 2: Write the failing tests — append to `UsersAdminControllerTest.kt`**

Append these tests inside the `UsersAdminControllerTest` class, right after the existing `` `approve on an unknown id returns 404` `` test (before the `companion object`):

```kotlin

    @Test
    @Order(6)
    fun `registers a third user to manage role, totp, and deletion`() {
        restTestClient.post()
            .uri("/private/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Para Gerenciar","email":"gerenciar@example.com","password":"senha123"}""")
            .exchange()
            .expectStatus().isCreated()

        val response = restTestClient.get()
            .uri("/private/v1/users?size=200")
            .exchange()
            .expectStatus().isOk()
            .returnResult<Map<String, Any?>>()
            .responseBody

        @Suppress("UNCHECKED_CAST")
        val content = response?.get("content") as List<Map<String, Any?>>
        manageTargetId = content.single { it["email"] == "gerenciar@example.com" }["id"] as String
    }

    @Test
    @Order(7)
    fun `role change promotes the target user to admin`() {
        val response = restTestClient.patch()
            .uri("/private/v1/users/$manageTargetId/role")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"role":"ADMIN"}""")
            .exchange()
            .expectStatus().isOk()
            .returnResult<UserAdminResponse>()
            .responseBody

        assertEquals("ADMIN", response?.role?.name)
    }

    @Test
    @Order(8)
    fun `role change rejects targeting your own account`() {
        val adminId = (
            restTestClient.get()
                .uri("/private/v1/users?size=200")
                .exchange()
                .expectStatus().isOk()
                .returnResult<Map<String, Any?>>()
                .responseBody
                ?.get("content") as List<*>
            )
            .map { it as Map<*, *> }
            .single { it["email"] == "admin@admin.com" }["id"] as String

        restTestClient.patch()
            .uri("/private/v1/users/$adminId/role")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"role":"USER"}""")
            .exchange()
            .expectStatus().isBadRequest()
    }

    @Test
    @Order(9)
    fun `totp reset clears the enabled flag`() {
        val response = restTestClient.patch()
            .uri("/private/v1/users/$manageTargetId/totp-reset")
            .exchange()
            .expectStatus().isOk()
            .returnResult<UserAdminResponse>()
            .responseBody

        assertEquals(false, response?.totpEnabled)
    }

    @Test
    @Order(10)
    fun `delete rejects targeting your own account`() {
        val adminId = (
            restTestClient.get()
                .uri("/private/v1/users?size=200")
                .exchange()
                .expectStatus().isOk()
                .returnResult<Map<String, Any?>>()
                .responseBody
                ?.get("content") as List<*>
            )
            .map { it as Map<*, *> }
            .single { it["email"] == "admin@admin.com" }["id"] as String

        restTestClient.delete()
            .uri("/private/v1/users/$adminId")
            .exchange()
            .expectStatus().isBadRequest()
    }

    @Test
    @Order(11)
    fun `delete removes the target user`() {
        restTestClient.delete()
            .uri("/private/v1/users/$manageTargetId")
            .exchange()
            .expectStatus().isNoContent()

        val response = restTestClient.get()
            .uri("/private/v1/users?size=200")
            .exchange()
            .expectStatus().isOk()
            .returnResult<Map<String, Any?>>()
            .responseBody

        @Suppress("UNCHECKED_CAST")
        val content = response?.get("content") as List<Map<String, Any?>>
        assertTrue(content.none { it["email"] == "gerenciar@example.com" })
    }

    @Test
    @Order(12)
    fun `rejecting a user revokes their already-open session on the next request, without a new login`() {
        restTestClient.post()
            .uri("/private/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Sessão Aberta","email":"sessao@example.com","password":"senha123"}""")
            .exchange()
            .expectStatus().isCreated()

        val targetId = (
            restTestClient.get()
                .uri("/private/v1/users?size=200")
                .exchange()
                .expectStatus().isOk()
                .returnResult<Map<String, Any?>>()
                .responseBody
                ?.get("content") as List<*>
            )
            .map { it as Map<*, *> }
            .single { it["email"] == "sessao@example.com" }["id"] as String

        restTestClient.patch()
            .uri("/private/v1/users/$targetId/approve")
            .exchange()
            .expectStatus().isOk()

        val secret = restTestClient.post()
            .uri("/private/v1/auth/totp/enroll")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"email":"sessao@example.com","password":"senha123"}""")
            .exchange()
            .expectStatus().isOk()
            .returnResult<TotpEnrollResponse>()
            .responseBody
            ?.secretKey
            ?: error("Enroll did not return a secret")

        val code = DefaultCodeGenerator().generate(secret, System.currentTimeMillis() / 1000L / 30L)

        val cookie = restTestClient.post()
            .uri("/private/v1/auth/totp/verify")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"email":"sessao@example.com","password":"senha123","code":"$code"}""")
            .exchange()
            .expectStatus().isOk()
            .returnResult<SessionResponse>()
            .responseHeaders
            .getFirst("Set-Cookie")
            ?.substringBefore(";")
            ?: error("Verify did not set a session cookie")

        // The session's cached authorities now say STATUS_APPROVED. Rejecting the user changes
        // the database but cannot reach into that already-issued session.
        restTestClient.patch()
            .uri("/private/v1/users/$targetId/reject")
            .exchange()
            .expectStatus().isOk()

        // The filter chain alone would still admit this request (its cached authority is stale) —
        // this must be blocked by SecurityContextCurrentUserProvider's fresh status re-check
        // instead, proving the gap from Task 1 Step 11 is actually closed, not just unit-level.
        restTestClient.get()
            .uri("/private/v1/profile")
            .header("Cookie", cookie)
            .exchange()
            .expectStatus().isEqualTo(403)
            .returnResult<Map<String, Any?>>()
            .responseBody
            .let { assertEquals("pending_approval", it?.get("error")) }
    }
```

Add these imports alongside the existing ones at the top of the file:

```kotlin
import br.com.investlog.server.auth.rest.payloads.SessionResponse
import br.com.investlog.server.auth.rest.payloads.TotpEnrollResponse
import dev.samstevens.totp.code.DefaultCodeGenerator
```

Add `private lateinit var manageTargetId: String` to the existing `companion object` block.

- [ ] **Step 3: Run the tests to verify they fail**

Run: `cd server && ./gradlew test --tests "br.com.investlog.server.usersadmin.rest.controllers.UsersAdminControllerTest"`
Expected: FAIL — compilation error (`/role`, `/totp-reset`, `DELETE` don't exist yet).

- [ ] **Step 4: Create `RoleUpdateRequest`**

Create `server/src/main/kotlin/br/com/investlog/server/usersadmin/rest/payloads/RoleUpdateRequest.kt`:

```kotlin
package br.com.investlog.server.usersadmin.rest.payloads

import br.com.investlog.server.shared.security.UserRole

data class RoleUpdateRequest(
    val role: UserRole,
)
```

- [ ] **Step 5: Add `updateRole`, `resetTotp`, and `deleteByExternalId` to `UsersAdminRepository`**

In `server/src/main/kotlin/br/com/investlog/server/usersadmin/domain/repositories/UsersAdminRepository.kt`,
add this import alongside the existing ones:

```kotlin
import br.com.investlog.server.shared.security.UserRole
```

Add these three methods right after `updateStatus`:

```kotlin

    fun updateRole(userId: Long, role: UserRole): UserAdminResponse =
        dsl.update(USERS)
            .set(USERS.ROLE, role.name)
            .set(USERS.UPDATED_AT, OffsetDateTime.now())
            .where(USERS.ID.eq(userId))
            .returning()
            .fetchSingle()
            .toAdminResponse()

    fun resetTotp(userId: Long): UserAdminResponse =
        dsl.update(USERS)
            .set(USERS.TOTP_SECRET, null as String?)
            .set(USERS.TOTP_ENABLED, false)
            .set(USERS.UPDATED_AT, OffsetDateTime.now())
            .where(USERS.ID.eq(userId))
            .returning()
            .fetchSingle()
            .toAdminResponse()

    fun deleteByExternalId(externalId: UUID): Int =
        dsl.deleteFrom(USERS)
            .where(USERS.EXTERNAL_ID.eq(externalId))
            .execute()
```

- [ ] **Step 6: Extend `UsersAdminService` with role change, TOTP reset, delete, and the self-guard**

Replace the full contents of
`server/src/main/kotlin/br/com/investlog/server/usersadmin/domain/services/UsersAdminService.kt`:

```kotlin
package br.com.investlog.server.usersadmin.domain.services

import br.com.investlog.server.jooq.system.tables.records.UsersRecord
import br.com.investlog.server.shared.exceptions.NotFoundException
import br.com.investlog.server.shared.exceptions.SelfActionNotAllowedException
import br.com.investlog.server.shared.security.CurrentUserProvider
import br.com.investlog.server.shared.security.UserStatus
import br.com.investlog.server.usersadmin.domain.repositories.UsersAdminRepository
import br.com.investlog.server.usersadmin.rest.payloads.RoleUpdateRequest
import br.com.investlog.server.usersadmin.rest.payloads.UserAdminResponse
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class UsersAdminService(
    private val currentUserProvider: CurrentUserProvider,
    private val usersAdminRepository: UsersAdminRepository,
) {

    fun findAll(pageable: Pageable): PagedModel<UserAdminResponse> = usersAdminRepository.findAll(pageable)

    fun approve(externalId: UUID): UserAdminResponse = updateStatus(externalId, UserStatus.APPROVED)

    fun reject(externalId: UUID): UserAdminResponse = updateStatus(externalId, UserStatus.REJECTED)

    fun changeRole(externalId: UUID, request: RoleUpdateRequest): UserAdminResponse {
        val user = requireUser(externalId)
        requireNotSelf(user)
        return usersAdminRepository.updateRole(user.id!!, request.role)
    }

    fun resetTotp(externalId: UUID): UserAdminResponse {
        val user = requireUser(externalId)
        return usersAdminRepository.resetTotp(user.id!!)
    }

    fun delete(externalId: UUID) {
        val user = requireUser(externalId)
        requireNotSelf(user)
        usersAdminRepository.deleteByExternalId(externalId)
    }

    private fun updateStatus(externalId: UUID, status: UserStatus): UserAdminResponse {
        val user = requireUser(externalId)
        return usersAdminRepository.updateStatus(user.id!!, status)
    }

    private fun requireUser(externalId: UUID): UsersRecord =
        usersAdminRepository.findByExternalId(externalId)
            ?: throw NotFoundException("User $externalId not found")

    private fun requireNotSelf(user: UsersRecord) {
        if (user.id == currentUserProvider.getCurrentUser().id) {
            throw SelfActionNotAllowedException("This action cannot target your own account")
        }
    }
}
```

- [ ] **Step 7: Add the three endpoints to `UsersAdminController`**

Replace the full contents of
`server/src/main/kotlin/br/com/investlog/server/usersadmin/rest/controllers/UsersAdminController.kt`:

```kotlin
package br.com.investlog.server.usersadmin.rest.controllers

import br.com.investlog.server.usersadmin.domain.services.UsersAdminService
import br.com.investlog.server.usersadmin.rest.payloads.RoleUpdateRequest
import br.com.investlog.server.usersadmin.rest.payloads.UserAdminResponse
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/users")
class UsersAdminController(private val usersAdminService: UsersAdminService) {

    @GetMapping
    fun findAll(pageable: Pageable): PagedModel<UserAdminResponse> = usersAdminService.findAll(pageable)

    @PatchMapping("/{id}/approve")
    fun approve(@PathVariable id: UUID): UserAdminResponse = usersAdminService.approve(id)

    @PatchMapping("/{id}/reject")
    fun reject(@PathVariable id: UUID): UserAdminResponse = usersAdminService.reject(id)

    @PatchMapping("/{id}/role")
    fun changeRole(@PathVariable id: UUID, @RequestBody request: RoleUpdateRequest): UserAdminResponse =
        usersAdminService.changeRole(id, request)

    @PatchMapping("/{id}/totp-reset")
    fun resetTotp(@PathVariable id: UUID): UserAdminResponse = usersAdminService.resetTotp(id)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: UUID) {
        usersAdminService.delete(id)
    }
}
```

- [ ] **Step 8: Run the tests to verify they pass**

Run: `cd server && ./gradlew test --tests "br.com.investlog.server.usersadmin.rest.controllers.UsersAdminControllerTest"`
Expected: PASS (12 tests).

- [ ] **Step 9: Run the full server suite**

Run: `cd server && ./gradlew test`
Expected: PASS.

- [ ] **Step 10: Commit**

```bash
git add server/src/main/kotlin/br/com/investlog/server/shared/exceptions/SelfActionNotAllowedException.kt \
  server/src/main/kotlin/br/com/investlog/server/config/GlobalExceptionHandler.kt \
  server/src/main/kotlin/br/com/investlog/server/usersadmin \
  server/src/test/kotlin/br/com/investlog/server/usersadmin/rest/controllers/UsersAdminControllerTest.kt
git commit -m "feat(server): add role change, TOTP reset, and delete with a self-action guard"
```

- [ ] **Step 11: Push the branch and open the server PR**

```bash
git push -u origin feature/auth-phase3-registration-server
```

Open a PR from `feature/auth-phase3-registration-server` into `feature/auth-phase2-totp-server`
(a stacked PR, not into `feature/authentication` — Phase 2's server PR is still open). Title:
`feat(auth): Phase 3 — self-registration and admin approval (server)`. Reference `Refs #3`, add
the `feature` label, assign to `arthurgregorio`.

---

## Task 4: Client — auth API/store additions and the users-admin API/store

**Branch:** `feature/auth-phase3-registration-client` (create from `feature/auth-phase2-totp-client`)

**Files:**
- Modify: `client/src/types.ts`
- Modify: `client/src/api/auth.ts`
- Create: `client/src/api/usersAdmin.ts`
- Modify: `client/src/stores/auth.ts`
- Create: `client/src/stores/usersAdmin.ts`
- Modify: `client/src/stores/auth.test.ts`
- Create: `client/src/stores/usersAdmin.test.ts`

**Interfaces:**
- Consumes (HTTP contract only, defined in Task 1-3, on the sibling server branch): `POST
  /auth/register` → 201, `GET /users` → `PagedModel<UserAdminResponse>`, `PATCH
  /users/{id}/approve|reject|role|totp-reset` → 200 `UserAdminResponse`, `DELETE /users/{id}` →
  204.
- Produces: `authApi.register(name, email, password): Promise<void>`, `useAuthStore().isAdmin`
  (computed), `usersAdminApi.*`, `useUsersAdminStore()` — all consumed by Tasks 5-7.

- [ ] **Step 1: Write the failing tests**

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
    register: vi.fn(),
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
      session: { name: 'Administrador', email: 'admin@admin.com', role: 'ADMIN', status: 'APPROVED' },
    })

    const store = useAuthStore()
    const status = await store.login('admin@admin.com', 'admin')

    expect(status).toBe('authenticated')
    expect(store.session).toEqual({
      name: 'Administrador',
      email: 'admin@admin.com',
      role: 'ADMIN',
      status: 'APPROVED',
    })
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
      session: { name: 'Administrador', email: 'admin@admin.com', role: 'ADMIN', status: 'APPROVED' },
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
    vi.mocked(authApi.fetchSession).mockResolvedValue({
      name: 'Administrador',
      email: 'admin@admin.com',
      role: 'ADMIN',
      status: 'APPROVED',
    })

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
    vi.mocked(authApi.verify).mockResolvedValue({
      name: 'Administrador',
      email: 'admin@admin.com',
      role: 'ADMIN',
      status: 'APPROVED',
    })

    const store = useAuthStore()
    await store.verifyTotp('admin@admin.com', 'admin', '123456')

    expect(authApi.verify).toHaveBeenCalledWith('admin@admin.com', 'admin', '123456')
    expect(store.session?.email).toBe('admin@admin.com')
  })

  it('register delegates to the API', async () => {
    vi.mocked(authApi.register).mockResolvedValue(undefined)

    const store = useAuthStore()
    await store.register('Nova Usuária', 'nova@example.com', 'senha123')

    expect(authApi.register).toHaveBeenCalledWith('Nova Usuária', 'nova@example.com', 'senha123')
  })

  it('isAdmin reflects the session role', async () => {
    vi.mocked(authApi.login).mockResolvedValue({
      status: 'authenticated',
      session: { name: 'Administrador', email: 'admin@admin.com', role: 'ADMIN', status: 'APPROVED' },
    })

    const store = useAuthStore()
    expect(store.isAdmin).toBe(false)
    await store.login('admin@admin.com', 'admin')
    expect(store.isAdmin).toBe(true)
  })
})
```

Create `client/src/stores/usersAdmin.test.ts`:

```typescript
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useUsersAdminStore } from './usersAdmin'
import { usersAdminApi } from '@/api/usersAdmin'

vi.mock('@/api/usersAdmin', () => ({
  usersAdminApi: {
    findAll: vi.fn(),
    approve: vi.fn(),
    reject: vi.fn(),
    changeRole: vi.fn(),
    resetTotp: vi.fn(),
    remove: vi.fn(),
  },
}))

const adminUser = {
  id: '11111111-1111-1111-1111-111111111111',
  name: 'Administrador',
  email: 'admin@admin.com',
  role: 'ADMIN' as const,
  status: 'APPROVED' as const,
  authProvider: 'LOCAL' as const,
  totpEnabled: true,
}

const pendingUser = {
  id: '22222222-2222-2222-2222-222222222222',
  name: 'Nova Usuária',
  email: 'nova@example.com',
  role: 'USER' as const,
  status: 'PENDING' as const,
  authProvider: 'LOCAL' as const,
  totpEnabled: false,
}

describe('usersAdmin store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('loads the user list', async () => {
    vi.mocked(usersAdminApi.findAll).mockResolvedValue([adminUser, pendingUser])

    const store = useUsersAdminStore()
    await store.load()

    expect(store.users).toEqual([adminUser, pendingUser])
  })

  it('approve replaces the user in place', async () => {
    vi.mocked(usersAdminApi.findAll).mockResolvedValue([pendingUser])
    const approved = { ...pendingUser, status: 'APPROVED' as const }
    vi.mocked(usersAdminApi.approve).mockResolvedValue(approved)

    const store = useUsersAdminStore()
    await store.load()
    await store.approve(pendingUser.id)

    expect(usersAdminApi.approve).toHaveBeenCalledWith(pendingUser.id)
    expect(store.users[0].status).toBe('APPROVED')
  })

  it('remove drops the user from the list', async () => {
    vi.mocked(usersAdminApi.findAll).mockResolvedValue([adminUser, pendingUser])
    vi.mocked(usersAdminApi.remove).mockResolvedValue(undefined)

    const store = useUsersAdminStore()
    await store.load()
    await store.remove(pendingUser.id)

    expect(usersAdminApi.remove).toHaveBeenCalledWith(pendingUser.id)
    expect(store.users).toEqual([adminUser])
  })
})
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd client && npm run test -- src/stores/auth.test.ts src/stores/usersAdmin.test.ts`
Expected: FAIL — `authApi.register`, `usersAdminApi`, `useUsersAdminStore` don't exist yet;
`SessionResponse` object literals are missing the new `status` field (a TypeScript build error,
not necessarily a vitest runtime failure — confirm with `npm run build` too once implemented).

- [ ] **Step 3: Add `UserStatus`, extend `SessionResponse`, and add `UserAdminResponse` to types.ts**

In `client/src/types.ts`, change:

```typescript
export interface SessionResponse {
  name: string
  email: string
  role: UserRole
}
```

to:

```typescript
export type UserStatus = 'PENDING' | 'APPROVED' | 'REJECTED'

export interface SessionResponse {
  name: string
  email: string
  role: UserRole
  status: UserStatus
}

export interface UserAdminResponse {
  id: string
  name: string
  email: string
  role: UserRole
  status: UserStatus
  authProvider: 'LOCAL' | 'GOOGLE'
  totpEnabled: boolean
}
```

- [ ] **Step 4: Add `register` to `api/auth.ts`**

In `client/src/api/auth.ts`, add this method to the `authApi` object, right after `verify`:

```typescript
  register(name: string, email: string, password: string): Promise<void> {
    return apiClient.post('/auth/register', { name, email, password }).then(() => undefined)
  },
```

- [ ] **Step 5: Create `api/usersAdmin.ts`**

Create `client/src/api/usersAdmin.ts`:

```typescript
import { apiClient } from './client'
import type { PagedResponse, UserAdminResponse, UserRole } from '@/types'

export const usersAdminApi = {
  findAll(): Promise<UserAdminResponse[]> {
    return apiClient
      .get<PagedResponse<UserAdminResponse>>('/users', { params: { size: 200 } })
      .then((response) => response.data.content)
  },

  approve(id: string): Promise<UserAdminResponse> {
    return apiClient.patch<UserAdminResponse>(`/users/${id}/approve`).then((response) => response.data)
  },

  reject(id: string): Promise<UserAdminResponse> {
    return apiClient.patch<UserAdminResponse>(`/users/${id}/reject`).then((response) => response.data)
  },

  changeRole(id: string, role: UserRole): Promise<UserAdminResponse> {
    return apiClient.patch<UserAdminResponse>(`/users/${id}/role`, { role }).then((response) => response.data)
  },

  resetTotp(id: string): Promise<UserAdminResponse> {
    return apiClient.patch<UserAdminResponse>(`/users/${id}/totp-reset`).then((response) => response.data)
  },

  remove(id: string): Promise<void> {
    return apiClient.delete(`/users/${id}`).then(() => undefined)
  },
}
```

- [ ] **Step 6: Add `register` and `isAdmin` to `stores/auth.ts`**

In `client/src/stores/auth.ts`, add this action to the `useAuthStore` setup function, right after
`verifyTotp`:

```typescript

  async function register(name: string, email: string, password: string): Promise<void> {
    await authApi.register(name, email, password)
  }
```

Add this computed import and declaration. Change:

```typescript
import { ref } from 'vue'
```

to:

```typescript
import { computed, ref } from 'vue'
```

Add this, right after the `loading` ref declaration:

```typescript
  const isAdmin = computed(() => session.value?.role === 'ADMIN')
```

Change the final `return` statement to:

```typescript
  return { session, loading, isAdmin, login, enrollTotp, verifyTotp, register, logout, restoreSession }
```

- [ ] **Step 7: Create `stores/usersAdmin.ts`**

Create `client/src/stores/usersAdmin.ts`:

```typescript
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { usersAdminApi } from '@/api/usersAdmin'
import type { UserAdminResponse, UserRole } from '@/types'

export const useUsersAdminStore = defineStore('usersAdmin', () => {
  const users = ref<UserAdminResponse[]>([])
  const loaded = ref(false)
  const loading = ref(false)

  async function load() {
    if (loaded.value) return
    loading.value = true
    try {
      users.value = await usersAdminApi.findAll()
      loaded.value = true
    } finally {
      loading.value = false
    }
  }

  async function refresh() {
    loaded.value = false
    await load()
  }

  function replaceUser(updated: UserAdminResponse) {
    users.value = users.value.map((user) => (user.id === updated.id ? updated : user))
  }

  async function approve(id: string): Promise<void> {
    replaceUser(await usersAdminApi.approve(id))
  }

  async function reject(id: string): Promise<void> {
    replaceUser(await usersAdminApi.reject(id))
  }

  async function changeRole(id: string, role: UserRole): Promise<void> {
    replaceUser(await usersAdminApi.changeRole(id, role))
  }

  async function resetTotp(id: string): Promise<void> {
    replaceUser(await usersAdminApi.resetTotp(id))
  }

  async function remove(id: string): Promise<void> {
    await usersAdminApi.remove(id)
    users.value = users.value.filter((user) => user.id !== id)
  }

  return { users, loaded, loading, load, refresh, approve, reject, changeRole, resetTotp, remove }
})
```

- [ ] **Step 8: Run the tests to verify they pass**

Run: `cd client && npm run test -- src/stores/auth.test.ts src/stores/usersAdmin.test.ts`
Expected: PASS (9 + 3 = 12 tests).

- [ ] **Step 9: Run the full client suite and the build**

Run: `cd client && npm run test`
Expected: PASS, aside from the pre-existing unrelated `useFormat.spec.ts` failures — note in your
report any other failure and confirm it's pre-existing (check `git log`/`git blame` if unsure)
before treating it as unrelated.

Run: `cd client && npm run build`
Expected: no TypeScript errors. **Pay close attention here** — this task changes `SessionResponse`
to require `status`, so any existing test file that constructs a `SessionResponse` object literal
without one (e.g. `client/src/router/index.test.ts`, unmodified by this task) will fail to build.
If `npm run build` fails because of this, that file is Task 5's responsibility — note it in your
report as a known, deferred failure rather than fixing it yourself (fixing it here would
duplicate Task 5's own planned changes to that file). If `npm run test` also fails for the same
reason in that file, same rule: note it, don't fix it.

- [ ] **Step 10: Commit**

```bash
git add client/src/types.ts client/src/api/auth.ts client/src/api/usersAdmin.ts \
  client/src/stores/auth.ts client/src/stores/usersAdmin.ts \
  client/src/stores/auth.test.ts client/src/stores/usersAdmin.test.ts
git commit -m "feat(client): add registration and users-admin API/store support"
```

---

## Task 5: Client — registration UI and the pending-approval view

**Files:**
- Modify: `client/src/views/LoginView.vue`
- Modify: `client/src/views/LoginView.test.ts`
- Create: `client/src/views/PendingApprovalView.vue`
- Create: `client/src/views/PendingApprovalView.test.ts`
- Modify: `client/src/router/index.ts`
- Modify: `client/src/router/index.test.ts`
- Modify: `client/src/components/layout/TheTopNav.vue`

**Interfaces:**
- Consumes: `useAuthStore().register(name, email, password)`, `.isAdmin`, `.session.status`
  (Task 4).

- [ ] **Step 1: Write the failing LoginView and router tests**

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
  authApi: { login: vi.fn(), logout: vi.fn(), fetchSession: vi.fn(), enroll: vi.fn(), verify: vi.fn(), register: vi.fn() },
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
        { path: '/pending-approval', name: 'pending-approval', component: { template: '<div />' } },
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

  it('registers a new account and navigates to the pending-approval screen', async () => {
    const store = useAuthStore()
    const registerSpy = vi.spyOn(store, 'register').mockResolvedValue()
    router.push('/login')
    await router.isReady()

    const wrapper = mount(LoginView, { global: { plugins: [router, Buefy] } })
    await wrapper.find('[data-testid="toggle-register"]').trigger('click')
    await wrapper.find('input[type="text"]').setValue('Nova Usuária')
    await wrapper.find('input[type="email"]').setValue('nova@example.com')
    await wrapper.find('input[type="password"]').setValue('senha123')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(registerSpy).toHaveBeenCalledWith('Nova Usuária', 'nova@example.com', 'senha123')
    expect(router.currentRoute.value.name).toBe('pending-approval')
  })
})

function flushPromises() {
  return new Promise((resolve) => setTimeout(resolve, 0))
}
```

Create `client/src/views/PendingApprovalView.test.ts`:

```typescript
import { describe, expect, it, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import Buefy from 'buefy'
import PendingApprovalView from './PendingApprovalView.vue'
import { useAuthStore } from '@/stores/auth'

describe('PendingApprovalView', () => {
  let router: ReturnType<typeof createRouter>

  beforeEach(() => {
    setActivePinia(createPinia())
    router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/login', name: 'login', component: { template: '<div />' } },
        { path: '/pending-approval', name: 'pending-approval', component: PendingApprovalView },
      ],
    })
  })

  it('shows a generic message when there is no session', async () => {
    router.push('/pending-approval')
    await router.isReady()

    const wrapper = mount(PendingApprovalView, { global: { plugins: [router, Buefy] } })

    expect(wrapper.text()).toContain('Cadastro enviado')
  })

  it('shows a personalized message when a pending session exists', async () => {
    const auth = useAuthStore()
    auth.session = { name: 'Nova Usuária', email: 'nova@example.com', role: 'USER', status: 'PENDING' }
    router.push('/pending-approval')
    await router.isReady()

    const wrapper = mount(PendingApprovalView, { global: { plugins: [router, Buefy] } })

    expect(wrapper.text()).toContain('Nova Usuária')
  })
})
```

Replace the full contents of `client/src/router/index.test.ts`:

```typescript
import { describe, expect, it, beforeEach, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '@/stores/auth'

vi.mock('@/api/auth', () => ({
  authApi: { login: vi.fn(), logout: vi.fn(), fetchSession: vi.fn(), enroll: vi.fn(), verify: vi.fn(), register: vi.fn() },
}))

describe('router auth guard', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('redirects to /login when there is no session', async () => {
    vi.resetModules()
    const { router } = await import('./index')
    router.push('/wallets')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('login')
  })

  it('allows navigation when an approved session exists', async () => {
    vi.resetModules()
    const auth = useAuthStore()
    auth.session = { name: 'Administrador', email: 'admin@admin.com', role: 'ADMIN', status: 'APPROVED' }

    const { router } = await import('./index')
    router.push('/wallets')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('wallets')
  })

  it('redirects a pending session to /pending-approval', async () => {
    vi.resetModules()
    const auth = useAuthStore()
    auth.session = { name: 'Nova Usuária', email: 'nova@example.com', role: 'USER', status: 'PENDING' }

    const { router } = await import('./index')
    router.push('/wallets')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('pending-approval')
  })

  it('redirects an approved session away from /pending-approval', async () => {
    vi.resetModules()
    const auth = useAuthStore()
    auth.session = { name: 'Administrador', email: 'admin@admin.com', role: 'ADMIN', status: 'APPROVED' }

    const { router } = await import('./index')
    router.push('/pending-approval')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('overview')
  })

  it('redirects a non-admin away from /settings', async () => {
    vi.resetModules()
    const auth = useAuthStore()
    auth.session = { name: 'Usuário Comum', email: 'user@example.com', role: 'USER', status: 'APPROVED' }

    const { router } = await import('./index')
    router.push('/settings')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('overview')
  })
})
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd client && npm run test -- src/views/LoginView.test.ts src/views/PendingApprovalView.test.ts src/router/index.test.ts`
Expected: FAIL — `PendingApprovalView.vue` doesn't exist, the register toggle/form doesn't exist,
the router doesn't yet have the new route/guard behavior.

- [ ] **Step 3: Add the registration step to LoginView.vue**

Replace the full contents of `client/src/views/LoginView.vue`:

```vue
<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import LogoMark from '@/components/icons/LogoMark.vue'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()

type Step = 'credentials' | 'register' | 'enroll' | 'totp'

const step = ref<Step>('credentials')
const name = ref('')
const email = ref('')
const password = ref('')
const totpCode = ref('')
const qrCodeDataUri = ref('')
const error = ref('')
const submitting = ref(false)

const title = computed(() => {
  if (step.value === 'register') return 'Criar conta'
  if (step.value === 'enroll') return 'Configure a autenticação em duas etapas'
  if (step.value === 'totp') return 'Confirme o código de autenticação'
  return 'Bem-vindo de volta'
})

const subtitle = computed(() => {
  if (step.value === 'register') return 'Sua conta ficará pendente até que um administrador a aprove.'
  if (step.value === 'enroll') return 'Escaneie o QR code com um aplicativo autenticador e digite o código gerado.'
  if (step.value === 'totp') return 'Digite o código do seu aplicativo autenticador.'
  return 'Entre para acompanhar seus investimentos.'
})

function toggleRegister() {
  error.value = ''
  step.value = step.value === 'register' ? 'credentials' : 'register'
}

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

async function submitRegistration() {
  error.value = ''
  submitting.value = true
  try {
    await auth.register(name.value, email.value, password.value)
    await router.push({ name: 'pending-approval' })
  } catch {
    error.value = 'Não foi possível concluir o cadastro. Verifique os dados e tente novamente.'
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
          <button type="button" class="auth-toggle" data-testid="toggle-register" @click="toggleRegister">
            Não tem uma conta? Criar conta
          </button>
        </form>

        <form v-else-if="step === 'register'" class="form-stack" @submit.prevent="submitRegistration">
          <b-field label="Nome">
            <b-input v-model="name" type="text" placeholder="Seu nome" required />
          </b-field>
          <b-field label="E-mail">
            <b-input v-model="email" type="email" placeholder="voce@email.com" required />
          </b-field>
          <b-field label="Senha">
            <b-input v-model="password" type="password" placeholder="••••••••" required />
          </b-field>
          <b-button type="is-primary" expanded native-type="submit" :loading="submitting" class="auth-submit">
            Criar conta
          </b-button>
          <button type="button" class="auth-toggle" data-testid="toggle-register" @click="toggleRegister">
            Já tem uma conta? Entrar
          </button>
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

- [ ] **Step 4: Add the `.auth-toggle` style**

In `client/src/assets/styles.css`, add this line right after the existing `.auth-totp-qr{...}`
rule (in the `/* ===== Auth / login ===== */` section, before the `@media(max-width:860px){` block):

```css
.auth-toggle{background:none;border:none;color:var(--primary);font-size:13px;font-weight:600;cursor:pointer;text-align:center;margin-top:4px;padding:0;}
```

- [ ] **Step 5: Create PendingApprovalView.vue**

Create `client/src/views/PendingApprovalView.vue`:

```vue
<script setup lang="ts">
import { computed } from 'vue'
import { useAuthStore } from '@/stores/auth'
import LogoMark from '@/components/icons/LogoMark.vue'

const auth = useAuthStore()

const message = computed(() =>
  auth.session
    ? `Olá, ${auth.session.name}! Sua conta ainda está aguardando aprovação de um administrador.`
    : 'Cadastro enviado! Aguarde a aprovação de um administrador para acessar sua conta.',
)

async function logout() {
  await auth.logout()
}
</script>

<template>
  <div class="auth-root">
    <main class="auth-main">
      <div class="auth-card">
        <div class="auth-card-brand">
          <span class="brand-mark"><LogoMark :size="20" /></span>
          <span class="brand-name">Invest<b>Log</b></span>
        </div>
        <div class="auth-head">
          <h1 class="auth-title">Aguardando aprovação</h1>
          <p class="auth-sub">{{ message }}</p>
        </div>
        <b-button v-if="auth.session" type="is-ghost" expanded @click="logout">Sair</b-button>
        <RouterLink v-else :to="{ name: 'login' }">
          <b-button type="is-ghost" expanded>Voltar para o login</b-button>
        </RouterLink>
      </div>
    </main>
  </div>
</template>
```

- [ ] **Step 6: Update the router**

Replace the full contents of `client/src/router/index.ts`:

```typescript
import { createRouter, createWebHistory } from 'vue-router'

import OverviewView from '@/views/OverviewView.vue'
import WalletsView from '@/views/WalletsView.vue'
import InvestmentsView from '@/views/InvestmentsView.vue'
import SettingsView from '@/views/SettingsView.vue'
import LoginView from '@/views/LoginView.vue'
import PendingApprovalView from '@/views/PendingApprovalView.vue'
import { useAuthStore } from '@/stores/auth'

export const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', redirect: { name: 'overview' } },
    { path: '/login', name: 'login', component: LoginView },
    { path: '/pending-approval', name: 'pending-approval', component: PendingApprovalView },
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

const PUBLIC_ROUTE_NAMES = ['login', 'pending-approval']

router.beforeEach((to) => {
  const auth = useAuthStore()

  if (!PUBLIC_ROUTE_NAMES.includes(to.name as string) && !auth.session) {
    return { name: 'login' }
  }
  if (to.name === 'login' && auth.session) {
    return auth.session.status === 'APPROVED' ? { name: 'overview' } : { name: 'pending-approval' }
  }
  if (auth.session && auth.session.status !== 'APPROVED' && !PUBLIC_ROUTE_NAMES.includes(to.name as string)) {
    return { name: 'pending-approval' }
  }
  if (to.name === 'pending-approval' && auth.session?.status === 'APPROVED') {
    return { name: 'overview' }
  }
  if (to.name === 'settings' && auth.session && auth.session.role !== 'ADMIN') {
    return { name: 'overview' }
  }
  return true
})
```

- [ ] **Step 7: Hide the "Configurações" nav item for non-admins**

In `client/src/components/layout/TheTopNav.vue`, add this import:

```typescript
import { computed } from 'vue'
import { useAuthStore } from '@/stores/auth'
```

(combine with the existing `import { onMounted } from 'vue'` into one `vue` import line).

Change:

```typescript
const nav: { name: string; label: string; icon: string }[] = [
  { name: 'overview', label: 'Visão geral', icon: 'view-dashboard-outline' },
  { name: 'wallets', label: 'Carteiras', icon: 'wallet-outline' },
  { name: 'investments', label: 'Investimentos', icon: 'layers-outline' },
  { name: 'settings', label: 'Configurações', icon: 'cog-outline' },
]
```

to:

```typescript
const auth = useAuthStore()

const nav = computed(() => {
  const items = [
    { name: 'overview', label: 'Visão geral', icon: 'view-dashboard-outline' },
    { name: 'wallets', label: 'Carteiras', icon: 'wallet-outline' },
    { name: 'investments', label: 'Investimentos', icon: 'layers-outline' },
  ]
  if (auth.isAdmin) {
    items.push({ name: 'settings', label: 'Configurações', icon: 'cog-outline' })
  }
  return items
})
```

And in the template, change `v-for="item in nav"` to `v-for="item in nav"` (unchanged — `nav` is
now a computed ref, and Vue templates auto-unwrap top-level refs, so no template change is
needed beyond the script).

- [ ] **Step 8: Run the tests to verify they pass**

Run: `cd client && npm run test -- src/views/LoginView.test.ts src/views/PendingApprovalView.test.ts src/router/index.test.ts`
Expected: PASS (4 + 2 + 5 = 11 tests).

- [ ] **Step 9: Run the full client suite and the build**

Run: `cd client && npm run test`
Expected: PASS aside from the pre-existing unrelated `useFormat.spec.ts` failures.

Run: `cd client && npm run build`
Expected: no TypeScript errors.

- [ ] **Step 10: Commit**

```bash
git add client/src/views/LoginView.vue client/src/views/LoginView.test.ts \
  client/src/views/PendingApprovalView.vue client/src/views/PendingApprovalView.test.ts \
  client/src/router/index.ts client/src/router/index.test.ts \
  client/src/components/layout/TheTopNav.vue client/src/assets/styles.css
git commit -m "feat(client): add registration UI, the pending-approval view, and role-based nav gating"
```

---

## Task 6: Client — "Usuários locais" admin section

**Files:**
- Modify: `client/src/views/SettingsView.vue`

**Interfaces:**
- Consumes: `useUsersAdminStore()` (Task 4), `useAuthStore().session` (for identifying "yourself"
  in the list, to hide self-targeting destructive actions client-side — the server also enforces
  this, this is purely a UX nicety).

Since `/settings` is already redirect-guarded to admins only (Task 5), no additional
admin-only conditional is needed inside this view — reaching it at all already implies the
current session is an admin.

- [ ] **Step 1: Add the "Usuários locais" card to SettingsView.vue**

In `client/src/views/SettingsView.vue`, add these imports alongside the existing ones:

```typescript
import { useDialog, useToast } from 'buefy'
import { useUsersAdminStore } from '@/stores/usersAdmin'
import { useAuthStore } from '@/stores/auth'
import type { UserRole } from '@/types'
```

(the file already imports `useToast` — combine into one `buefy` import line: `import { useDialog, useToast } from 'buefy'`).

Add these right after the existing store instantiations (`const typesListStore = ...`, etc.):

```typescript
const dialog = useDialog()
const usersAdminStore = useUsersAdminStore()
const auth = useAuthStore()
```

Change the `onMounted` call to also load the users list:

```typescript
onMounted(() => {
  Promise.all([typesListStore.load(), ratesStore.load(), usersAdminStore.load()])
})
```

Add these functions, anywhere after the existing ones in the `<script setup>` block:

```typescript
function isSelf(email: string): boolean {
  return auth.session?.email === email
}

async function approveUser(id: string) {
  await usersAdminStore.approve(id)
  toast.open({ message: 'Usuário aprovado.', type: 'is-success' })
}

async function rejectUser(id: string) {
  await usersAdminStore.reject(id)
  toast.open({ message: 'Usuário rejeitado.', type: 'is-success' })
}

function confirmRoleChange(id: string, name: string, currentRole: UserRole) {
  const nextRole: UserRole = currentRole === 'ADMIN' ? 'USER' : 'ADMIN'
  dialog.confirm({
    title: nextRole === 'ADMIN' ? 'Promover a administrador' : 'Remover privilégios de administrador',
    message: `Alterar o papel de <strong>${name}</strong> para <strong>${nextRole}</strong>?`,
    confirmText: 'Confirmar',
    cancelText: 'Cancelar',
    onConfirm: async () => {
      await usersAdminStore.changeRole(id, nextRole)
      toast.open({ message: 'Papel atualizado.', type: 'is-success' })
    },
  })
}

function confirmTotpReset(id: string, name: string) {
  dialog.confirm({
    title: 'Redefinir autenticação em duas etapas',
    message: `<strong>${name}</strong> precisará configurar a autenticação novamente no próximo login.`,
    type: 'is-danger',
    hasIcon: true,
    confirmText: 'Redefinir',
    cancelText: 'Cancelar',
    onConfirm: async () => {
      await usersAdminStore.resetTotp(id)
      toast.open({ message: 'Autenticação em duas etapas redefinida.', type: 'is-success' })
    },
  })
}

function confirmDeleteUser(id: string, name: string) {
  dialog.confirm({
    title: 'Remover usuário',
    message: `Remover <strong>${name}</strong>. Esta ação <strong>não pode ser desfeita</strong>.`,
    type: 'is-danger',
    hasIcon: true,
    confirmText: 'Remover',
    cancelText: 'Cancelar',
    onConfirm: async () => {
      await usersAdminStore.remove(id)
      toast.open({ message: 'Usuário removido.', type: 'is-success' })
    },
  })
}
```

Add this new `<Card>` block in the template, right after the closing `</Card>` of the "Aparência"
section (the last one in the file, right before the closing `</div>` of `.page-narrow`):

```html

    <Card>
      <CardBody>
        <b-loading :is-full-page="false" :active="usersAdminStore.loading" />
        <div class="set-head"><h2 class="set-title">Usuários locais</h2></div>
        <p class="set-desc">Aprove, rejeite ou gerencie o acesso de usuários locais.</p>
        <div class="wallet-grid">
          <Card v-for="user in usersAdminStore.users" :key="user.id" class="wallet-card">
            <CardBody>
              <div class="wallet-head">
                <div class="wallet-titles">
                  <div class="wallet-name">{{ user.name }}</div>
                  <div class="wallet-tags">
                    <b-tag :type="user.role === 'ADMIN' ? 'is-link' : 'is-light'">{{ user.role }}</b-tag>
                    <b-tag
                      :type="
                        user.status === 'APPROVED' ? 'is-success' : user.status === 'REJECTED' ? 'is-danger' : 'is-warning'
                      "
                    >
                      {{ user.status }}
                    </b-tag>
                    <b-tag v-if="user.totpEnabled" type="is-info">2FA ativo</b-tag>
                  </div>
                </div>
              </div>
              <p class="set-desc">{{ user.email }}</p>
              <div class="wallet-foot" style="flex-wrap: wrap; gap: 6px">
                <b-button
                  v-if="user.status !== 'APPROVED'"
                  size="is-small"
                  type="is-success"
                  outlined
                  @click="approveUser(user.id)"
                >
                  Aprovar
                </b-button>
                <b-button
                  v-if="user.status !== 'REJECTED'"
                  size="is-small"
                  type="is-warning"
                  outlined
                  @click="rejectUser(user.id)"
                >
                  Rejeitar
                </b-button>
                <template v-if="!isSelf(user.email)">
                  <b-button size="is-small" type="is-link" outlined @click="confirmRoleChange(user.id, user.name, user.role)">
                    {{ user.role === 'ADMIN' ? 'Remover admin' : 'Promover a admin' }}
                  </b-button>
                  <b-button size="is-small" type="is-info" outlined @click="confirmTotpReset(user.id, user.name)">
                    Redefinir 2FA
                  </b-button>
                  <b-button size="is-small" type="is-danger" outlined @click="confirmDeleteUser(user.id, user.name)">
                    Remover
                  </b-button>
                </template>
              </div>
            </CardBody>
          </Card>
        </div>
      </CardBody>
    </Card>
```

- [ ] **Step 2: Run the full client suite and the build**

There is no dedicated test file for this task's UI (the underlying `usersAdmin` store actions are
already covered by Task 4's `usersAdmin.test.ts`, and this view follows the same established,
untested-at-the-component-level pattern as the rest of `SettingsView.vue`, which also has no
component test today).

Run: `cd client && npm run test`
Expected: PASS aside from the pre-existing unrelated `useFormat.spec.ts` failures.

Run: `cd client && npm run build`
Expected: no TypeScript errors.

- [ ] **Step 3: Commit**

```bash
git add client/src/views/SettingsView.vue
git commit -m "feat(client): add the Usuários locais admin section to Settings"
```

---

## Task 7: Client — hide destructive actions for non-admin users

**Files:**
- Modify: `client/src/views/WalletsView.vue`
- Modify: `client/src/components/investments/HoldingDetailPanel.vue`
- Modify: `client/src/views/SettingsView.vue`

**Interfaces:**
- Consumes: `useAuthStore().isAdmin` (Task 4).

There is no dedicated test file for this task — it's a mechanical, low-risk `v-if` wrap around
seven already-existing, already-tested buttons across three files. Verify manually (see Step 5)
rather than adding component tests for a pattern this codebase doesn't test at this level anywhere
else (`WalletsView.vue`/`HoldingDetailPanel.vue`/`SettingsView.vue` have no existing component
test files).

- [ ] **Step 1: Gate the wallet delete button**

In `client/src/views/WalletsView.vue`, add this import:

```typescript
import { useAuthStore } from '@/stores/auth'
```

Add this alongside the other store instantiations:

```typescript
const auth = useAuthStore()
```

Change:

```html
              <b-button
                  outlined
                  type="is-danger"
                size="is-small"
                icon-left="delete"
                @click.stop="confirmDeleteWallet(wallet.id, wallet.name)"
              />
```

to:

```html
              <b-button
                  v-if="auth.isAdmin"
                  outlined
                  type="is-danger"
                size="is-small"
                icon-left="delete"
                @click.stop="confirmDeleteWallet(wallet.id, wallet.name)"
              />
```

- [ ] **Step 2: Gate the three delete buttons in HoldingDetailPanel.vue**

In `client/src/components/investments/HoldingDetailPanel.vue`, add this import:

```typescript
import { useAuthStore } from '@/stores/auth'
```

Add this alongside the other consts:

```typescript
const auth = useAuthStore()
```

Change the contribution-row delete button:

```html
            <td class="c-act">
              <b-button
                outlined
                type="is-danger"
                size="is-small"
                icon-left="delete"
                @click.stop="confirmDeleteContribution(contribution.id)"
              />
            </td>
```

to:

```html
            <td class="c-act">
              <b-button
                v-if="auth.isAdmin"
                outlined
                type="is-danger"
                size="is-small"
                icon-left="delete"
                @click.stop="confirmDeleteContribution(contribution.id)"
              />
            </td>
```

Change the lot-row delete button:

```html
            <td class="c-act">
              <b-button
                outlined
                type="is-danger"
                size="is-small"
                icon-left="delete"
                @click.stop="confirmDeleteLot(lot.id)"
              />
            </td>
```

to:

```html
            <td class="c-act">
              <b-button
                v-if="auth.isAdmin"
                outlined
                type="is-danger"
                size="is-small"
                icon-left="delete"
                @click.stop="confirmDeleteLot(lot.id)"
              />
            </td>
```

Change the "Remover" (delete holding) footer button:

```html
      <b-button outlined type="is-danger" size="is-small" icon-left="delete" @click="confirmRemove">
        Remover
      </b-button>
```

to:

```html
      <b-button v-if="auth.isAdmin" outlined type="is-danger" size="is-small" icon-left="delete" @click="confirmRemove">
        Remover
      </b-button>
```

- [ ] **Step 3: Gate the two "remove type" buttons in SettingsView.vue**

In `client/src/views/SettingsView.vue`, change the stock-type remove button:

```html
            <button
              :aria-label="`Remover ${stockType.name}`"
              @click="removeStockType(stockType.id)"
            >
              <b-icon icon="close" size="is-small" />
            </button>
```

to:

```html
            <button
              v-if="auth.isAdmin"
              :aria-label="`Remover ${stockType.name}`"
              @click="removeStockType(stockType.id)"
            >
              <b-icon icon="close" size="is-small" />
            </button>
```

Change the fund-type remove button:

```html
            <button :aria-label="`Remover ${fundType.name}`" @click="removeFundType(fundType.id)">
              <b-icon icon="close" size="is-small" />
            </button>
```

to:

```html
            <button v-if="auth.isAdmin" :aria-label="`Remover ${fundType.name}`" @click="removeFundType(fundType.id)">
              <b-icon icon="close" size="is-small" />
            </button>
```

(`auth` is already in scope from Task 6's changes to this same file — no new import needed here.)

- [ ] **Step 4: Run the full client suite and the build**

Run: `cd client && npm run test`
Expected: PASS aside from the pre-existing unrelated `useFormat.spec.ts` failures.

Run: `cd client && npm run build`
Expected: no TypeScript errors.

- [ ] **Step 5: Manual verification note**

Since `/settings` is already admin-only (Task 5's router guard), the `SettingsView.vue` changes in
this task are unreachable by a non-admin anyway — belt-and-suspenders only. The genuinely
observable changes are in `WalletsView.vue` and `HoldingDetailPanel.vue`, which non-admins DO
reach. Note in your report that this needs a manual check once both this PR and the server PR are
running together: log in as a non-admin (`role: USER`) and confirm the wallet-delete and the
three holding-detail delete buttons are absent, while everything else (add position, update
price, rename) remains visible.

- [ ] **Step 6: Commit**

```bash
git add client/src/views/WalletsView.vue client/src/components/investments/HoldingDetailPanel.vue \
  client/src/views/SettingsView.vue
git commit -m "feat(client): hide destructive actions on wallets and holdings for non-admin users"
```

- [ ] **Step 7: Push the branch and open the client PR**

```bash
git push -u origin feature/auth-phase3-registration-client
```

Open a PR from `feature/auth-phase3-registration-client` into `feature/auth-phase2-totp-client`
(a stacked PR, not into `feature/authentication`). Title:
`feat(auth): Phase 3 — self-registration and admin approval (client)`. Reference `Refs #3`, add
the `feature` label, assign to `arthurgregorio`. Summarize Tasks 4-7.
