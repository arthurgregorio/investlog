package br.com.investlog.server.config

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.auth.rest.payloads.SessionResponse
import br.com.investlog.server.auth.rest.payloads.TotpEnrollResponse
import br.com.investlog.server.shared.security.UserRepository
import br.com.investlog.server.usersadmin.services.UsersAdminService
import dev.samstevens.totp.code.DefaultCodeGenerator
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.returnResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SecurityConfigurationTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTestClient: RestTestClient

    @Autowired
    lateinit var passwordEncoder: PasswordEncoder

    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var usersAdminService: UsersAdminService

    @Test
    fun `authenticated requests reach private endpoints`() {
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

    @Test
    fun `an approved non-admin hitting an admin-only route gets the forbidden body`() {
        val cookie = approvedUserSessionCookie("nao-admin@example.com")

        val body = restTestClient.get()
            .uri("/private/v1/users")
            .header("Cookie", cookie)
            .exchange()
            .expectStatus().isForbidden()
            .returnResult<Map<String, Any?>>()
            .responseBody

        assertEquals("forbidden", body?.get("error"))
        assertEquals("Você não tem permissão para executar esta ação", body?.get("detail"))
    }

    @Test
    fun `an approved non-admin cannot toggle a runtime configuration`() {
        val cookie = approvedUserSessionCookie("nao-admin-config@example.com")

        restTestClient.patch()
            .uri("/private/v1/configurations/stock_price_sync_enabled")
            .header("Cookie", cookie)
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"value":"false"}""")
            .exchange()
            .expectStatus().isForbidden()
            .expectBody()
            .jsonPath("$.error").isEqualTo("forbidden")
    }

    private fun approvedUserSessionCookie(email: String): String {
        restTestClient.post()
            .uri("/private/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Usuário Comum","email":"$email","password":"Senha123"}""")
            .exchange()
            .expectStatus().isCreated()

        val user = userRepository.findByEmail(email) ?: error("User was not created by register")
        usersAdminService.approve(user.externalId)

        val secret = restTestClient.post()
            .uri("/private/v1/auth/totp/enroll")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"email":"$email","password":"Senha123"}""")
            .exchange()
            .expectStatus().isOk()
            .returnResult<TotpEnrollResponse>()
            .responseBody
            ?.secretKey
            ?: error("Enroll did not return a secret")

        val code = DefaultCodeGenerator().generate(secret, System.currentTimeMillis() / 1000L / 30L)

        return restTestClient.post()
            .uri("/private/v1/auth/totp/verify")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"email":"$email","password":"Senha123","code":"$code"}""")
            .exchange()
            .expectStatus().isOk()
            .returnResult<SessionResponse>()
            .responseHeaders
            .getFirst("Set-Cookie")
            ?.substringBefore(";")
            ?: error("Verify did not set a session cookie")
    }
}
