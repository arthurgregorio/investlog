package br.com.investlog.server.overview

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.overview.rest.payloads.PortfolioSummaryResponse
import br.com.investlog.server.overview.rest.payloads.SeriesPointResponse
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
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OverviewCapitalSeriesTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTestClient: RestTestClient

    lateinit var stockTypeId: UUID
    lateinit var realWalletId: UUID
    lateinit var tradedHoldingId: UUID

    @BeforeAll
    fun setup() {
        restTestClient.put()
            .uri("/private/v1/currency-rates/USD")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"rate":5.00}""")
            .exchange()
            .expectStatus().isOk()

        stockTypeId = restTestClient.post()
            .uri("/private/v1/holding-types")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Capital Series Type","kinds":["STOCKS"]}""")
            .exchange()
            .returnResult<TypeResponse>()
            .responseBody!!
            .id

        realWalletId = createWallet("Capital Real", "BRL")
        val dollarWalletId = createWallet("Capital Dólar", "USD")

        tradedHoldingId = createStock(realWalletId, "CAPX3", "2025-01-10", "10", "30")
        val sourceHoldingId = createStock(realWalletId, "CAPZ3", "2025-01-11", "10", "10")
        val dollarHoldingId = createStock(dollarWalletId, "CAPY", "2025-01-12", "10", "10")

        post("/private/v1/wallets/$realWalletId/stock-holdings/$tradedHoldingId/withdrawals", """{"resultDate":"2025-02-01","quantity":4,"unitPrice":50}""")
        post("/private/v1/wallets/$realWalletId/stock-holdings/$tradedHoldingId/lots", """{"lotDate":"2025-02-15","quantity":2,"price":50}""")
        post("/private/v1/wallets/$dollarWalletId/stock-holdings/$dollarHoldingId/withdrawals", """{"resultDate":"2025-02-05","quantity":5,"unitPrice":20}""")
        post("/private/v1/wallets/$dollarWalletId/cash-transfers", """{"destinationWalletId":"$realWalletId","transferDate":"2025-02-10","amount":100,"rate":5.2}""")
        post("/private/v1/wallets/$realWalletId/cash-withdrawals", """{"withdrawalDate":"2025-03-01","amount":50,"bankName":"Itaú"}""")
        post(
            "/private/v1/reinvestments",
            """
            {
              "sourceKind":"STOCKS","sourceHoldingId":"$sourceHoldingId",
              "destinationKind":"STOCKS","destinationHoldingId":"$tradedHoldingId",
              "reinvestmentDate":"2025-03-10","quantity":5,"unitPrice":20
            }
            """.trimIndent(),
        )
    }

    private fun createWallet(name: String, currency: String): UUID = restTestClient.post()
        .uri("/private/v1/wallets")
        .contentType(MediaType.APPLICATION_JSON)
        .body("""{"name":"$name","kind":"STOCKS","currency":"$currency"}""")
        .exchange()
        .returnResult<WalletResponse>()
        .responseBody!!
        .id

    private fun createStock(walletId: UUID, ticker: String, lotDate: String, quantity: String, price: String): UUID =
        restTestClient.post()
            .uri("/private/v1/wallets/$walletId/stock-holdings")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                """{"typeId":"$stockTypeId","ticker":"$ticker","currentPrice":50,
                   "lot":{"lotDate":"$lotDate","quantity":$quantity,"price":$price}}"""
            )
            .exchange()
            .expectStatus().isCreated()
            .returnResult<StockHoldingResponse>()
            .responseBody!!
            .id

    private fun post(uri: String, body: String) {
        restTestClient.post()
            .uri(uri)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .exchange()
            .expectStatus().isCreated()
    }

    private fun series(): List<SeriesPointResponse> = restTestClient.get()
        .uri("/private/v1/overview/series")
        .exchange()
        .expectStatus().isOk()
        .returnResult<List<SeriesPointResponse>>()
        .responseBody!!

    private fun summary(): PortfolioSummaryResponse = restTestClient.get()
        .uri("/private/v1/overview")
        .exchange()
        .expectStatus().isOk()
        .returnResult<PortfolioSummaryResponse>()
        .responseBody!!

    private fun cents(value: BigDecimal): BigDecimal = value.setScale(2, RoundingMode.HALF_UP)

    private fun assertMonth(point: SeriesPointResponse, month: String, ownMoney: String, reinvestedProfit: String) {
        assertEquals(month, point.month)
        assertEquals(BigDecimal(ownMoney), cents(point.ownMoney))
        assertEquals(BigDecimal(reinvestedProfit), cents(point.reinvestedProfit))
        assertEquals(cents(point.ownMoney + point.reinvestedProfit), cents(point.totalInvested))
    }

    @Test
    @Order(1)
    fun `own money counts new money and transfers less bank withdrawals, and every exit adds its profit`() {
        val points = series()

        assertEquals(3, points.size)
        assertMonth(points[0], "2025-01", "900.00", "0.00")
        assertMonth(points[1], "2025-02", "920.00", "330.00")
        assertMonth(points[2], "2025-03", "870.00", "380.00")
    }

    @Test
    @Order(2)
    fun `the series ends at the invested cost basis plus the cash, also after a partial move`() {
        val before = series().last()
        val summaryBefore = summary()
        assertEquals(cents(summaryBefore.totalCostBasis + summaryBefore.totalCash), cents(before.ownMoney + before.reinvestedProfit))
        assertEquals(BigDecimal("1250.00"), cents(before.totalInvested))

        val destinationWalletId = createWallet("Capital Destino", "BRL")
        post("/private/v1/wallets/$realWalletId/moves", """{"destinationWalletId":"$destinationWalletId","items":[{"holdingId":"$tradedHoldingId","quantity":3}]}""")

        val after = series().last()
        val summaryAfter = summary()
        assertEquals(cents(summaryAfter.totalCostBasis + summaryAfter.totalCash), cents(after.ownMoney + after.reinvestedProfit))
        assertEquals(BigDecimal("1250.00"), cents(after.totalInvested))
    }
}
