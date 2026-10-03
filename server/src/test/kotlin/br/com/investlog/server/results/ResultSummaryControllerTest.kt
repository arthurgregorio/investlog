package br.com.investlog.server.results

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.stockholdings.rest.payloads.StockHoldingResponse
import br.com.investlog.server.typelists.rest.payloads.TypeResponse
import br.com.investlog.server.wallets.rest.payloads.WalletResponse
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.returnResult
import java.util.UUID
import kotlin.test.Test

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ResultSummaryControllerTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTestClient: RestTestClient

    lateinit var stockTypeId: UUID
    lateinit var brlWalletId: UUID
    lateinit var usdWalletId: UUID

    @BeforeAll
    fun setup() {
        stockTypeId = restTestClient.post()
            .uri("/private/v1/stock-types")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Summary Stock Type"}""")
            .exchange()
            .returnResult<TypeResponse>()
            .responseBody!!
            .id

        brlWalletId = createWallet("Summary BRL Wallet", "BRL")
        usdWalletId = createWallet("Summary USD Wallet", "USD")

        restTestClient.put()
            .uri("/private/v1/currency-rates/USD")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"rate":5.00}""")
            .exchange()
            .expectStatus().isOk()
    }

    private fun createWallet(name: String, currency: String): UUID = restTestClient.post()
        .uri("/private/v1/wallets")
        .contentType(MediaType.APPLICATION_JSON)
        .body("""{"name":"$name","kind":"stocks","currency":"$currency"}""")
        .exchange()
        .returnResult<WalletResponse>()
        .responseBody!!
        .id

    private fun createStockHolding(walletId: UUID, ticker: String): UUID = restTestClient.post()
        .uri("/private/v1/wallets/$walletId/stock-holdings")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            """
            {
              "stockTypeId":"$stockTypeId",
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

    private fun withdraw(walletId: UUID, holdingId: UUID, body: String) = restTestClient.post()
        .uri("/private/v1/wallets/$walletId/stock-holdings/$holdingId/withdrawals")
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .exchange()
        .expectStatus().isCreated()

    @Test
    @Order(1)
    fun `a user with no results gets zeroed totals rather than an error`() {

        restTestClient.get()
            .uri("/private/v1/results/summary")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.displayCurrency").isEqualTo("BRL")
            .jsonPath("$.totalWithdrawn").isEqualTo(0)
            .jsonPath("$.totalNetReceived").isEqualTo(0)
            .jsonPath("$.totalProfit").isEqualTo(0)
            .jsonPath("$.totalFees").isEqualTo(0)
            .jsonPath("$.totalTaxes").isEqualTo(0)
            .jsonPath("$.exitCount").isEqualTo(0)
    }

    @Test
    @Order(2)
    fun `totals add up the recorded results`() {

        val holdingId = createStockHolding(brlWalletId, "SUMM3")

        withdraw(brlWalletId, holdingId, """{"resultDate":"2026-03-10","quantity":10,"unitPrice":30.00,"fees":2,"taxes":3}""")
        withdraw(brlWalletId, holdingId, """{"resultDate":"2026-06-10","quantity":20,"unitPrice":25.00,"fees":5,"taxes":0}""")

        restTestClient.get()
            .uri("/private/v1/results/summary")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.totalWithdrawn").isEqualTo(800.0)
            .jsonPath("$.totalNetReceived").isEqualTo(790.0)
            .jsonPath("$.totalProfit").isEqualTo(190.0)
            .jsonPath("$.totalFees").isEqualTo(7.0)
            .jsonPath("$.totalTaxes").isEqualTo(3.0)
            .jsonPath("$.exitCount").isEqualTo(2)
    }

    @Test
    @Order(3)
    fun `a date range keeps only the results dated inside it`() {

        restTestClient.get()
            .uri("/private/v1/results/summary?from=2026-06-01&to=2026-06-30")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.totalWithdrawn").isEqualTo(500.0)
            .jsonPath("$.totalProfit").isEqualTo(95.0)
            .jsonPath("$.exitCount").isEqualTo(1)

        restTestClient.get()
            .uri("/private/v1/results/summary?to=2026-03-10")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.totalWithdrawn").isEqualTo(300.0)
            .jsonPath("$.exitCount").isEqualTo(1)

        restTestClient.get()
            .uri("/private/v1/results/summary?from=2026-07-01")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.totalWithdrawn").isEqualTo(0)
            .jsonPath("$.exitCount").isEqualTo(0)
    }

    @Test
    @Order(4)
    fun `results in another currency are converted with the configured rate`() {

        val holdingId = createStockHolding(usdWalletId, "USDS3")

        withdraw(usdWalletId, holdingId, """{"resultDate":"2026-06-15","quantity":10,"unitPrice":30.00,"fees":1,"taxes":0}""")

        restTestClient.get()
            .uri("/private/v1/results/summary")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.totalWithdrawn").isEqualTo(2300.0)
            .jsonPath("$.totalFees").isEqualTo(12.0)
            .jsonPath("$.totalProfit").isEqualTo(685.0)
            .jsonPath("$.exitCount").isEqualTo(3)
    }
}
