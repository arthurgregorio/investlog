package br.com.investlog.server.stockpricesync

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.auth.rest.payloads.SessionResponse
import br.com.investlog.server.auth.rest.payloads.TotpEnrollResponse
import br.com.investlog.server.stockholdings.rest.payloads.StockHoldingResponse
import br.com.investlog.server.typelists.rest.payloads.TypeResponse
import br.com.investlog.server.wallets.rest.payloads.WalletResponse
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import dev.samstevens.totp.code.DefaultCodeGenerator
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.returnResult
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StockPriceSyncControllerTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTestClient: RestTestClient

    lateinit var walletId: UUID
    lateinit var stockTypeId: UUID

    @BeforeAll
    fun setup() {
        walletId = restTestClient.post()
            .uri("/private/v1/wallets")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Force Sync Wallet","kind":"stocks","currency":"BRL"}""")
            .exchange()
            .returnResult<WalletResponse>()
            .responseBody!!
            .id

        stockTypeId = restTestClient.post()
            .uri("/private/v1/stock-types")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Ação Force Sync"}""")
            .exchange()
            .returnResult<TypeResponse>()
            .responseBody!!
            .id

        wireMockServer.stubFor(
            get(urlPathEqualTo("/v2/stocks/quote"))
                .withQueryParam("symbols", equalTo("FSYN3"))
                .withHeader("Authorization", equalTo("Bearer $TEST_TOKEN"))
                .willReturn(okJson(classpathResource("brapi/quote-wege3-response.json")))
        )
    }

    @AfterAll
    fun tearDown() {
        wireMockServer.stop()
    }

    private fun createHolding(ticker: String, currentPrice: BigDecimal): StockHoldingResponse =
        restTestClient.post()
            .uri("/private/v1/wallets/$walletId/stock-holdings")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                """
                {
                  "stockTypeId":"$stockTypeId",
                  "ticker":"$ticker",
                  "name":"$ticker",
                  "currentPrice":$currentPrice,
                  "lot":{"lotDate":"2024-01-15","quantity":100,"price":$currentPrice}
                }
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isCreated()
            .returnResult<StockHoldingResponse>()
            .responseBody!!

    private fun fetchHolding(holdingId: UUID): StockHoldingResponse =
        restTestClient.get()
            .uri("/private/v1/wallets/$walletId/stock-holdings/$holdingId")
            .exchange()
            .returnResult<StockHoldingResponse>()
            .responseBody!!

    @Test
    @Order(1)
    fun `admin forces a sync run even when the toggle is disabled`() {
        restTestClient.patch()
            .uri("/private/v1/configurations/stock_price_sync_enabled")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"value":"false"}""")
            .exchange()
            .expectStatus().isOk()

        val holding = createHolding("FSYN3", BigDecimal("10.00"))

        restTestClient.post()
            .uri("/private/v1/stock-price-sync")
            .exchange()
            .expectStatus().isNoContent()

        assertEquals(BigDecimal("44.26"), fetchHolding(holding.id).currentPrice)
    }

    @Test
    @Order(2)
    fun `a non-admin is forbidden from forcing a sync run`() {
        val cookie = registerApproveAndLogin("force-sync-writer@example.com", "senha123")

        restTestClient.post()
            .uri("/private/v1/stock-price-sync")
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
        private const val TEST_TOKEN = "test-token"
        private val wireMockServer = WireMockServer(wireMockConfig().dynamicPort())

        private fun classpathResource(path: String): String =
            StockPriceSyncControllerTest::class.java.classLoader.getResource(path)!!.readText()

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            wireMockServer.start()
            registry.add("investlog.brapi.base-url") { "http://localhost:${wireMockServer.port()}" }
            registry.add("investlog.brapi.token") { TEST_TOKEN }
        }
    }
}
