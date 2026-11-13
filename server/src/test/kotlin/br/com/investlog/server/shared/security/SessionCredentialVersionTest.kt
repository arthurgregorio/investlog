package br.com.investlog.server.shared.security

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.auth.rest.payloads.TotpEnrollResponse
import dev.samstevens.totp.code.DefaultCodeGenerator
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.returnResult
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class SessionCredentialVersionTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTestClient: RestTestClient

    @Autowired
    lateinit var userRepository: UserRepository

    @LocalServerPort
    var port: Int = 0

    @Test
    fun `a password change ends the other open sessions of the account`() {
        val account = registerEnrollAndApprove("troca-outra-sessao@example.com")
        val changingSession = signIn(account, "Senha123")
        val otherSession = signIn(account, "Senha123")

        changePassword(changingSession, "Senha123", "NovaSenha456").expectStatus().isNoContent()

        assertEquals(401, profileStatus(otherSession))
    }

    @Test
    fun `the session that changed the password stays valid`() {
        val account = registerEnrollAndApprove("troca-mesma-sessao@example.com")
        val changingSession = signIn(account, "Senha123")

        changePassword(changingSession, "Senha123", "NovaSenha456").expectStatus().isNoContent()

        assertEquals(200, profileStatus(changingSession))
    }

    @Test
    fun `the session that changed the password can change it again`() {
        val account = registerEnrollAndApprove("troca-duas-vezes@example.com")
        val changingSession = signIn(account, "Senha123")

        changePassword(changingSession, "Senha123", "NovaSenha456").expectStatus().isNoContent()
        changePassword(changingSession, "NovaSenha456", "OutraSenha789").expectStatus().isNoContent()

        assertEquals(200, profileStatus(changingSession))
    }

    @Test
    fun `a session opened with the new password after a change works`() {
        val account = registerEnrollAndApprove("troca-nova-sessao@example.com")
        val changingSession = signIn(account, "Senha123")

        changePassword(changingSession, "Senha123", "NovaSenha456").expectStatus().isNoContent()
        val newSession = signIn(account, "NovaSenha456")

        assertEquals(200, profileStatus(newSession))
    }

    @Test
    fun `a rejected password change leaves the other open sessions alone`() {
        val account = registerEnrollAndApprove("troca-recusada@example.com")
        val changingSession = signIn(account, "Senha123")
        val otherSession = signIn(account, "Senha123")

        changePassword(changingSession, "SenhaErrada1", "NovaSenha456").expectStatus().isUnauthorized()

        assertEquals(200, profileStatus(otherSession))
        assertEquals(200, profileStatus(changingSession))
    }

    @Test
    fun `an administrator password reset ends every open session of the account`() {
        val account = registerEnrollAndApprove("reset-encerra-sessoes@example.com")
        val firstSession = signIn(account, "Senha123")
        val secondSession = signIn(account, "Senha123")

        resetPassword(account.externalId, "SenhaResetada1")

        assertEquals(401, profileStatus(firstSession))
        assertEquals(401, profileStatus(secondSession))
    }

    @Test
    fun `a session opened after an administrator password reset works`() {
        val account = registerEnrollAndApprove("reset-nova-sessao@example.com")
        signIn(account, "Senha123")

        resetPassword(account.externalId, "SenhaResetada1")
        val newSession = signIn(account, "SenhaResetada1")

        assertEquals(200, profileStatus(newSession))
    }

    @Test
    fun `an administrator password reset leaves the sessions of other accounts alone`() {
        val resetAccount = registerEnrollAndApprove("reset-alvo@example.com")
        val otherAccount = registerEnrollAndApprove("reset-outra-conta@example.com")
        signIn(resetAccount, "Senha123")
        val otherAccountSession = signIn(otherAccount, "Senha123")

        resetPassword(resetAccount.externalId, "SenhaResetada1")

        assertEquals(200, profileStatus(otherAccountSession))
    }

    private fun profileStatus(cookie: String): Int =
        restTestClient.get()
            .uri("/private/v1/profile")
            .header("Cookie", cookie)
            .exchange()
            .returnResult<String>()
            .status.value()

    private fun changePassword(cookie: String, currentPassword: String, newPassword: String): RestTestClient.ResponseSpec =
        restTestClient.patch()
            .uri("/private/v1/profile/password")
            .header("Cookie", cookie)
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"currentPassword":"$currentPassword","newPassword":"$newPassword"}""")
            .exchange()

    private fun resetPassword(externalId: UUID, newPassword: String) {
        restTestClient.patch()
            .uri("/private/v1/users/$externalId/password")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"newPassword":"$newPassword"}""")
            .exchange()
            .expectStatus().isOk()
    }

    private fun registerEnrollAndApprove(email: String): Account {
        restTestClient.post()
            .uri("/private/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Conta de Teste","email":"$email","password":"Senha123"}""")
            .exchange()
            .expectStatus().isCreated()

        val externalId = userRepository.findByEmail(email)?.externalId ?: error("User was not created by register")

        restTestClient.patch().uri("/private/v1/users/$externalId/approve").exchange().expectStatus().isOk()

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

        restTestClient.post()
            .uri("/private/v1/auth/totp/verify")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"email":"$email","password":"Senha123","code":"$code"}""")
            .exchange()
            .expectStatus().isOk()

        return Account(externalId, email, secret)
    }

    private fun signIn(account: Account, password: String): String {
        val code = DefaultCodeGenerator().generate(account.totpSecret, System.currentTimeMillis() / 1000L / 30L)

        val request = HttpRequest.newBuilder(URI("http://localhost:$port/private/v1/auth/login"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("""{"email":"${account.email}","password":"$password","totpCode":"$code"}"""))
            .build()

        val response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())

        assertEquals(200, response.statusCode())

        return response.headers().firstValue("Set-Cookie").orElseThrow().substringBefore(";")
    }

    private data class Account(val externalId: UUID, val email: String, val totpSecret: String)
}
