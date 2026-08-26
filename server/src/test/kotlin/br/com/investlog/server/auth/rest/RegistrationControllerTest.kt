package br.com.investlog.server.auth.rest

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

class RegistrationControllerTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTestClient: RestTestClient

    @Test
    @Order(1)
    fun `register creates a pending local user`() {
        restTestClient.post()
            .uri("/private/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Nova Usuária","email":"nova@example.com","password":"Senha123"}""")
            .exchange()
            .expectStatus().isCreated()
    }

    @Test
    @Order(2)
    fun `duplicate email registration responds identically to a new registration, without overwriting the existing account`() {
        restTestClient.post()
            .uri("/private/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Outra Pessoa","email":"nova@example.com","password":"OutraSenha1"}""")
            .exchange()
            .expectStatus().isCreated()

        restTestClient.post()
            .uri("/private/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"email":"nova@example.com","password":"outrasenha"}""")
            .exchange()
            .expectStatus().isUnauthorized()

        restTestClient.post()
            .uri("/private/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"email":"nova@example.com","password":"Senha123"}""")
            .exchange()
            .expectStatus().isEqualTo(202)
    }

    @Test
    @Order(3)
    fun `a pending user can log in after totp enrollment but is forbidden from private endpoints`() {
        restTestClient.post()
            .uri("/private/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"email":"nova@example.com","password":"Senha123"}""")
            .exchange()
            .expectStatus().isEqualTo(202)

        val secret = restTestClient.post()
            .uri("/private/v1/auth/totp/enroll")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"email":"nova@example.com","password":"Senha123"}""")
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
            .body("""{"email":"nova@example.com","password":"Senha123","code":"$code"}""")
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

    @Test
    @Order(5)
    fun `registration rejects a password shorter than 8 characters`() {
        restTestClient.post()
            .uri("/private/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Teste","email":"curta@example.com","password":"1234567"}""")
            .exchange()
            .expectStatus().isBadRequest()
    }

    @Test
    @Order(6)
    fun `registration rejects a password without an uppercase letter`() {
        restTestClient.post()
            .uri("/private/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Teste","email":"semmaiuscula@example.com","password":"semmaiuscula123"}""")
            .exchange()
            .expectStatus().isBadRequest()
            .returnResult<Map<String, Any?>>()
            .responseBody
            .let {
                @Suppress("UNCHECKED_CAST")
                val errors = it?.get("errors") as List<String>
                assertTrue(errors.any { message -> message.contains("letra maiúscula") })
            }
    }

    @Test
    @Order(7)
    fun `registration rejects a password without a number`() {
        restTestClient.post()
            .uri("/private/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Teste","email":"semnumero@example.com","password":"SemNumeroAqui"}""")
            .exchange()
            .expectStatus().isBadRequest()
            .returnResult<Map<String, Any?>>()
            .responseBody
            .let {
                @Suppress("UNCHECKED_CAST")
                val errors = it?.get("errors") as List<String>
                assertTrue(errors.any { message -> message.contains("número") })
            }
    }

    companion object {
        private lateinit var pendingUserCookie: String
    }
}
