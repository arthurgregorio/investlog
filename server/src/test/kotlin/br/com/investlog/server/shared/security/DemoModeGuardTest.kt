package br.com.investlog.server.shared.security

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.auth.rest.payloads.SessionResponse
import br.com.investlog.server.auth.rest.payloads.TotpEnrollResponse
import dev.samstevens.totp.code.DefaultCodeGenerator
import org.junit.jupiter.api.Order
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.returnResult
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.Test

@TestPropertySource(properties = ["investlog.demo-mode.enabled=true"])
class DemoModeGuardTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTestClient: RestTestClient

    @Test
    @Order(1)
    fun `registers and promotes a second admin used to target the protected account`() {
        val email = "segundo-admin@example.com"
        val password = "senha123"

        restTestClient.post()
            .uri("/private/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Segundo Admin","email":"$email","password":"$password"}""")
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
            .single { it["email"] == email }["id"] as String

        restTestClient.patch()
            .uri("/private/v1/users/$targetId/approve")
            .exchange()
            .expectStatus().isOk()

        restTestClient.patch()
            .uri("/private/v1/users/$targetId/role")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"role":"ADMIN"}""")
            .exchange()
            .expectStatus().isOk()

        val secret = restTestClient.post()
            .uri("/private/v1/auth/totp/enroll")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"email":"$email","password":"$password"}""")
            .exchange()
            .expectStatus().isOk()
            .returnResult<TotpEnrollResponse>()
            .responseBody
            ?.secretKey
            ?: error("Enroll did not return a secret")

        val code = DefaultCodeGenerator().generate(secret, System.currentTimeMillis() / 1000L / 30L)

        secondAdminCookie = restTestClient.post()
            .uri("/private/v1/auth/totp/verify")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"email":"$email","password":"$password","code":"$code"}""")
            .exchange()
            .expectStatus().isOk()
            .returnResult<SessionResponse>()
            .responseHeaders
            .getFirst("Set-Cookie")
            ?.substringBefore(";")
            ?: error("Verify did not set a session cookie")

        protectedAdminId = (
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
    }

    @Test
    @Order(2)
    fun `role change on the protected admin account is forbidden in demo mode`() {
        restTestClient.patch()
            .uri("/private/v1/users/$protectedAdminId/role")
            .header("Cookie", secondAdminCookie)
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"role":"USER"}""")
            .exchange()
            .expectStatus().isEqualTo(403)
    }

    @Test
    @Order(3)
    fun `password reset on the protected admin account is forbidden in demo mode`() {
        restTestClient.patch()
            .uri("/private/v1/users/$protectedAdminId/password")
            .header("Cookie", secondAdminCookie)
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"newPassword":"novaSenha456"}""")
            .exchange()
            .expectStatus().isEqualTo(403)
    }

    @Test
    @Order(4)
    fun `deleting the protected admin account is forbidden in demo mode`() {
        restTestClient.delete()
            .uri("/private/v1/users/$protectedAdminId")
            .header("Cookie", secondAdminCookie)
            .exchange()
            .expectStatus().isEqualTo(403)
    }

    @Test
    @Order(5)
    fun `self-service password change on the protected admin account is forbidden in demo mode`() {
        restTestClient.patch()
            .uri("/private/v1/profile/password")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"currentPassword":"admin","newPassword":"novaSenha456"}""")
            .exchange()
            .expectStatus().isEqualTo(403)
    }

    companion object {
        private lateinit var secondAdminCookie: String
        private lateinit var protectedAdminId: String
    }
}
