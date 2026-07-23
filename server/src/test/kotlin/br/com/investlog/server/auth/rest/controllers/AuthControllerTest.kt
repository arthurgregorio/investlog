package br.com.investlog.server.auth.rest.controllers

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.RestClientTestConfiguration
import br.com.investlog.server.TestcontainersConfiguration
import br.com.investlog.server.auth.rest.payloads.SessionResponse
import br.com.investlog.server.auth.rest.payloads.TotpEnrollResponse
import dev.samstevens.totp.code.DefaultCodeGenerator
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Order
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

    companion object {
        private lateinit var adminTotpSecret: String

        private fun currentTotpCode(secret: String): String =
            DefaultCodeGenerator().generate(secret, System.currentTimeMillis() / 1000L / 30L)
    }
}
