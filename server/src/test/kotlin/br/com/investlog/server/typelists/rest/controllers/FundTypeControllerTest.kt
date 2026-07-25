package br.com.investlog.server.typelists.rest.controllers

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.auth.rest.payloads.SessionResponse
import br.com.investlog.server.auth.rest.payloads.TotpEnrollResponse
import br.com.investlog.server.typelists.rest.payloads.TypeResponse
import dev.samstevens.totp.code.DefaultCodeGenerator
import org.junit.jupiter.api.Order
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.returnResult
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FundTypeControllerTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTestClient: RestTestClient

    @Test
    @Order(1)
    fun `returns the seeded default fund types initially`() {
        restTestClient.get()
            .uri("/private/v1/fund-types")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.page.totalElements").isEqualTo(3)
            .jsonPath("$.content").isArray()
    }

    @Test
    @Order(2)
    fun `creates a fund type`() {
        val response = restTestClient.post()
            .uri("/private/v1/fund-types")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Fundos Imobiliários"}""")
            .exchange()
            .expectStatus().isCreated()
            .returnResult<TypeResponse>()
            .responseBody

        assertEquals("Fundos Imobiliários", response?.name)
        createdId = response?.id
    }

    @Test
    @Order(3)
    fun `lists the created fund type`() {
        restTestClient.get()
            .uri("/private/v1/fund-types")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.page.totalElements").isEqualTo(4)
            .jsonPath("$.content[0].name").isEqualTo("Fundos Imobiliários")
    }

    @Test
    @Order(4)
    fun `rejects a duplicate name with 409`() {
        restTestClient.post()
            .uri("/private/v1/fund-types")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Fundos Imobiliários"}""")
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.CONFLICT)
    }

    @Test
    @Order(5)
    fun `deletes the created fund type`() {
        restTestClient.delete()
            .uri("/private/v1/fund-types/${createdId}")
            .exchange()
            .expectStatus().isNoContent()
    }

    @Test
    @Order(6)
    fun `returns 404 when deleting an unknown id`() {
        restTestClient.delete()
            .uri("/private/v1/fund-types/${UUID.randomUUID()}")
            .exchange()
            .expectStatus().isNotFound()
    }

    @Test
    @Order(7)
    fun `admin creates a global fund type visible to any approved user`() {
        val response = restTestClient.post()
            .uri("/private/v1/fund-types")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Fundo Compartilhado"}""")
            .exchange()
            .expectStatus().isCreated()
            .returnResult<TypeResponse>()
            .responseBody

        sharedTypeId = response!!.id

        val cookie = registerApproveAndLogin("fund-types-reader@example.com", "senha123")

        val listResponse = restTestClient.get()
            .uri("/private/v1/fund-types?size=200")
            .header("Cookie", cookie)
            .exchange()
            .expectStatus().isOk()
            .returnResult<Map<String, Any?>>()
            .responseBody

        @Suppress("UNCHECKED_CAST")
        val content = listResponse?.get("content") as List<Map<String, Any?>>
        assertTrue(content.any { it["name"] == "Fundo Compartilhado" })
    }

    @Test
    @Order(8)
    fun `a non-admin is forbidden from creating or deleting a fund type`() {
        val cookie = registerApproveAndLogin("fund-types-writer@example.com", "senha123")

        restTestClient.post()
            .uri("/private/v1/fund-types")
            .header("Cookie", cookie)
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Tentativa Não Admin"}""")
            .exchange()
            .expectStatus().isEqualTo(403)

        restTestClient.delete()
            .uri("/private/v1/fund-types/$sharedTypeId")
            .header("Cookie", cookie)
            .exchange()
            .expectStatus().isEqualTo(403)
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

    companion object {
        private var createdId: UUID? = null
        private lateinit var sharedTypeId: UUID
    }
}
