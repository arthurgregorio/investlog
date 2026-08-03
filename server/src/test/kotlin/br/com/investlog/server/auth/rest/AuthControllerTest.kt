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
import kotlin.collections.get
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
            .body("""{"name":"Vincular HTTP","email":"vincular-http@example.com","password":"senha123"}""")
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
            .body("""{"linkToken":"$token","password":"senha123"}""")
            .exchange()
            .expectStatus().isOk()
            .returnResult<SessionResponse>()
            .responseBody

        assertEquals("vincular-http@example.com", response?.email)
        assertEquals("GOOGLE", response?.authProvider?.name)
    }

    @Nested
    @NestedTestConfiguration(EnclosingConfiguration.OVERRIDE)
    @AutoConfigureRestTestClient
    @ActiveProfiles("test")
    @Import(value = [TestcontainersConfiguration::class, RestClientTestConfiguration::class])
    @SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = ["investlog.totp-required=false"],
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
        properties = ["investlog.totp-lockout.max-attempts=2", "investlog.totp-lockout.base-duration=3s"],
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
                .body("""{"name":"Lockout Test","email":"totp-lockout@example.com","password":"senha123"}""")
                .exchange()
                .expectStatus().isCreated()

            val secret = restTestClient.post()
                .uri("/private/v1/auth/totp/enroll")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""{"email":"totp-lockout@example.com","password":"senha123"}""")
                .exchange()
                .expectStatus().isOk()
                .returnResult<TotpEnrollResponse>()
                .responseBody
                ?.secretKey
                ?: error("Enroll did not return a secret")

            restTestClient.post()
                .uri("/private/v1/auth/totp/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""{"email":"totp-lockout@example.com","password":"senha123","code":"${currentTotpCode(secret)}"}""")
                .exchange()
                .expectStatus().isOk()

            // Two invalid codes trip the configured max-attempts=2 lockout
            repeat(2) {
                restTestClient.post()
                    .uri("/private/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""{"email":"totp-lockout@example.com","password":"senha123","totpCode":"000000"}""")
                    .exchange()
                    .expectStatus().isUnauthorized()
            }

            // The account is now locked out, even with a correct code
            restTestClient.post()
                .uri("/private/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""{"email":"totp-lockout@example.com","password":"senha123","totpCode":"${currentTotpCode(secret)}"}""")
                .exchange()
                .expectStatus().isEqualTo(429)
                .returnResult<Map<String, Any?>>()
                .responseBody
                .let { assertEquals("too_many_totp_attempts", it?.get("error")) }

            // The limiter is shared: /auth/totp/verify is blocked too, for the same account
            restTestClient.post()
                .uri("/private/v1/auth/totp/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""{"email":"totp-lockout@example.com","password":"senha123","code":"${currentTotpCode(secret)}"}""")
                .exchange()
                .expectStatus().isEqualTo(429)

            Thread.sleep(3100)

            restTestClient.post()
                .uri("/private/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""{"email":"totp-lockout@example.com","password":"senha123","totpCode":"${currentTotpCode(secret)}"}""")
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
                    .body("""{"email":"totp-lockout@example.com","password":"senha123","code":"000000"}""")
                    .exchange()
                    .expectStatus().isUnauthorized()
            }

            restTestClient.post()
                .uri("/private/v1/auth/totp/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""{"email":"totp-lockout@example.com","password":"senha123","code":"000000"}""")
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
                .body("""{"email":"totp-lockout@example.com","password":"senha123","code":"000000"}""")
                .exchange()
                .expectStatus().isUnauthorized()
        }
    }

    companion object {
        private lateinit var adminTotpSecret: String

        private fun currentTotpCode(secret: String): String =
            DefaultCodeGenerator().generate(secret, System.currentTimeMillis() / 1000L / 30L)
    }
}
