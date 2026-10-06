package br.com.investlog.server.typelists

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.auth.rest.payloads.SessionResponse
import br.com.investlog.server.auth.rest.payloads.TotpEnrollResponse
import br.com.investlog.server.typelists.rest.payloads.TypeResponse
import br.com.investlog.server.wallets.rest.payloads.WalletResponse
import dev.samstevens.totp.code.DefaultCodeGenerator
import org.junit.jupiter.api.Order
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.returnResult
import java.util.UUID
import kotlin.collections.get
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StockSegmentControllerTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTestClient: RestTestClient

    @Test
    @Order(1)
    fun `returns no segments initially`() {
        restTestClient.get()
            .uri("/private/v1/stock-segments")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.page.totalElements").isEqualTo(0)
            .jsonPath("$.page.size").isEqualTo(20)
            .jsonPath("$.content").isArray()
    }

    @Test
    @Order(2)
    fun `creates a stock segment`() {
        val response = restTestClient.post()
            .uri("/private/v1/stock-segments")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Tecnologia"}""")
            .exchange()
            .expectStatus().isCreated()
            .returnResult<TypeResponse>()
            .responseBody

        assertEquals("Tecnologia", response?.name)
        createdId = response?.id
    }

    @Test
    @Order(3)
    fun `lists the created stock segment`() {
        restTestClient.get()
            .uri("/private/v1/stock-segments")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.page.totalElements").isEqualTo(1)
            .jsonPath("$.content[0].name").isEqualTo("Tecnologia")
    }

    @Test
    @Order(4)
    fun `rejects a duplicate name with 409`() {
        restTestClient.post()
            .uri("/private/v1/stock-segments")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Tecnologia"}""")
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.CONFLICT)
    }

    @Test
    @Order(5)
    fun `rejects a blank name with 400`() {
        restTestClient.post()
            .uri("/private/v1/stock-segments")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":""}""")
            .exchange()
            .expectStatus().isBadRequest()
    }

    @Test
    @Order(6)
    fun `deletes the created stock segment`() {
        restTestClient.delete()
            .uri("/private/v1/stock-segments/${createdId}")
            .exchange()
            .expectStatus().isNoContent()
    }

    @Test
    @Order(7)
    fun `returns 404 when deleting an unknown id`() {
        restTestClient.delete()
            .uri("/private/v1/stock-segments/${UUID.randomUUID()}")
            .exchange()
            .expectStatus().isNotFound()
    }

    @Test
    @Order(8)
    fun `admin creates a global stock segment visible to any approved user`() {
        val response = restTestClient.post()
            .uri("/private/v1/stock-segments")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Segmento Compartilhado"}""")
            .exchange()
            .expectStatus().isCreated()
            .returnResult<TypeResponse>()
            .responseBody

        sharedSegmentId = response!!.id

        val cookie = registerApproveAndLogin("stock-segments-reader@example.com", "Senha123")

        val listResponse = restTestClient.get()
            .uri("/private/v1/stock-segments?size=200")
            .header("Cookie", cookie)
            .exchange()
            .expectStatus().isOk()
            .returnResult<Map<String, Any?>>()
            .responseBody

        @Suppress("UNCHECKED_CAST")
        val content = listResponse?.get("content") as List<Map<String, Any?>>
        assertTrue(content.any { it["name"] == "Segmento Compartilhado" })
    }

    @Test
    @Order(9)
    fun `a non-admin is forbidden from creating or deleting a stock segment`() {
        val cookie = registerApproveAndLogin("stock-segments-writer@example.com", "Senha123")

        restTestClient.post()
            .uri("/private/v1/stock-segments")
            .header("Cookie", cookie)
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Tentativa Não Admin"}""")
            .exchange()
            .expectStatus().isEqualTo(403)

        restTestClient.delete()
            .uri("/private/v1/stock-segments/$sharedSegmentId")
            .header("Cookie", cookie)
            .exchange()
            .expectStatus().isEqualTo(403)
    }

    @Test
    @Order(10)
    fun `renames a stock segment`() {
        val original = restTestClient.post()
            .uri("/private/v1/stock-segments")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Segmento Para Renomear"}""")
            .exchange()
            .expectStatus().isCreated()
            .returnResult<TypeResponse>()
            .responseBody!!

        val renamed = restTestClient.put()
            .uri("/private/v1/stock-segments/${original.id}")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Segmento Renomeado"}""")
            .exchange()
            .expectStatus().isOk()
            .returnResult<TypeResponse>()
            .responseBody!!

        assertEquals(original.id, renamed.id)
        assertEquals("Segmento Renomeado", renamed.name)
        assertEquals(0, renamed.usageCount)
    }

    @Test
    @Order(11)
    fun `returns 404 when renaming an unknown id`() {
        restTestClient.put()
            .uri("/private/v1/stock-segments/${UUID.randomUUID()}")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Não Importa"}""")
            .exchange()
            .expectStatus().isNotFound()
    }

    @Test
    @Order(12)
    fun `a non-admin is forbidden from renaming a stock segment`() {
        val cookie = registerApproveAndLogin("stock-segments-renamer@example.com", "Senha123")

        restTestClient.put()
            .uri("/private/v1/stock-segments/$sharedSegmentId")
            .header("Cookie", cookie)
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Tentativa Não Admin"}""")
            .exchange()
            .expectStatus().isEqualTo(403)
    }

    @Test
    @Order(13)
    fun `rejects deleting a stock segment that is in use`() {
        val inUseSegment = restTestClient.post()
            .uri("/private/v1/stock-segments")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Segmento Em Uso"}""")
            .exchange()
            .expectStatus().isCreated()
            .returnResult<TypeResponse>()
            .responseBody!!

        val walletId = restTestClient.post()
            .uri("/private/v1/wallets")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Wallet Segmento Em Uso","kind":"stocks","currency":"BRL"}""")
            .exchange()
            .expectStatus().isCreated()
            .returnResult<WalletResponse>()
            .responseBody!!
            .id

        restTestClient.post()
            .uri("/private/v1/wallets/$walletId/stock-holdings")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                """
                {
                  "stockTypeId":"${firstStockTypeId()}",
                  "stockSegmentId":"${inUseSegment.id}",
                  "ticker":"TESTE4",
                  "name":"Teste",
                  "currentPrice":10.00,
                  "lot":{"lotDate":"2024-01-15","quantity":10,"price":10.00}
                }
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isCreated()

        val listResponse = restTestClient.get()
            .uri("/private/v1/stock-segments?size=200")
            .exchange()
            .expectStatus().isOk()
            .returnResult<Map<String, Any?>>()
            .responseBody

        @Suppress("UNCHECKED_CAST")
        val content = listResponse?.get("content") as List<Map<String, Any?>>
        assertEquals(1, content.single { it["id"] == inUseSegment.id.toString() }["usageCount"])

        restTestClient.delete()
            .uri("/private/v1/stock-segments/${inUseSegment.id}")
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.CONFLICT)
    }

    private fun firstStockTypeId(): String {
        val listResponse = restTestClient.get()
            .uri("/private/v1/stock-types")
            .exchange()
            .expectStatus().isOk()
            .returnResult<Map<String, Any?>>()
            .responseBody

        @Suppress("UNCHECKED_CAST")
        val content = listResponse?.get("content") as List<Map<String, Any?>>
        return content.first()["id"] as String
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
        private lateinit var sharedSegmentId: UUID
    }
}
