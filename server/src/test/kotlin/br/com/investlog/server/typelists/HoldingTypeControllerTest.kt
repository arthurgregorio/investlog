package br.com.investlog.server.typelists

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.auth.rest.payloads.SessionResponse
import br.com.investlog.server.auth.rest.payloads.TotpEnrollResponse
import br.com.investlog.server.typelists.rest.payloads.HoldingTypeResponse
import br.com.investlog.server.wallets.rest.payloads.WalletKind
import br.com.investlog.server.wallets.rest.payloads.WalletResponse
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

class HoldingTypeControllerTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTestClient: RestTestClient

    @Test
    @Order(1)
    fun `lists the stock and fund types carried over plus the seeded crypto type`() {
        restTestClient.get()
            .uri("/private/v1/holding-types")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.page.totalElements").isEqualTo(7)
            .jsonPath("$.page.size").isEqualTo(20)

        restTestClient.get()
            .uri("/private/v1/holding-types?kind=CRYPTO")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.page.totalElements").isEqualTo(1)
            .jsonPath("$.content[0].name").isEqualTo("Cripto")
            .jsonPath("$.content[0].kinds[0]").isEqualTo("CRYPTO")
    }

    @Test
    @Order(2)
    fun `filters by kind`() {
        val funds = listContent("/private/v1/holding-types?kind=FUNDS&size=200")

        assertEquals(3, funds.size)
        assertTrue(funds.all { holdingType -> holdingType["kinds"] == listOf("FUNDS") })
    }

    @Test
    @Order(3)
    fun `creates a type for several kinds and returns them in kind order`() {
        val response = createHoldingType("Ações Globais", """["FUNDS","STOCKS"]""")

        assertEquals("Ações Globais", response.name)
        assertEquals(listOf(WalletKind.STOCKS, WalletKind.FUNDS), response.kinds)
        assertEquals(0, response.usageCount)
        multiKindTypeId = response.id

        val stocks = listContent("/private/v1/holding-types?kind=STOCKS&size=200")
        val funds = listContent("/private/v1/holding-types?kind=FUNDS&size=200")
        val crypto = listContent("/private/v1/holding-types?kind=CRYPTO&size=200")
        assertTrue(stocks.any { holdingType -> holdingType["id"] == multiKindTypeId.toString() })
        assertTrue(funds.any { holdingType -> holdingType["id"] == multiKindTypeId.toString() })
        assertTrue(crypto.none { holdingType -> holdingType["id"] == multiKindTypeId.toString() })
    }

    @Test
    @Order(4)
    fun `rejects a type without kinds with 400`() {
        restTestClient.post()
            .uri("/private/v1/holding-types")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Sem Tipos","kinds":[]}""")
            .exchange()
            .expectStatus().isBadRequest()

        restTestClient.post()
            .uri("/private/v1/holding-types")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Sem Tipos"}""")
            .exchange()
            .expectStatus().isBadRequest()

        restTestClient.put()
            .uri("/private/v1/holding-types/$multiKindTypeId")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Ações Globais","kinds":[]}""")
            .exchange()
            .expectStatus().isBadRequest()
    }

    @Test
    @Order(5)
    fun `rejects a blank name with 400`() {
        restTestClient.post()
            .uri("/private/v1/holding-types")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"","kinds":["STOCKS"]}""")
            .exchange()
            .expectStatus().isBadRequest()
    }

    @Test
    @Order(6)
    fun `rejects a duplicate name with 409`() {
        restTestClient.post()
            .uri("/private/v1/holding-types")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Ações Globais","kinds":["CRYPTO"]}""")
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.CONFLICT)
    }

    @Test
    @Order(7)
    fun `renames a type and changes its kinds`() {
        val original = createHoldingType("Tipo Para Renomear", """["STOCKS"]""")

        val renamed = restTestClient.put()
            .uri("/private/v1/holding-types/${original.id}")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Tipo Renomeado","kinds":["CRYPTO","FUNDS"]}""")
            .exchange()
            .expectStatus().isOk()
            .returnResult<HoldingTypeResponse>()
            .responseBody!!

        assertEquals(original.id, renamed.id)
        assertEquals("Tipo Renomeado", renamed.name)
        assertEquals(listOf(WalletKind.CRYPTO, WalletKind.FUNDS), renamed.kinds)
    }

    @Test
    @Order(8)
    fun `returns 404 when renaming or deleting an unknown id`() {
        restTestClient.put()
            .uri("/private/v1/holding-types/${UUID.randomUUID()}")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Não Importa","kinds":["STOCKS"]}""")
            .exchange()
            .expectStatus().isNotFound()

        restTestClient.delete()
            .uri("/private/v1/holding-types/${UUID.randomUUID()}")
            .exchange()
            .expectStatus().isNotFound()
    }

    @Test
    @Order(9)
    fun `removing a kind still used by a holding is rejected with 409 while an unused kind can go`() {
        val walletId = createWallet("Wallet Tipo Multi", "stocks")
        createStockHolding(walletId, multiKindTypeId, "MULT3")

        val listed = listContent("/private/v1/holding-types?size=200")
            .single { holdingType -> holdingType["id"] == multiKindTypeId.toString() }
        assertEquals(1, listed["usageCount"])

        restTestClient.put()
            .uri("/private/v1/holding-types/$multiKindTypeId")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Ações Globais","kinds":["FUNDS"]}""")
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.CONFLICT)

        val narrowed = restTestClient.put()
            .uri("/private/v1/holding-types/$multiKindTypeId")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Ações Globais","kinds":["STOCKS"]}""")
            .exchange()
            .expectStatus().isOk()
            .returnResult<HoldingTypeResponse>()
            .responseBody!!

        assertEquals(listOf(WalletKind.STOCKS), narrowed.kinds)
        assertEquals(1, narrowed.usageCount)
    }

    @Test
    @Order(10)
    fun `rejects deleting a type that is in use`() {
        restTestClient.delete()
            .uri("/private/v1/holding-types/$multiKindTypeId")
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.CONFLICT)
    }

    @Test
    @Order(11)
    fun `deletes an unused type`() {
        val unused = createHoldingType("Tipo Descartável", """["CRYPTO"]""")

        restTestClient.delete()
            .uri("/private/v1/holding-types/${unused.id}")
            .exchange()
            .expectStatus().isNoContent()

        assertTrue(listContent("/private/v1/holding-types?size=200").none { holdingType -> holdingType["id"] == unused.id.toString() })
    }

    @Test
    @Order(12)
    fun `a non-admin reads the global list but is forbidden from writing it`() {
        val cookie = registerApproveAndLogin("holding-types-reader@example.com", "Senha123")

        restTestClient.get()
            .uri("/private/v1/holding-types?kind=STOCKS")
            .header("Cookie", cookie)
            .exchange()
            .expectStatus().isOk()

        restTestClient.post()
            .uri("/private/v1/holding-types")
            .header("Cookie", cookie)
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Tentativa Não Admin","kinds":["STOCKS"]}""")
            .exchange()
            .expectStatus().isEqualTo(403)

        restTestClient.put()
            .uri("/private/v1/holding-types/$multiKindTypeId")
            .header("Cookie", cookie)
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Tentativa Não Admin","kinds":["STOCKS"]}""")
            .exchange()
            .expectStatus().isEqualTo(403)

        restTestClient.delete()
            .uri("/private/v1/holding-types/$multiKindTypeId")
            .header("Cookie", cookie)
            .exchange()
            .expectStatus().isEqualTo(403)
    }

    private fun createHoldingType(name: String, kinds: String): HoldingTypeResponse =
        restTestClient.post()
            .uri("/private/v1/holding-types")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"$name","kinds":$kinds}""")
            .exchange()
            .expectStatus().isCreated()
            .returnResult<HoldingTypeResponse>()
            .responseBody!!

    private fun createWallet(name: String, kind: String): UUID =
        restTestClient.post()
            .uri("/private/v1/wallets")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"$name","kind":"$kind","currency":"BRL"}""")
            .exchange()
            .expectStatus().isCreated()
            .returnResult<WalletResponse>()
            .responseBody!!
            .id

    private fun createStockHolding(walletId: UUID, typeId: UUID, ticker: String) {
        restTestClient.post()
            .uri("/private/v1/wallets/$walletId/stock-holdings")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                """
                {
                  "typeId":"$typeId",
                  "ticker":"$ticker",
                  "currentPrice":10.00,
                  "lot":{"lotDate":"2024-01-15","quantity":10,"price":10.00}
                }
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isCreated()
    }

    private fun listContent(uri: String): List<Map<String, Any?>> {
        val listResponse = restTestClient.get()
            .uri(uri)
            .exchange()
            .expectStatus().isOk()
            .returnResult<Map<String, Any?>>()
            .responseBody

        @Suppress("UNCHECKED_CAST")
        return listResponse?.get("content") as List<Map<String, Any?>>
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
        private lateinit var multiKindTypeId: UUID
    }
}
