# Authentication Phase 4 — Google OAuth2 Login Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Google OAuth2 login, toggled on/off via an environment variable. Google users go
through the exact same admin-approval gate as local users, and skip InvestLog's own TOTP gate
(Google's own account security is the trust boundary for a federated identity).

**Architecture:** A conditionally-registered `ClientRegistrationRepository` bean (present only
when `GOOGLE_AUTH_ENABLED=true`) turns on Spring Security's `oauth2Login()` DSL inside the
existing `SecurityConfig` filter chain. The OAuth2 authorization/redirect endpoints are relocated
under `/private/**` — the only path prefix nginx (prod) and Vite (dev) actually proxy to the
server — via `authorizationEndpoint`/`redirectionEndpoint` base-URI overrides. A custom
`AuthenticationSuccessHandler` intercepts the moment Spring Security has verified the Google
identity, upserts the corresponding `CurrentUser` row by `google_sub`, and hands off to
`AuthService`'s existing, private `establishSession` — the single session-issuing chokepoint
already used by local login and TOTP verification (see `server/CLAUDE.md`) — so the resulting
session carries the same `ROLE_${role}`/`STATUS_${status}` authorities the rest of the filter
chain already understands, regardless of how the user signed in.

**Tech Stack:** `spring-boot-starter-oauth2-client` (new dependency), Spring Security's
`CommonOAuth2Provider.GOOGLE` provider preset. No new client dependencies — the Google button is
a plain anchor tag triggering a full browser navigation, not an API call.

## Global Constraints

- No abbreviated names — full descriptive names everywhere (server: `server/CLAUDE.md`; client:
  `client/CLAUDE.md`).
- No new Liquibase migration. `system.users.google_sub` is already nullable and `UNIQUE` (dropped
  `NOT NULL` in `28-1000-add-auth-columns-to-users.xml`, Phase 1) and `AuthProvider.GOOGLE` already
  exists as an unused enum value — the schema has been Google-ready since Phase 1.
- Per the repo's root `CLAUDE.md`: every layer gets its own branch/PR. All three branches
  (`feature/auth-phase4-google-oauth-server`, `-client`, `-docs`) branch from the current tip of
  `feature/authentication` and each PR targets `feature/authentication` directly — Phases 1-3 are
  now fully merged into that branch, so there is no more stacked-PR chain to maintain.
- Every commit and PR references `Refs #4` (this phase's tracking issue). Since three PRs address
  it, none of them use `Closes #4` — close the issue manually once all three have merged.
- Every PR gets the `feature` label and is assigned to `arthurgregorio`.
- TOTP enrollment/verification is untouched by this phase and stays local-account-only — see
  Design Decisions below for why Google sessions don't go through it.

## Design decisions (read before implementing)

**Why the OAuth2 authorization/redirect endpoints move under `/private/**`:** verified against
both `client/nginx.conf` (prod) and `client/vite.config.ts` (dev) — each proxies only the
`/private` path prefix to the server; every other path is served the SPA's static files
(`try_files ... /index.html` in nginx). Spring Security's OAuth2 login defaults
(`/oauth2/authorization/{registrationId}`, `/login/oauth2/code/{registrationId}`) don't start with
`/private`, so a browser hitting them through the SPA's origin would get routed to the client's
`index.html`, not the server. `authorizationEndpoint { baseUri = "/private/oauth2/authorization" }`
and `redirectionEndpoint { baseUri = "/private/login/oauth2/code/*" }` fix this — matching the
original design spec's redirect URI (`http(s)://<host>/private/login/oauth2/code/google`), not the
shorter, unprefixed one in the GitHub issue's summary text.

**Why the `ClientRegistrationRepository` is built manually instead of via Spring Boot's standard
`spring.security.oauth2.client.registration.*` YAML autoconfiguration:** when `GOOGLE_AUTH_ENABLED`
is `false` (the default), `GOOGLE_CLIENT_ID`/`GOOGLE_CLIENT_SECRET` are empty strings. Whether
Spring Boot's `OAuth2ClientAutoConfiguration` cleanly no-ops on blank-but-present registration
properties, versus attempting to build a `ClientRegistration` and failing at startup, isn't
something to gamble on. Instead, `GoogleOAuth2Config` builds the `ClientRegistrationRepository`
bean itself, using `CommonOAuth2Provider.GOOGLE`'s builder (Spring Security's own well-known
preset for Google's endpoints/scopes), gated behind `@ConditionalOnProperty` on InvestLog's own
`investlog.google-auth-enabled` property — this sidesteps Spring Boot's OAuth2 client
autoconfiguration entirely (it only activates based on the standard `spring.security.oauth2.*`
namespace, which this app never populates) and makes the enabled/disabled behavior fully explicit
and testable. `securityFilterChain` injects `ClientRegistrationRepository?` (nullable — the bean
may not exist) and only calls `oauth2Login { ... }` when it's non-null.

**Why the Google success handler funnels through `AuthService.handleGoogleLogin` →
`establishSession` instead of just accepting Spring Security's default OAuth2 authentication:**
by default, `oauth2Login()` stores a generic `OAuth2AuthenticationToken` (wrapping an `OAuth2User`)
as the session's `Authentication` once Google's handshake succeeds — that principal is not
InvestLog's `CurrentUser` domain type and carries none of the `ROLE_${role}`/`STATUS_${status}`
authorities the rest of the filter chain (`hasAuthority("STATUS_APPROVED")`, `/users/**`'s
`ROLE_ADMIN` gate) and every business service's `CurrentUserProvider` re-check depend on. The
custom `GoogleLoginSuccessHandler` reads the Google user's `sub`/`email`/`name`/`picture` claims,
upserts a `CurrentUser` row (creating one with `role=USER, status=PENDING, authProvider=GOOGLE` on
first login, matching local registration's defaults), and calls `AuthService.handleGoogleLogin`,
which calls the *same private `establishSession`* method `login()`/`verifyTotp()` already use —
overwriting the filter's default OAuth2 authentication with a proper `CurrentUser`-principal one
before the request completes. `establishSession` staying the single session-issuing chokepoint
(already documented in `server/CLAUDE.md`) is exactly what makes this phase safe to add: every
authorization check downstream already treats every session uniformly, regardless of how it was
established.

**Why Google logins skip InvestLog's own TOTP gate:** TOTP enforcement (Phase 2) lives procedurally
inside `AuthService.login()`/`verifyTotp()` — checking `user.totpEnabled` before ever calling
`establishSession` — not as a session authority the filter chain checks. `handleGoogleLogin` calls
`establishSession` directly, bypassing that TOTP check entirely, by design: a Google account
already has its own account-security surface (which may include the user's own 2FA on Google's
side), and there's no `totp_secret` to enroll against for a Google-only identity unless the design
scope grows to support linking a Google login to a locally-enrolled TOTP secret — out of scope here
per the original design spec ("Out of scope: Backup codes for 2FA recovery... Multi-tenancy" —
account linking was never part of this design).

**Why a duplicate email is rejected instead of auto-linked:** if a Google login's email already
belongs to an existing user (most likely a local account registered before that person ever used
"Continuar com Google"), silently linking the two accounts together is a real decision with data
implications (which password stays valid? which `totp_enabled` wins?) that the original design
never asked for — the safest, most honest behavior for a two-line edge case in a personal app is
to reject the login attempt and tell the person to use their existing sign-in method.
`handleGoogleLogin` throws `GoogleAccountEmailInUseException`; `GoogleLoginSuccessHandler` catches
it directly (an `AuthenticationSuccessHandler` runs outside the normal
`@RestControllerAdvice`/`GlobalExceptionHandler` pipeline — an uncaught exception here would
surface as a raw, unhandled 500 from the servlet container, not a clean API error) and redirects to
`/login?error=email_in_use` instead.

**Why `application-prod.yaml` needs `server.forward-headers-strategy: framework`:** Spring
computes the `redirect_uri` it sends to Google in the authorization request from the *incoming
request's* scheme/host — but in production that request arrives at the server container already
proxied by nginx (`client/nginx.conf`'s `location /private { proxy_pass http://server:8080; ... }`,
which does set `X-Forwarded-*` headers). Without `forward-headers-strategy: framework`, Spring
ignores those headers and computes `redirect_uri` from the internal `http://server:8080/...`
address — which won't match the redirect URI registered in Google Cloud Console, and the OAuth2
handshake will fail in prod even though it works in local dev (where the server is hit directly).
Confirmed neither `application.yaml` nor the pre-existing `application-prod.yaml` sets this today.

**Test coverage boundary, chosen deliberately:** driving a real Google OAuth2 handshake
end-to-end through an integration test would mean standing up a fake authorization server and is
not worth the complexity for a two-person self-hosted app. Instead: (1) an integration test proves
`GET /auth/config` correctly reports `googleAuthEnabled: false` in the default (disabled) test
profile — the only server-observable behavior that differs when the feature is off, which is the
state every other existing test already runs under, so this also implicitly proves nothing else
regressed; (2) a direct, HTTP-bypassing test of `AuthService.handleGoogleLogin` (called on the
Spring-managed bean directly, the same pattern `BaseIntegrationTest` subclasses already use for
autowiring) exercises the real logic: first-login user creation, idempotent re-login by
`google_sub`, the duplicate-email rejection, and — this is the one that actually matters — that the
resulting `HttpSession`'s persisted `SecurityContext` carries `ROLE_USER`/`STATUS_PENDING`
authorities, proving the principal-swap via `establishSession` genuinely took effect and isn't
silently still the default `OAuth2AuthenticationToken`. What this boundary does **not** cover: the
real Spring Security filter invoking `GoogleLoginSuccessHandler` in response to an actual completed
handshake. Once real Google OAuth2 credentials are configured, do one manual check: log in with
"Continuar com Google", then hit `GET /private/v1/auth/session` — success looks like a normal
`SessionResponse` body (name/email/role/status), because `AuthService.currentSession()` explicitly
casts the session's principal to `CurrentUser` and throws if that cast fails. This is the single
manual step this plan cannot automate away.

## File Structure

**Server** (`br.com.investlog.server`):
- `build.gradle.kts` — modified, add `spring-boot-starter-oauth2-client`.
- `shared/security/GoogleOAuth2Config.kt` — new, the conditional `ClientRegistrationRepository`
  bean.
- `shared/security/UserRepository.kt` — modified, add `createGoogleUser`.
- `shared/exceptions/GoogleAccountEmailInUseException.kt` — new. Deliberately **not** mapped in
  `GlobalExceptionHandler` — it's caught directly inside `GoogleLoginSuccessHandler`, never reaches
  REST exception handling.
- `auth/security/GoogleLoginSuccessHandler.kt`, `auth/security/GoogleLoginFailureHandler.kt` — new
  package, OAuth2-specific security wiring that depends on `AuthService` (keeping `config/` thin
  and this dependency direction consistent with how `AuthController` already depends on
  `AuthService`).
- `auth/domain/services/AuthService.kt` — modified, add `handleGoogleLogin` and `authConfig`.
- `auth/rest/payloads/AuthConfigResponse.kt` — new.
- `auth/rest/controllers/AuthController.kt` — modified, add `GET /auth/config`.
- `config/SecurityConfig.kt` — modified, permit the new OAuth2 paths and `/auth/config`;
  conditionally wire `oauth2Login`.
- `src/main/resources/application.yaml` — modified, bind `investlog.google-auth-enabled`/
  `google-client-id`/`google-client-secret`.
- `src/main/resources/application-prod.yaml` — modified, add `server.forward-headers-strategy`.
- Tests: `AuthConfigControllerTest.kt` (new), `AuthServiceGoogleLoginTest.kt` (new).

**Client** (`client/src`):
- `types.ts` — modified, add `AuthConfigResponse`.
- `api/auth.ts` — modified, add `fetchConfig`.
- `views/LoginView.vue` — modified, fetch config on mount, show/hide the Google button, surface
  `?error=` query-param messages.
- `assets/styles.css` — modified, add `.auth-divider`/`.auth-google-button` rules.
- Tests: `views/LoginView.test.ts` — modified.

**Docs:**
- `README.md` — modified, new "Google OAuth2 login (optional)" section + three new rows in the
  Configuration table.
- `.env.example` — modified, three new variables.

---

## Task 1: Conditional Google client registration + `GET /auth/config`

**Branch:** `feature/auth-phase4-google-oauth-server` (create from the current tip of
`feature/authentication`)

**Files:**
- Modify: `server/build.gradle.kts`
- Create: `server/src/main/kotlin/br/com/investlog/server/shared/security/GoogleOAuth2Config.kt`
- Create: `server/src/main/kotlin/br/com/investlog/server/auth/rest/payloads/AuthConfigResponse.kt`
- Modify: `server/src/main/kotlin/br/com/investlog/server/auth/domain/services/AuthService.kt`
- Modify: `server/src/main/kotlin/br/com/investlog/server/auth/rest/controllers/AuthController.kt`
- Modify: `server/src/main/kotlin/br/com/investlog/server/config/SecurityConfig.kt`
- Modify: `server/src/main/resources/application.yaml`
- Modify: `server/src/main/resources/application-prod.yaml`
- Test: `server/src/test/kotlin/br/com/investlog/server/auth/rest/controllers/AuthConfigControllerTest.kt`

**Interfaces:**
- Produces: `AuthService.authConfig(): AuthConfigResponse`, `GET /private/v1/auth/config` (public)
  — consumed by Task 3 (client).
- Produces: `GoogleOAuth2Config`'s conditional `ClientRegistrationRepository` bean — consumed by
  Task 2, which wires it into `SecurityConfig`'s `oauth2Login` block.

- [ ] **Step 1: Add the `spring-boot-starter-oauth2-client` dependency**

In `server/build.gradle.kts`, add this line right after
`implementation("org.springframework.boot:spring-boot-starter-security")` in the `// spring
stuff` group:

```kotlin
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
```

- [ ] **Step 2: Bind the Google config properties**

Replace the full contents of `server/src/main/resources/application.yaml`:

```yaml
spring:
  application:
    name: server

  liquibase:
    change-log: classpath:db/changelog/db.changelog-master.xml
    database-change-log-table: database_changelog
    database-change-log-lock-table: database_changelog_lock

server:
  servlet:
    session:
      cookie:
        same-site: lax

investlog:
  admin-default-password: ${ADMIN_DEFAULT_PASSWORD:admin}
  google-auth-enabled: ${GOOGLE_AUTH_ENABLED:false}
  google-client-id: ${GOOGLE_CLIENT_ID:}
  google-client-secret: ${GOOGLE_CLIENT_SECRET:}
```

- [ ] **Step 3: Add `forward-headers-strategy` to the prod profile**

Replace the full contents of `server/src/main/resources/application-prod.yaml`:

```yaml
spring:
  application:
    name: server
  datasource:
    url: jdbc:postgresql://${DB_HOST}:${DB_PORT:5432}/${DB_NAME}
    username: ${DB_USER}
    password: ${DB_PASSWORD}

server:
  forward-headers-strategy: framework
```

- [ ] **Step 4: Create `GoogleOAuth2Config`**

Create `server/src/main/kotlin/br/com/investlog/server/shared/security/GoogleOAuth2Config.kt`:

```kotlin
package br.com.investlog.server.shared.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.registration.ClientRegistrations
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository

@Configuration
class GoogleOAuth2Config {

    // NOTE: CommonOAuth2Provider (a static, hardcoded-endpoint preset) was removed in Spring
    // Security 7.x (pulled in by Spring Boot 4.1.0 here). ClientRegistrations.fromIssuerLocation
    // is its replacement — it performs a blocking HTTP call to Google's OIDC issuer metadata
    // endpoint (https://accounts.google.com/.well-known/openid-configuration) at bean-creation
    // time, i.e. only when google-auth-enabled=true, to resolve authorizationUri/tokenUri/
    // jwkSetUri/userInfoUri. This requires network access to Google at startup when the feature
    // is enabled — an acceptable tradeoff for an opt-in feature configured with real credentials.
    @Bean
    @ConditionalOnProperty(prefix = "investlog", name = ["google-auth-enabled"], havingValue = "true")
    fun clientRegistrationRepository(
        @Value("\${investlog.google-client-id}") googleClientId: String,
        @Value("\${investlog.google-client-secret}") googleClientSecret: String,
    ): ClientRegistrationRepository {
        val googleRegistration = ClientRegistrations.fromIssuerLocation("https://accounts.google.com")
            .registrationId("google")
            .clientId(googleClientId)
            .clientSecret(googleClientSecret)
            .scope("openid", "profile", "email")
            .build()

        return InMemoryClientRegistrationRepository(googleRegistration)
    }
}
```

`ClientRegistrations.fromIssuerLocation(...)` does not default to any scopes — omitting `.scope(...)`
silently produces an empty scope set (bytecode-verified during Task 1's review), which would have
broken the actual Google handshake Task 2 builds on top of this bean.

**Verified deviation (discovered during Task 1's implementation, confirmed independently against
the resolved `spring-security-oauth2-client-7.1.0.jar`):** `CommonOAuth2Provider` genuinely does
not exist in this project's resolved Spring Security version — `ClientRegistrations` is the only
remaining built-in helper, and the code above (not the `CommonOAuth2Provider.GOOGLE.getBuilder(...)`
form referenced in this plan's Design Decisions section) is what Task 1 actually implemented and
what Task 2 builds on.

- [ ] **Step 5: Write the failing `GET /auth/config` test**

Create `server/src/test/kotlin/br/com/investlog/server/auth/rest/controllers/AuthConfigControllerTest.kt`:

```kotlin
package br.com.investlog.server.auth.rest.controllers

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.auth.rest.payloads.AuthConfigResponse
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.returnResult
import kotlin.test.assertEquals

class AuthConfigControllerTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTestClient: RestTestClient

    @Test
    fun `config reports google auth as disabled by default`() {
        val response = restTestClient.get()
            .uri("/private/v1/auth/config")
            .exchange()
            .expectStatus().isOk()
            .returnResult<AuthConfigResponse>()
            .responseBody

        assertEquals(false, response?.googleAuthEnabled)
    }
}
```

- [ ] **Step 6: Run the test to verify it fails**

Run: `cd server && ./gradlew test --tests "br.com.investlog.server.auth.rest.controllers.AuthConfigControllerTest"`
Expected: FAIL — compilation error (`AuthConfigResponse`, `/auth/config` don't exist yet).

- [ ] **Step 7: Create `AuthConfigResponse`**

Create `server/src/main/kotlin/br/com/investlog/server/auth/rest/payloads/AuthConfigResponse.kt`:

```kotlin
package br.com.investlog.server.auth.rest.payloads

data class AuthConfigResponse(val googleAuthEnabled: Boolean)
```

- [ ] **Step 8: Add `authConfig()` to `AuthService`**

In `server/src/main/kotlin/br/com/investlog/server/auth/domain/services/AuthService.kt`, add this
import:

```kotlin
import br.com.investlog.server.auth.rest.payloads.AuthConfigResponse
import org.springframework.beans.factory.annotation.Value
```

Change the class's constructor to add a fourth parameter, right after `totpService`:

```kotlin
@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val totpService: TotpService,
    @Value("\${investlog.google-auth-enabled:false}") private val googleAuthEnabled: Boolean,
) {
```

Add this method anywhere in the class, e.g. right after `currentSession`:

```kotlin

    fun authConfig(): AuthConfigResponse = AuthConfigResponse(googleAuthEnabled = googleAuthEnabled)
```

- [ ] **Step 9: Add the `GET /auth/config` endpoint**

In `server/src/main/kotlin/br/com/investlog/server/auth/rest/controllers/AuthController.kt`, add
this import:

```kotlin
import br.com.investlog.server.auth.rest.payloads.AuthConfigResponse
```

Add this method right after `logout`:

```kotlin

    @GetMapping("/config")
    fun config(): AuthConfigResponse = authService.authConfig()
```

- [ ] **Step 10: Permit `/auth/config` in `SecurityConfig`**

In `server/src/main/kotlin/br/com/investlog/server/config/SecurityConfig.kt`, inside the
`authorizeHttpRequests { }` block, add this line right after the existing
`authorize("/private/v1/auth/register", permitAll)` line:

```kotlin
                authorize("/private/v1/auth/config", permitAll)
```

- [ ] **Step 11: Run the tests to verify they pass**

Run: `cd server && ./gradlew test --tests "br.com.investlog.server.auth.rest.controllers.AuthConfigControllerTest"`
Expected: PASS (1 test).

- [ ] **Step 12: Run the full server suite**

Run: `cd server && ./gradlew test`
Expected: PASS — `GoogleOAuth2Config`'s bean stays absent in the test profile (no
`GOOGLE_AUTH_ENABLED` env var set), so the filter chain and every existing test are unaffected.
Confirm this in your report — if anything unexpected goes red, stop and report it rather than
guessing at a fix.

- [ ] **Step 13: Commit**

```bash
git add server/build.gradle.kts \
  server/src/main/resources/application.yaml \
  server/src/main/resources/application-prod.yaml \
  server/src/main/kotlin/br/com/investlog/server/shared/security/GoogleOAuth2Config.kt \
  server/src/main/kotlin/br/com/investlog/server/auth \
  server/src/main/kotlin/br/com/investlog/server/config/SecurityConfig.kt \
  server/src/test/kotlin/br/com/investlog/server/auth/rest/controllers/AuthConfigControllerTest.kt
git commit -m "feat(server): add the conditional Google OAuth2 client registration and GET /auth/config"
```

---

## Task 2: Google login flow — success/failure handlers and session establishment

**Files:**
- Create: `server/src/main/kotlin/br/com/investlog/server/shared/exceptions/GoogleAccountEmailInUseException.kt`
- Modify: `server/src/main/kotlin/br/com/investlog/server/shared/security/UserRepository.kt`
- Create: `server/src/main/kotlin/br/com/investlog/server/auth/security/GoogleLoginSuccessHandler.kt`
- Create: `server/src/main/kotlin/br/com/investlog/server/auth/security/GoogleLoginFailureHandler.kt`
- Modify: `server/src/main/kotlin/br/com/investlog/server/auth/domain/services/AuthService.kt`
- Modify: `server/src/main/kotlin/br/com/investlog/server/config/SecurityConfig.kt`
- Test: `server/src/test/kotlin/br/com/investlog/server/auth/domain/services/AuthServiceGoogleLoginTest.kt`

**Interfaces:**
- Consumes: `GoogleOAuth2Config`'s `ClientRegistrationRepository?` bean (Task 1).
- Produces: `AuthService.handleGoogleLogin(googleSub, email, name, avatarUrl, servletRequest,
  servletResponse): SessionResponse` — consumed only by `GoogleLoginSuccessHandler` in this same
  task; no later task calls it directly.

- [ ] **Step 1: Write the failing `handleGoogleLogin` tests**

Create `server/src/test/kotlin/br/com/investlog/server/auth/domain/services/AuthServiceGoogleLoginTest.kt`:

```kotlin
package br.com.investlog.server.auth.domain.services

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.shared.exceptions.GoogleAccountEmailInUseException
import br.com.investlog.server.shared.security.UserRepository
import br.com.investlog.server.usersadmin.domain.services.UsersAdminService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AuthServiceGoogleLoginTest : BaseIntegrationTest() {

    @Autowired
    lateinit var authService: AuthService

    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var usersAdminService: UsersAdminService

    @Test
    fun `first Google login creates a pending user and swaps in a CurrentUser-authorized session`() {
        val servletRequest = MockHttpServletRequest()
        val servletResponse = MockHttpServletResponse()

        val session = authService.handleGoogleLogin(
            googleSub = "google-sub-first-login",
            email = "primeira@example.com",
            name = "Primeira Vez",
            avatarUrl = "https://example.com/avatar.png",
            servletRequest = servletRequest,
            servletResponse = servletResponse,
        )

        assertEquals("PENDING", session.status.name)
        assertEquals("USER", session.role.name)

        val securityContext = HttpSessionSecurityContextRepository()
            .loadDeferredContext(servletRequest)
            .get()
        val authorities = securityContext.authentication.authorities.map { it.authority }

        assertTrue(authorities.contains("ROLE_USER"))
        assertTrue(authorities.contains("STATUS_PENDING"))
    }

    @Test
    fun `logging in again with the same google subject reuses the same user`() {
        val firstRequest = MockHttpServletRequest()
        val firstResponse = MockHttpServletResponse()
        authService.handleGoogleLogin(
            googleSub = "google-sub-repeat-login",
            email = "repetida@example.com",
            name = "Usuária Repetida",
            avatarUrl = null,
            servletRequest = firstRequest,
            servletResponse = firstResponse,
        )

        val secondRequest = MockHttpServletRequest()
        val secondResponse = MockHttpServletResponse()
        val secondSession = authService.handleGoogleLogin(
            googleSub = "google-sub-repeat-login",
            email = "repetida@example.com",
            name = "Usuária Repetida",
            avatarUrl = null,
            servletRequest = secondRequest,
            servletResponse = secondResponse,
        )

        assertEquals("repetida@example.com", secondSession.email)
    }

    @Test
    fun `a google login with an email already used by another account is rejected`() {
        val servletRequest = MockHttpServletRequest()
        val servletResponse = MockHttpServletResponse()

        assertFailsWith<GoogleAccountEmailInUseException> {
            authService.handleGoogleLogin(
                googleSub = "google-sub-duplicate-email",
                email = "admin@admin.com",
                name = "Impostor",
                avatarUrl = null,
                servletRequest = servletRequest,
                servletResponse = servletResponse,
            )
        }
    }

    @Test
    fun `an already-approved Google user's next login carries STATUS_APPROVED`() {
        val firstRequest = MockHttpServletRequest()
        val firstResponse = MockHttpServletResponse()
        authService.handleGoogleLogin(
            googleSub = "google-sub-approved-login",
            email = "aprovada@example.com",
            name = "Usuária Aprovada",
            avatarUrl = null,
            servletRequest = firstRequest,
            servletResponse = firstResponse,
        )

        val user = userRepository.findByEmail("aprovada@example.com")
            ?: error("User was not created by the first Google login")
        usersAdminService.approve(user.externalId)

        val secondRequest = MockHttpServletRequest()
        val secondResponse = MockHttpServletResponse()
        val secondSession = authService.handleGoogleLogin(
            googleSub = "google-sub-approved-login",
            email = "aprovada@example.com",
            name = "Usuária Aprovada",
            avatarUrl = null,
            servletRequest = secondRequest,
            servletResponse = secondResponse,
        )

        assertEquals("APPROVED", secondSession.status.name)

        val securityContext = HttpSessionSecurityContextRepository()
            .loadDeferredContext(secondRequest)
            .get()
        val authorities = securityContext.authentication.authorities.map { it.authority }

        assertTrue(authorities.contains("STATUS_APPROVED"))
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd server && ./gradlew test --tests "br.com.investlog.server.auth.domain.services.AuthServiceGoogleLoginTest"`
Expected: FAIL — compilation error (`handleGoogleLogin`, `GoogleAccountEmailInUseException` don't
exist yet).

- [ ] **Step 3: Create `GoogleAccountEmailInUseException`**

Create `server/src/main/kotlin/br/com/investlog/server/shared/exceptions/GoogleAccountEmailInUseException.kt`:

```kotlin
package br.com.investlog.server.shared.exceptions

/**
 * Deliberately not mapped in GlobalExceptionHandler — this is thrown and caught entirely inside
 * GoogleLoginSuccessHandler, an AuthenticationSuccessHandler that runs outside the normal
 * @RestControllerAdvice pipeline, never as a REST controller's response.
 */
class GoogleAccountEmailInUseException(message: String) : RuntimeException(message)
```

- [ ] **Step 4: Add `createGoogleUser` to `UserRepository`**

In `server/src/main/kotlin/br/com/investlog/server/shared/security/UserRepository.kt`, add this
method to the `UserRepository` class, right after `createLocalUser`:

```kotlin

    fun createGoogleUser(googleSub: String, email: String, name: String, avatarUrl: String?): CurrentUser =
        dsl.insertInto(USERS)
            .set(USERS.NAME, name)
            .set(USERS.EMAIL, email)
            .set(USERS.GOOGLE_SUB, googleSub)
            .set(USERS.AVATAR_URL, avatarUrl)
            .set(USERS.AUTH_PROVIDER, AuthProvider.GOOGLE.name)
            .set(USERS.ROLE, UserRole.USER.name)
            .set(USERS.STATUS, CurrentUser.Status.PENDING.name)
            .returning()
            .fetchSingle()
            .toCurrentUser()
```

- [ ] **Step 5: Add `handleGoogleLogin` to `AuthService`**

In `server/src/main/kotlin/br/com/investlog/server/auth/domain/services/AuthService.kt`, add this
import:

```kotlin
import br.com.investlog.server.shared.exceptions.GoogleAccountEmailInUseException
```

Add this method right after `register`:

```kotlin

    fun handleGoogleLogin(
        googleSub: String,
        email: String,
        name: String,
        avatarUrl: String?,
        servletRequest: HttpServletRequest,
        servletResponse: HttpServletResponse,
    ): SessionResponse {

        val user = userRepository.findByGoogleSub(googleSub) ?: run {
            if (userRepository.findByEmail(email) != null) {
                throw GoogleAccountEmailInUseException("An account with email $email already exists")
            }
            userRepository.createGoogleUser(googleSub, email, name, avatarUrl)
        }

        return establishSession(user, servletRequest, servletResponse)
    }
```

- [ ] **Step 6: Create `GoogleLoginSuccessHandler`**

Create `server/src/main/kotlin/br/com/investlog/server/auth/security/GoogleLoginSuccessHandler.kt`:

```kotlin
package br.com.investlog.server.auth.security

import br.com.investlog.server.auth.domain.services.AuthService
import br.com.investlog.server.shared.exceptions.GoogleAccountEmailInUseException
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

@Component
class GoogleLoginSuccessHandler(private val authService: AuthService) : AuthenticationSuccessHandler {

    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication,
    ) {
        val oauth2Token = authentication as OAuth2AuthenticationToken
        val attributes = oauth2Token.principal.attributes

        try {
            authService.handleGoogleLogin(
                googleSub = attributes["sub"] as String,
                email = attributes["email"] as String,
                name = attributes["name"] as String,
                avatarUrl = attributes["picture"] as String?,
                servletRequest = request,
                servletResponse = response,
            )
            response.sendRedirect("/")
        } catch (exception: GoogleAccountEmailInUseException) {
            log.warn(exception) { "Google login rejected: email already in use by another account" }
            response.sendRedirect("/login?error=email_in_use")
        }
    }
}
```

- [ ] **Step 7: Create `GoogleLoginFailureHandler`**

Create `server/src/main/kotlin/br/com/investlog/server/auth/security/GoogleLoginFailureHandler.kt`:

```kotlin
package br.com.investlog.server.auth.security

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.authentication.AuthenticationFailureHandler
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

@Component
class GoogleLoginFailureHandler : AuthenticationFailureHandler {

    override fun onAuthenticationFailure(
        request: HttpServletRequest,
        response: HttpServletResponse,
        exception: AuthenticationException,
    ) {
        log.warn(exception) { "Google login failed" }
        response.sendRedirect("/login?error=oauth_failed")
    }
}
```

- [ ] **Step 8: Wire `oauth2Login` into `SecurityConfig`**

Replace the full contents of `server/src/main/kotlin/br/com/investlog/server/config/SecurityConfig.kt`:

```kotlin
package br.com.investlog.server.config

import br.com.investlog.server.auth.security.GoogleLoginFailureHandler
import br.com.investlog.server.auth.security.GoogleLoginSuccessHandler
import br.com.investlog.server.shared.rest.payloads.AccessDeniedResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import tools.jackson.databind.json.JsonMapper

@Configuration
class SecurityConfig {

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun accessDeniedHandler(jsonMapper: JsonMapper): AccessDeniedHandler = AccessDeniedHandler { _, response, _ ->
        val authentication = SecurityContextHolder.getContext().authentication
        val isPendingApproval = authentication?.authorities.orEmpty().none { it.authority == "STATUS_APPROVED" }

        val body = if (isPendingApproval) {
            AccessDeniedResponse("pending_approval", "Your account is pending administrator approval")
        } else {
            AccessDeniedResponse("forbidden", "You do not have permission to perform this action")
        }

        response.status = HttpStatus.FORBIDDEN.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.writer.write(jsonMapper.writeValueAsString(body))
    }

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        jsonMapper: JsonMapper,
        clientRegistrationRepository: ClientRegistrationRepository?,
        googleLoginSuccessHandler: GoogleLoginSuccessHandler,
        googleLoginFailureHandler: GoogleLoginFailureHandler,
    ): SecurityFilterChain {
        val unauthorizedEntryPoint: AuthenticationEntryPoint = HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)
        http {
            csrf { disable() }
            anonymous { disable() }
            authorizeHttpRequests {
                authorize("/private/v1/auth/login", permitAll)
                authorize("/private/v1/auth/register", permitAll)
                authorize("/private/v1/auth/config", permitAll)
                authorize("/private/v1/auth/totp/enroll", permitAll)
                authorize("/private/v1/auth/totp/verify", permitAll)
                authorize("/private/v1/auth/session", authenticated)
                authorize("/private/v1/auth/logout", authenticated)
                authorize("/private/oauth2/**", permitAll)
                authorize("/private/login/oauth2/**", permitAll)
                authorize("/private/v1/users/**", hasAuthority("ROLE_ADMIN"))
                authorize(anyRequest, hasAuthority("STATUS_APPROVED"))
            }
            exceptionHandling {
                authenticationEntryPoint = unauthorizedEntryPoint
                accessDeniedHandler = accessDeniedHandler(jsonMapper)
            }
            if (clientRegistrationRepository != null) {
                oauth2Login {
                    authorizationEndpoint {
                        baseUri = "/private/oauth2/authorization"
                    }
                    redirectionEndpoint {
                        baseUri = "/private/login/oauth2/code/*"
                    }
                    this.clientRegistrationRepository = clientRegistrationRepository
                    authenticationSuccessHandler = googleLoginSuccessHandler
                    authenticationFailureHandler = googleLoginFailureHandler
                }
            }
        }
        return http.build()
    }
}
```

Note: `/private/oauth2/**` and `/private/login/oauth2/**` are `permitAll` unconditionally, even
though the `oauth2Login` block that actually serves them is conditional. When Google auth is
disabled, these routes simply don't exist in the filter's OAuth2 machinery and 404 like any other
unmapped path — `permitAll` here only means "don't demand `STATUS_APPROVED` for a path that isn't
a real business endpoint," it doesn't conjure the feature into existence.

- [ ] **Step 9: Run the tests to verify they pass**

Run: `cd server && ./gradlew test --tests "br.com.investlog.server.auth.domain.services.AuthServiceGoogleLoginTest"`
Expected: PASS (4 tests).

- [ ] **Step 10: Run the full server suite**

Run: `cd server && ./gradlew test`
Expected: PASS. Confirm in your report — the `anonymous { disable() }` line already present is
unrelated to `permitAll` routes (Spring Security's `permitAll` only requires the filter chain to
not demand an `Authentication`; it doesn't require anonymous-authentication support), but note
explicitly in your report whether any of the new `permitAll` OAuth2 paths behaved unexpectedly
under the existing `anonymous { disable() }` setting — if the full suite is green, they didn't.

- [ ] **Step 11: Commit**

```bash
git add server/src/main/kotlin/br/com/investlog/server/shared/exceptions/GoogleAccountEmailInUseException.kt \
  server/src/main/kotlin/br/com/investlog/server/shared/security/UserRepository.kt \
  server/src/main/kotlin/br/com/investlog/server/auth \
  server/src/main/kotlin/br/com/investlog/server/config/SecurityConfig.kt \
  server/src/test/kotlin/br/com/investlog/server/auth/domain/services/AuthServiceGoogleLoginTest.kt
git commit -m "feat(server): add the Google login flow — success/failure handlers and session establishment"
```

- [ ] **Step 12: Push the branch and open the server PR**

```bash
git push -u origin feature/auth-phase4-google-oauth-server
```

Open a PR from `feature/auth-phase4-google-oauth-server` into `feature/authentication`. Title:
`feat(auth): Phase 4 — Google OAuth2 login (server)`. Reference `Refs #4`, add the `feature`
label, assign to `arthurgregorio`. In the PR description, note the one manual verification this
plan can't automate: once real Google OAuth2 credentials are configured, log in with "Continuar
com Google" and confirm `GET /private/v1/auth/session` returns a normal session body (proves the
principal swap works against a real handshake, not just the mocked service-level test).

---

## Task 3: Client — Google button, config fetch, and OAuth error messaging

**Branch:** `feature/auth-phase4-google-oauth-client` (create from the current tip of
`feature/authentication`)

**Files:**
- Modify: `client/src/types.ts`
- Modify: `client/src/api/auth.ts`
- Modify: `client/src/views/LoginView.vue`
- Modify: `client/src/assets/styles.css`
- Test: `client/src/views/LoginView.test.ts`

**Interfaces:**
- Consumes (HTTP contract only, defined in Task 1, on the sibling server branch): `GET
  /auth/config` → 200 `{ googleAuthEnabled: boolean }`.
- Produces: nothing consumed by a later task in this plan.

- [ ] **Step 1: Add `AuthConfigResponse` to `types.ts`**

In `client/src/types.ts`, add this interface anywhere near the other auth-facing types (e.g.
right after `SessionResponse`):

```typescript
export interface AuthConfigResponse {
  googleAuthEnabled: boolean
}
```

- [ ] **Step 2: Add `fetchConfig` to `api/auth.ts`**

In `client/src/api/auth.ts`, add `AuthConfigResponse` to the existing type-only import from
`@/types`:

```typescript
import type { AuthConfigResponse, LoginOutcome, SessionResponse, TotpEnrollResponse } from '@/types'
```

Add this method to the `authApi` object, right after `register`:

```typescript
  fetchConfig(): Promise<AuthConfigResponse> {
    return apiClient.get<AuthConfigResponse>('/auth/config').then((response) => response.data)
  },
```

- [ ] **Step 3: Write the failing tests**

`client/src/views/LoginView.test.ts` currently mocks `@/api/auth` with a fixed object of `vi.fn()`
stubs and no default resolved values (existing tests bypass it entirely by spying on
`useAuthStore()`'s actions instead). Your new `onMounted` code calls `authApi.fetchConfig()`
directly — every existing test in this file will now also trigger that call on mount, so
`fetchConfig` needs a baked-in default resolved value or all four existing tests break with an
unhandled rejection inside `onMounted`.

Change the `vi.mock('@/api/auth', ...)` call at the top of the file to:

```typescript
vi.mock('@/api/auth', () => ({
  authApi: {
    login: vi.fn(),
    logout: vi.fn(),
    fetchSession: vi.fn(),
    enroll: vi.fn(),
    verify: vi.fn(),
    register: vi.fn(),
    fetchConfig: vi.fn().mockResolvedValue({ googleAuthEnabled: false }),
  },
}))
```

Add this import right after the existing `import { useAuthStore } from '@/stores/auth'` line:

```typescript
import { authApi } from '@/api/auth'
```

Add these three test cases inside the `describe('LoginView', ...)` block, right after the
existing `` `registers a new account and navigates to the pending-approval screen` `` test:

```typescript
  it('shows the Google button when the server reports googleAuthEnabled: true', async () => {
    vi.mocked(authApi.fetchConfig).mockResolvedValueOnce({ googleAuthEnabled: true })
    router.push('/login')
    await router.isReady()

    const wrapper = mount(LoginView, { global: { plugins: [router, Buefy] } })
    await flushPromises()

    expect(wrapper.find('.auth-google-button').exists()).toBe(true)
  })

  it('hides the Google button when the server reports googleAuthEnabled: false', async () => {
    router.push('/login')
    await router.isReady()

    const wrapper = mount(LoginView, { global: { plugins: [router, Buefy] } })
    await flushPromises()

    expect(wrapper.find('.auth-google-button').exists()).toBe(false)
  })

  it('shows a friendly message when redirected back with ?error=email_in_use', async () => {
    router.push('/login?error=email_in_use')
    await router.isReady()

    const wrapper = mount(LoginView, { global: { plugins: [router, Buefy] } })
    await flushPromises()

    expect(wrapper.text()).toContain('Já existe uma conta com este e-mail')
  })
```

- [ ] **Step 4: Run the tests to verify they fail**

Run: `cd client && npm run test -- src/views/LoginView.test.ts`
Expected: FAIL — `authApi.fetchConfig` doesn't exist yet, `.auth-google-button` never renders.

- [ ] **Step 5: Add the config fetch, Google button, and error-message handling to `LoginView.vue`**

In `client/src/views/LoginView.vue`, change the `<script setup>` imports:

```typescript
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import LogoMark from '@/components/icons/LogoMark.vue'
import { useAuthStore } from '@/stores/auth'
import { authApi } from '@/api/auth'
```

Add these two lines right after `const auth = useAuthStore()`:

```typescript
const route = useRoute()
const googleAuthEnabled = ref(false)
```

Add this `onMounted` block right after the existing `ref`/`computed` declarations, before the
function definitions:

```typescript
onMounted(async () => {
  if (route.query.error === 'email_in_use') {
    error.value = 'Já existe uma conta com este e-mail. Entre com e-mail e senha.'
  } else if (route.query.error === 'oauth_failed') {
    error.value = 'Não foi possível entrar com o Google. Tente novamente.'
  }
  const config = await authApi.fetchConfig()
  googleAuthEnabled.value = config.googleAuthEnabled
})
```

In the template, inside the `v-if="step === 'credentials'"` form, add this right after the
existing `toggle-register` button and before the closing `</form>` tag:

```html
          <div v-if="googleAuthEnabled" class="auth-divider">ou</div>
          <a v-if="googleAuthEnabled" href="/private/oauth2/authorization/google" class="auth-google-button">
            Continuar com Google
          </a>
```

- [ ] **Step 6: Add the Google button/divider styles**

In `client/src/assets/styles.css`, add this right after the existing `.auth-toggle` rule:

```css
.auth-divider {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 4px 0;
  color: var(--text-2);
  font-size: 12px;
  text-align: center;
}

.auth-divider::before,
.auth-divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: var(--border);
}

.auth-google-button {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 44px;
  border-radius: 10px;
  border: 1px solid var(--border);
  background: var(--surface);
  color: inherit;
  font-weight: 600;
  font-size: 14px;
  text-decoration: none;
}
```

- [ ] **Step 7: Run the tests to verify they pass**

Run: `cd client && npm run test -- src/views/LoginView.test.ts`
Expected: PASS (all cases, including the 3 new ones).

- [ ] **Step 8: Run the full client suite and build**

Run: `cd client && npm run test`
Expected: PASS — no regressions to any other test file.

Run: `cd client && npm run build`
Expected: PASS — no type errors.

- [ ] **Step 9: Commit**

```bash
git add client/src/types.ts client/src/api/auth.ts client/src/views/LoginView.vue \
  client/src/assets/styles.css client/src/views/LoginView.test.ts
git commit -m "feat(client): add the Google sign-in button and OAuth error messaging to LoginView"
```

- [ ] **Step 10: Push the branch and open the client PR**

```bash
git push -u origin feature/auth-phase4-google-oauth-client
```

Open a PR from `feature/auth-phase4-google-oauth-client` into `feature/authentication`. Title:
`feat(auth): Phase 4 — Google OAuth2 login (client)`. Reference `Refs #4`, add the `feature`
label, assign to `arthurgregorio`.

---

## Task 4: Docs — README Google Cloud Console setup + `.env.example`

**Branch:** `feature/auth-phase4-google-oauth-docs` (create from the current tip of
`feature/authentication`)

**Files:**
- Modify: `README.md`
- Modify: `.env.example`

**Interfaces:** none — pure documentation, no code.

- [ ] **Step 1: Add the three new variables to `.env.example`**

In `.env.example`, add these three lines right after the existing `ADMIN_DEFAULT_PASSWORD=admin`
line (under the `# --- Auth ---` header):

```
GOOGLE_AUTH_ENABLED=false
GOOGLE_CLIENT_ID=
GOOGLE_CLIENT_SECRET=
```

- [ ] **Step 2: Add the three new rows to the README's Configuration table**

In `README.md`, add these three rows to the Configuration table right after the
`ADMIN_DEFAULT_PASSWORD` row:

```markdown
| `GOOGLE_AUTH_ENABLED` | `false` | Enables Google OAuth2 login and shows the button client-side |
| `GOOGLE_CLIENT_ID` | _(empty)_ | From Google Cloud Console OAuth 2.0 Client ID |
| `GOOGLE_CLIENT_SECRET` | _(empty)_ | From Google Cloud Console |
```

- [ ] **Step 3: Add the Google Cloud Console setup section**

In `README.md`, add this new section right after the Configuration section (after its table,
before `## Stop / reset`):

```markdown
### Google OAuth2 login (optional)

Local email/password login always works. To also enable "Continuar com Google":

1. In [Google Cloud Console](https://console.cloud.google.com/), create (or select) a project,
   then go to **APIs & Services → OAuth consent screen** and configure it (External user type is
   fine for a personal deployment; add your own Google account as a test user if the app stays in
   "Testing" publishing status).
2. Go to **APIs & Services → Credentials → Create Credentials → OAuth client ID**, application
   type **Web application**.
3. Under **Authorized redirect URIs**, add `http(s)://<your-host>/private/login/oauth2/code/google`
   (for a local run against the defaults in this README, that's
   `http://localhost:8081/private/login/oauth2/code/google`).
4. Copy the generated **Client ID** and **Client secret** into your `.env`:
   ```
   GOOGLE_AUTH_ENABLED=true
   GOOGLE_CLIENT_ID=<your client id>
   GOOGLE_CLIENT_SECRET=<your client secret>
   ```
5. Rebuild/restart the server so it picks up the new environment variables.

A user's first Google login creates a `PENDING` account, exactly like self-registration — an
admin still has to approve it in "Usuários locais" before that person can use anything else.
```

- [ ] **Step 4: Commit**

```bash
git add README.md .env.example
git commit -m "docs: add the Google OAuth2 login setup section and .env.example variables"
```

- [ ] **Step 5: Push the branch and open the docs PR**

```bash
git push -u origin feature/auth-phase4-google-oauth-docs
```

Open a PR from `feature/auth-phase4-google-oauth-docs` into `feature/authentication`. Title:
`docs: Phase 4 — Google OAuth2 login setup`. Reference `Refs #4`, add the `feature` label, assign
to `arthurgregorio`.
