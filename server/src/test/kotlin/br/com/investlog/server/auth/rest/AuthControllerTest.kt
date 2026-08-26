package br.com.investlog.server.auth.rest

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.RestClientTestConfiguration
import br.com.investlog.server.TestcontainersConfiguration
import br.com.investlog.server.auth.rest.payloads.SessionResponse
import br.com.investlog.server.auth.rest.payloads.TotpEnrollResponse
import br.com.investlog.server.auth.security.GoogleLinkTokenStore
import dev.samstevens.totp.code.DefaultCodeGenerator
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.NestedTestConfiguration
import org.springframework.test.context.NestedTestConfiguration.EnclosingConfiguration
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.returnResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuthControllerTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTestClient: RestTestClient

    @Autowired
    lateinit var googleLinkTokenStore: GoogleLinkTokenStore

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
        assertEquals(false, response?.demoModeEnabled)
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

    @Test
    @Order(13)
    fun `linking a Google account via the HTTP endpoint establishes a session`() {
        restTestClient.post()
            .uri("/private/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Vincular HTTP","email":"vincular-http@example.com","password":"Senha123"}""")
            .exchange()
            .expectStatus().isCreated()

        val token = googleLinkTokenStore.issue(
            googleSub = "google-sub-link-http",
            email = "vincular-http@example.com",
            name = "Vincular HTTP",
            avatarUrl = null,
        )

        val response = restTestClient.post()
            .uri("/private/v1/auth/google/link")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"linkToken":"$token","password":"Senha123"}""")
            .exchange()
            .expectStatus().isOk()
            .returnResult<SessionResponse>()
            .responseBody

        assertEquals("vincular-http@example.com", response?.email)
        assertEquals("GOOGLE", response?.authProvider?.name)
    }

    @Test
    @Order(14)
    fun `wrong password against an unknown, unapproved, or blocked account is indistinguishable`() {
        restTestClient.post()
            .uri("/private/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Enum Pending","email":"enum-pending@example.com","password":"Senha123"}""")
            .exchange()
            .expectStatus().isCreated()

        restTestClient.post()
            .uri("/private/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Enum Blocked","email":"enum-blocked@example.com","password":"Senha123"}""")
            .exchange()
            .expectStatus().isCreated()

        // By this point admin already has totp enabled (Order 7), so the shared RestTestClient's
        // lazy auto-login as admin (AdminSessionCookieInterceptor) would fail on its first-ever
        // attempt here, since it only knows how to handle a not-yet-enrolled admin. Authenticate
        // as admin ourselves and pass the cookie explicitly, which short-circuits auto-injection.
        val adminCookie = restTestClient.post()
            .uri("/private/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"email":"admin@admin.com","password":"admin","totpCode":"${currentTotpCode(adminTotpSecret)}"}""")
            .exchange()
            .expectStatus().isOk()
            .returnResult<SessionResponse>()
            .responseHeaders
            .getFirst("Set-Cookie")
            ?.substringBefore(";")
            ?: error("Admin login did not set a session cookie")

        val blockedTargetId = (
            restTestClient.get()
                .uri("/private/v1/users?size=200")
                .header("Cookie", adminCookie)
                .exchange()
                .expectStatus().isOk()
                .returnResult<Map<String, Any?>>()
                .responseBody
                ?.get("content") as List<*>
            )
            .map { it as Map<*, *> }
            .single { it["email"] == "enum-blocked@example.com" }["id"] as String

        restTestClient.patch()
            .uri("/private/v1/users/$blockedTargetId/approve")
            .header("Cookie", adminCookie)
            .exchange()
            .expectStatus().isOk()

        restTestClient.patch()
            .uri("/private/v1/users/$blockedTargetId/block")
            .header("Cookie", adminCookie)
            .exchange()
            .expectStatus().isOk()

        val nonexistentDetail = loginErrorDetail("enum-nobody@example.com", "whatever")
        val pendingWrongPasswordDetail = loginErrorDetail("enum-pending@example.com", "wrong-password")
        val blockedWrongPasswordDetail = loginErrorDetail("enum-blocked@example.com", "wrong-password")

        assertEquals("E-mail ou senha inválidos", nonexistentDetail)
        assertEquals(nonexistentDetail, pendingWrongPasswordDetail)
        assertEquals(nonexistentDetail, blockedWrongPasswordDetail)
    }

    @Test
    @Order(15)
    fun `a blocked account's correct password still reveals the account is blocked`() {
        val detail = loginErrorDetail("enum-blocked@example.com", "Senha123")

        assertEquals("Login falhou. Entre em contato com um administrador.", detail)
    }

    private fun loginErrorDetail(email: String, password: String): String? =
        restTestClient.post()
            .uri("/private/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"email":"$email","password":"$password"}""")
            .exchange()
            .expectStatus().isUnauthorized()
            .returnResult<Map<String, Any?>>()
            .responseBody
            ?.get("detail") as String?

    @Nested
    @NestedTestConfiguration(EnclosingConfiguration.OVERRIDE)
    @AutoConfigureRestTestClient
    @ActiveProfiles("test")
    @Import(value = [TestcontainersConfiguration::class, RestClientTestConfiguration::class])
    @SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = ["investlog.security.totp.enabled=false"],
    )
    @DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
    inner class WhenTotpIsNotRequired {

        @Autowired
        lateinit var restTestClient: RestTestClient

        @Test
        fun `login authenticates immediately without enrollment or a totp code`() {
            val response = restTestClient.post()
                .uri("/private/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""{"email":"admin@admin.com","password":"admin"}""")
                .exchange()
                .expectStatus().isOk()
                .returnResult<SessionResponse>()
                .responseBody

            assertEquals("admin@admin.com", response?.email)
        }
    }

    @Nested
    @NestedTestConfiguration(EnclosingConfiguration.OVERRIDE)
    @AutoConfigureRestTestClient
    @ActiveProfiles("test")
    @Import(value = [TestcontainersConfiguration::class, RestClientTestConfiguration::class])
    @SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = ["investlog.demo-mode.enabled=true"],
    )
    @DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
    inner class WhenDemoModeIsEnabled {

        @Autowired
        lateinit var restTestClient: RestTestClient

        @Test
        fun `session reflects demoModeEnabled from the configured property`() {
            val response = restTestClient.get()
                .uri("/private/v1/auth/session")
                .exchange()
                .expectStatus().isOk()
                .returnResult<SessionResponse>()
                .responseBody

            assertEquals(true, response?.demoModeEnabled)
        }
    }

    @Nested
    @NestedTestConfiguration(EnclosingConfiguration.OVERRIDE)
    @AutoConfigureRestTestClient
    @ActiveProfiles("test")
    @Import(value = [TestcontainersConfiguration::class, RestClientTestConfiguration::class])
    @SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = ["investlog.security.totp.lockout-max-attempts=2", "investlog.security.totp.lockout-base-duration=15s"],
    )
    @DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    inner class WhenTotpAttemptsAreLimited {

        @Autowired
        lateinit var restTestClient: RestTestClient

        @Test
        @Order(1)
        fun `locks the account after the configured number of invalid codes, then unlocks once the window elapses`() {
            restTestClient.post()
                .uri("/private/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""{"name":"Lockout Test","email":"totp-lockout@example.com","password":"Senha123"}""")
                .exchange()
                .expectStatus().isCreated()

            val secret = restTestClient.post()
                .uri("/private/v1/auth/totp/enroll")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""{"email":"totp-lockout@example.com","password":"Senha123"}""")
                .exchange()
                .expectStatus().isOk()
                .returnResult<TotpEnrollResponse>()
                .responseBody
                ?.secretKey
                ?: error("Enroll did not return a secret")

            restTestClient.post()
                .uri("/private/v1/auth/totp/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""{"email":"totp-lockout@example.com","password":"Senha123","code":"${currentTotpCode(secret)}"}""")
                .exchange()
                .expectStatus().isOk()

            // Two invalid codes trip the configured max-attempts=2 lockout
            repeat(2) {
                restTestClient.post()
                    .uri("/private/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""{"email":"totp-lockout@example.com","password":"Senha123","totpCode":"000000"}""")
                    .exchange()
                    .expectStatus().isUnauthorized()
            }

            // The account is now locked out, even with a correct code
            restTestClient.post()
                .uri("/private/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""{"email":"totp-lockout@example.com","password":"Senha123","totpCode":"${currentTotpCode(secret)}"}""")
                .exchange()
                .expectStatus().isEqualTo(429)
                .returnResult<Map<String, Any?>>()
                .responseBody
                .let { assertEquals("too_many_totp_attempts", it?.get("error")) }

            // The limiter is shared: /auth/totp/verify is blocked too, for the same account
            restTestClient.post()
                .uri("/private/v1/auth/totp/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""{"email":"totp-lockout@example.com","password":"Senha123","code":"${currentTotpCode(secret)}"}""")
                .exchange()
                .expectStatus().isEqualTo(429)

            Thread.sleep(15100)

            restTestClient.post()
                .uri("/private/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""{"email":"totp-lockout@example.com","password":"Senha123","totpCode":"${currentTotpCode(secret)}"}""")
                .exchange()
                .expectStatus().isOk()
        }

        @Test
        @Order(2)
        fun `admin totp-reset clears an active lockout immediately, without waiting for the window`() {
            repeat(2) {
                restTestClient.post()
                    .uri("/private/v1/auth/totp/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""{"email":"totp-lockout@example.com","password":"Senha123","code":"000000"}""")
                    .exchange()
                    .expectStatus().isUnauthorized()
            }

            restTestClient.post()
                .uri("/private/v1/auth/totp/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""{"email":"totp-lockout@example.com","password":"Senha123","code":"000000"}""")
                .exchange()
                .expectStatus().isEqualTo(429)

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
                .single { it["email"] == "totp-lockout@example.com" }["id"] as String

            restTestClient.patch()
                .uri("/private/v1/users/$targetId/totp-reset")
                .exchange()
                .expectStatus().isOk()

            // No sleep: a still-401 (not 429) here proves the admin reset cleared the lockout
            // immediately, rather than the assertion coincidentally landing after the window elapsed.
            restTestClient.post()
                .uri("/private/v1/auth/totp/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""{"email":"totp-lockout@example.com","password":"Senha123","code":"000000"}""")
                .exchange()
                .expectStatus().isUnauthorized()
        }
    }

    @Nested
    @NestedTestConfiguration(EnclosingConfiguration.OVERRIDE)
    @AutoConfigureRestTestClient
    @ActiveProfiles("test")
    @Import(value = [TestcontainersConfiguration::class, RestClientTestConfiguration::class])
    @SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = ["investlog.security.login.lockout-max-attempts=2", "investlog.security.login.lockout-base-duration=3s"],
    )
    @DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    inner class WhenLoginAttemptsAreLimited {

        @Autowired
        lateinit var restTestClient: RestTestClient

        @Test
        @Order(1)
        fun `locks the account after the configured number of invalid passwords, then unlocks once the window elapses`() {
            restTestClient.post()
                .uri("/private/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""{"name":"Login Lockout Test","email":"login-lockout@example.com","password":"Senha123"}""")
                .exchange()
                .expectStatus().isCreated()

            // Two invalid passwords trip the configured max-attempts=2 lockout
            repeat(2) {
                restTestClient.post()
                    .uri("/private/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""{"email":"login-lockout@example.com","password":"wrong"}""")
                    .exchange()
                    .expectStatus().isUnauthorized()
            }

            // The account is now locked out, even with the correct password
            restTestClient.post()
                .uri("/private/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""{"email":"login-lockout@example.com","password":"Senha123"}""")
                .exchange()
                .expectStatus().isEqualTo(429)
                .returnResult<Map<String, Any?>>()
                .responseBody
                .let { assertEquals("too_many_login_attempts", it?.get("error")) }

            // The limiter guards every call site through the shared verifyCredentials, not just
            // /auth/login: /auth/totp/enroll is blocked too, for the same account
            restTestClient.post()
                .uri("/private/v1/auth/totp/enroll")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""{"email":"login-lockout@example.com","password":"Senha123"}""")
                .exchange()
                .expectStatus().isEqualTo(429)

            Thread.sleep(3100)

            restTestClient.post()
                .uri("/private/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""{"email":"login-lockout@example.com","password":"Senha123"}""")
                .exchange()
                .expectStatus().isEqualTo(202)
        }
    }

    companion object {
        private lateinit var adminTotpSecret: String

        private fun currentTotpCode(secret: String): String =
            DefaultCodeGenerator().generate(secret, System.currentTimeMillis() / 1000L / 30L)
    }
}
