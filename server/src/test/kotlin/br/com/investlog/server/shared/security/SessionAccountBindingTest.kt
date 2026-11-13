package br.com.investlog.server.shared.security

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.auth.rest.payloads.SessionResponse
import br.com.investlog.server.auth.rest.payloads.TotpEnrollResponse
import dev.samstevens.totp.code.DefaultCodeGenerator
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.returnResult
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class SessionAccountBindingTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTestClient: RestTestClient

    @Autowired
    lateinit var userRepository: UserRepository

    @Test
    fun `an open session keeps working while its account still exists`() {
        val session = openSession("sessao-viva@example.com", "Conta Viva")

        assertEquals(200, profileStatus(session.cookie))
    }

    @Test
    fun `a session of a deleted account is rejected after the same email registers again and is approved`() {
        val email = "conta-recriada@example.com"
        val oldSession = openSession(email, "Conta Antiga")

        restTestClient.delete().uri("/private/v1/users/${oldSession.externalId}").exchange().expectStatus().isNoContent()

        val newExternalId = registerAndApprove(email, "Conta Nova")

        assertEquals(401, profileStatus(oldSession.cookie))
        assertEquals(newExternalId, userRepository.findByEmail(email)?.externalId)
    }

    @Test
    fun `a new session of the re-registered account reaches its own profile`() {
        val email = "conta-recriada-nova-sessao@example.com"
        val oldSession = openSession(email, "Conta Antiga")

        restTestClient.delete().uri("/private/v1/users/${oldSession.externalId}").exchange().expectStatus().isNoContent()
        registerAndApprove(email, "Conta Nova")
        val newSession = signIn(email)

        assertEquals(401, profileStatus(oldSession.cookie))
        assertEquals(200, profileStatus(newSession))
    }

    private fun profileStatus(cookie: String): Int =
        restTestClient.get()
            .uri("/private/v1/profile")
            .header("Cookie", cookie)
            .exchange()
            .returnResult<String>()
            .status.value()

    private fun registerAndApprove(email: String, name: String): UUID {
        restTestClient.post()
            .uri("/private/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"$name","email":"$email","password":"Senha123"}""")
            .exchange()
            .expectStatus().isCreated()

        val externalId = userRepository.findByEmail(email)?.externalId ?: error("User was not created by register")

        restTestClient.patch().uri("/private/v1/users/$externalId/approve").exchange().expectStatus().isOk()

        return externalId
    }

    private fun openSession(email: String, name: String): OpenSession {
        val externalId = registerAndApprove(email, name)
        return OpenSession(externalId, signIn(email))
    }

    private fun signIn(email: String): String {
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

    private data class OpenSession(val externalId: UUID, val cookie: String)
}
