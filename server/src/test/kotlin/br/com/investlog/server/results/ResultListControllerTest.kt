package br.com.investlog.server.results

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.auth.rest.payloads.SessionResponse
import br.com.investlog.server.auth.rest.payloads.TotpEnrollResponse
import br.com.investlog.server.stockholdings.rest.payloads.StockHoldingResponse
import br.com.investlog.server.typelists.rest.payloads.TypeResponse
import br.com.investlog.server.wallets.rest.payloads.WalletResponse
import dev.samstevens.totp.code.DefaultCodeGenerator
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.returnResult
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ResultListControllerTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTestClient: RestTestClient

    lateinit var stockTypeId: UUID
    lateinit var otherUserCookie: String

    @BeforeAll
    fun setup() {
        stockTypeId = restTestClient.post()
            .uri("/private/v1/holding-types")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"List Stock Type","kinds":["STOCKS"]}""")
            .exchange()
            .returnResult<TypeResponse>()
            .responseBody!!
            .id

        val walletId = createWallet("List Wallet")
        val withdrawnHoldingId = createStockHolding(walletId, "LIST3")
        val reinvestedHoldingId = createStockHolding(walletId, "LIST4")

        withdraw(walletId, withdrawnHoldingId, """{"resultDate":"2026-03-10","quantity":10,"unitPrice":30.00}""")
        withdraw(walletId, withdrawnHoldingId, """{"resultDate":"2026-06-10","quantity":20,"unitPrice":25.00}""")

        restTestClient.post()
            .uri("/private/v1/reinvestments")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                """
                {
                  "sourceKind":"STOCKS","sourceHoldingId":"$reinvestedHoldingId",
                  "destinationKind":"STOCKS","destinationHoldingId":"$withdrawnHoldingId",
                  "reinvestmentDate":"2026-04-15","quantity":10,"unitPrice":30.00
                }
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isCreated()

        otherUserCookie = registerApproveAndLogin("resultados-outro@example.com", "Senha123")

        val otherWalletId = createWallet("Other User Wallet", otherUserCookie)
        val otherHoldingId = createStockHolding(otherWalletId, "OTHR3", otherUserCookie)

        withdraw(otherWalletId, otherHoldingId, """{"resultDate":"2026-05-10","quantity":5,"unitPrice":30.00}""", otherUserCookie)
    }

    private fun createWallet(name: String, cookie: String? = null): UUID = restTestClient.post()
        .uri("/private/v1/wallets")
        .contentType(MediaType.APPLICATION_JSON)
        .apply { cookie?.let { header("Cookie", it) } }
        .body("""{"name":"$name","kind":"stocks","currency":"BRL"}""")
        .exchange()
        .returnResult<WalletResponse>()
        .responseBody!!
        .id

    private fun createStockHolding(walletId: UUID, ticker: String, cookie: String? = null): UUID = restTestClient.post()
        .uri("/private/v1/wallets/$walletId/stock-holdings")
        .contentType(MediaType.APPLICATION_JSON)
        .apply { cookie?.let { header("Cookie", it) } }
        .body(
            """
            {
              "typeId":"$stockTypeId",
              "ticker":"$ticker",
              "currentPrice":30.00,
              "lot":{"lotDate":"2026-01-10","quantity":100,"price":20.00}
            }
            """.trimIndent()
        )
        .exchange()
        .expectStatus().isCreated()
        .returnResult<StockHoldingResponse>()
        .responseBody!!
        .id

    private fun withdraw(walletId: UUID, holdingId: UUID, body: String, cookie: String? = null) = restTestClient.post()
        .uri("/private/v1/wallets/$walletId/stock-holdings/$holdingId/withdrawals")
        .contentType(MediaType.APPLICATION_JSON)
        .apply { cookie?.let { header("Cookie", it) } }
        .body(body)
        .exchange()
        .expectStatus().isCreated()

    private fun listResults(query: String, cookie: String? = null): Map<String, Any?> = restTestClient.get()
        .uri("/private/v1/results$query")
        .apply { cookie?.let { header("Cookie", it) } }
        .exchange()
        .expectStatus().isOk()
        .returnResult<Map<String, Any?>>()
        .responseBody!!

    private fun Map<String, Any?>.rows(): List<Map<*, *>> = (this["content"] as List<*>).map { it as Map<*, *> }

    private fun Map<String, Any?>.totalElements(): Int = ((this["page"] as Map<*, *>)["totalElements"] as Number).toInt()

    @Test
    @Order(1)
    fun `without a result type every result of the user is listed`() {

        val response = listResults("")

        assertEquals(3, response.totalElements())
        assertEquals(
            listOf("WITHDRAWAL", "REINVESTMENT", "WITHDRAWAL"),
            response.rows().map { it["resultType"] },
        )
    }

    @Test
    @Order(2)
    fun `filtering by WITHDRAWAL lists only withdrawals, newest first, with a matching total`() {

        val response = listResults("?resultType=WITHDRAWAL")

        assertEquals(2, response.totalElements())
        assertEquals(listOf("WITHDRAWAL", "WITHDRAWAL"), response.rows().map { it["resultType"] })
        assertEquals(listOf("2026-06-10", "2026-03-10"), response.rows().map { it["resultDate"] })
    }

    @Test
    @Order(3)
    fun `the withdrawal filter pages through withdrawals alone`() {

        val firstPage = listResults("?resultType=WITHDRAWAL&size=1&page=0")
        val secondPage = listResults("?resultType=WITHDRAWAL&size=1&page=1")

        assertEquals(2, firstPage.totalElements())
        assertEquals(2, ((firstPage["page"] as Map<*, *>)["totalPages"] as Number).toInt())
        assertEquals(listOf("2026-06-10"), firstPage.rows().map { it["resultDate"] })
        assertEquals(listOf("2026-03-10"), secondPage.rows().map { it["resultDate"] })
    }

    @Test
    @Order(4)
    fun `filtering by REINVESTMENT lists only reinvestments with a matching total`() {

        val response = listResults("?resultType=REINVESTMENT")

        assertEquals(1, response.totalElements())
        assertEquals(listOf("REINVESTMENT"), response.rows().map { it["resultType"] })
        assertEquals(listOf("LIST4"), response.rows().map { it["ticker"] })
    }

    @Test
    @Order(5)
    fun `an unknown result type is rejected with 400`() {

        restTestClient.get()
            .uri("/private/v1/results?resultType=SALE")
            .exchange()
            .expectStatus().isBadRequest()
    }

    @Test
    @Order(6)
    fun `another user's results never appear in the list`() {

        val adminTickers = listResults("?resultType=WITHDRAWAL").rows().map { it["ticker"] }
        val otherUserResponse = listResults("?resultType=WITHDRAWAL", otherUserCookie)

        assertEquals(listOf("LIST3", "LIST3"), adminTickers)
        assertEquals(1, otherUserResponse.totalElements())
        assertEquals(listOf("OTHR3"), otherUserResponse.rows().map { it["ticker"] })
        assertEquals(0, listResults("?resultType=REINVESTMENT", otherUserCookie).totalElements())
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
