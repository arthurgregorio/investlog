package br.com.investlog.server.configurations

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

class ConfigurationControllerTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTestClient: RestTestClient

    @Test
    @Order(1)
    fun `returns the seeded stock_price_sync_enabled configuration initially`() {
        restTestClient.get()
            .uri("/private/v1/configurations")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content[?(@.key=='stock_price_sync_enabled')].value").isEqualTo("false")
    }

    @Test
    @Order(2)
    fun `admin updates a configuration value`() {
        restTestClient.patch()
            .uri("/private/v1/configurations/stock_price_sync_enabled")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"value":"true"}""")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.key").isEqualTo("stock_price_sync_enabled")
            .jsonPath("$.value").isEqualTo("true")

        restTestClient.get()
            .uri("/private/v1/configurations")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content[?(@.key=='stock_price_sync_enabled')].value").isEqualTo("true")
    }

    @Test
    @Order(3)
    fun `rejects a blank value with 400`() {
        restTestClient.patch()
            .uri("/private/v1/configurations/stock_price_sync_enabled")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"value":""}""")
            .exchange()
            .expectStatus().isBadRequest()
    }

    @Test
    @Order(4)
    fun `returns 404 when updating an unknown key`() {
        restTestClient.patch()
            .uri("/private/v1/configurations/not_a_real_key")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"value":"true"}""")
            .exchange()
            .expectStatus().isNotFound()
    }

    @Test
    @Order(5)
    fun `a non-admin is forbidden from updating a configuration`() {
        val cookie = registerApproveAndLogin("configurations-writer@example.com", "Senha123")

        restTestClient.patch()
            .uri("/private/v1/configurations/stock_price_sync_enabled")
            .header("Cookie", cookie)
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"value":"true"}""")
            .exchange()
            .expectStatus().isEqualTo(403)
    }

    @Test
    @Order(6)
    fun `returns the seeded usd_price_sync_enabled configuration as false`() {
        restTestClient.get()
            .uri("/private/v1/configurations")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content[?(@.key=='usd_price_sync_enabled')].value").isEqualTo("false")
    }

    private fun registerApproveAndLogin(email: String, password: String): String {
        restTestClient.post()
            .uri("/private/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Teste","email":"$email","password":"$password"}""")
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

        return restTestClient.post()
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
    }
}
