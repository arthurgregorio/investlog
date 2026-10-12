package br.com.investlog.server.overview

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.cryptoholdings.rest.payloads.CryptoHoldingResponse
import br.com.investlog.server.fundholdings.rest.payloads.FundHoldingResponse
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
class OverviewSeriesPayloadTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTestClient: RestTestClient

    lateinit var stockTypeId: UUID
    lateinit var fundTypeId: UUID

    @BeforeAll
    fun setup() {
        restTestClient.put()
            .uri("/private/v1/currency-rates/USD")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"rate":7.00}""")
            .exchange()
            .expectStatus().isOk()

        stockTypeId = createType("/private/v1/stock-types", "Series Payload Stock Type")
        fundTypeId = createType("/private/v1/fund-types", "Series Payload Fund Type")

        val brlStocksWalletId = createWallet("Series BRL Stocks", "STOCKS", "BRL")
        val usdStocksWalletId = createWallet("Series USD Stocks", "STOCKS", "USD")
        val brlCryptoWalletId = createWallet("Series BRL Crypto", "CRYPTO", "BRL")
        val usdFundsWalletId = createWallet("Series USD Funds", "FUNDS", "USD")

        createStockHolding(brlStocksWalletId, "SRBRL3", lotDate = "2025-01-15", quantity = "10", price = "45.00")
        createStockHolding(usdStocksWalletId, "SRUSD3", lotDate = "2025-01-20", quantity = "2", price = "100.00")
        createStockHolding(usdStocksWalletId, "SRUSE3", lotDate = "2025-03-05", quantity = "3", price = "33.33")
        createCryptoHolding(brlCryptoWalletId, "SRBTC", lotDate = "2025-02-10", quantity = "0.5", price = "1234.56")
        createFundHolding(usdFundsWalletId, "Series Fund", contributionDate = "2025-03-25", amount = "321.09")
    }

    private fun createType(path: String, name: String): UUID =
        restTestClient.post()
            .uri(path)
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"$name"}""")
            .exchange()
            .returnResult<TypeResponse>()
            .responseBody!!
            .id

    private fun createWallet(name: String, kind: String, currency: String): UUID =
        restTestClient.post()
            .uri("/private/v1/wallets")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"$name","kind":"$kind","currency":"$currency"}""")
            .exchange()
            .returnResult<WalletResponse>()
            .responseBody!!
            .id

    private fun createStockHolding(walletId: UUID, ticker: String, lotDate: String, quantity: String, price: String): UUID =
        restTestClient.post()
            .uri("/private/v1/wallets/$walletId/stock-holdings")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                """{"stockTypeId":"$stockTypeId","ticker":"$ticker","currentPrice":50.00,
                   "lot":{"lotDate":"$lotDate","quantity":$quantity,"price":$price}}"""
            )
            .exchange()
            .expectStatus().isCreated()
            .returnResult<StockHoldingResponse>()
            .responseBody!!
            .id

    private fun createCryptoHolding(walletId: UUID, ticker: String, lotDate: String, quantity: String, price: String): UUID =
        restTestClient.post()
            .uri("/private/v1/wallets/$walletId/crypto-holdings")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                """{"ticker":"$ticker","name":"Series $ticker","currentPrice":$price,
                   "lot":{"lotDate":"$lotDate","quantity":$quantity,"price":$price}}"""
            )
            .exchange()
            .expectStatus().isCreated()
            .returnResult<CryptoHoldingResponse>()
            .responseBody!!
            .id

    private fun createFundHolding(walletId: UUID, name: String, contributionDate: String, amount: String): UUID =
        restTestClient.post()
            .uri("/private/v1/wallets/$walletId/fund-holdings")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                """{"fundTypeId":"$fundTypeId","name":"$name","currentValue":$amount,
                   "contribution":{"contributionDate":"$contributionDate","amount":$amount}}"""
            )
            .exchange()
            .expectStatus().isCreated()
            .returnResult<FundHoldingResponse>()
            .responseBody!!
            .id

    private fun withdraw(walletId: UUID, holdingPath: String, holdingId: UUID, body: String) {
        restTestClient.post()
            .uri("/private/v1/wallets/$walletId/$holdingPath/$holdingId/withdrawals")
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .exchange()
            .expectStatus().isCreated()
    }

    private fun changePreferredCurrency(currency: String) {
        restTestClient.patch()
            .uri("/private/v1/profile")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"preferredCurrency":"$currency"}""")
            .exchange()
            .expectStatus().isOk()
    }

    private fun seriesPoints(): Map<String, BigDecimal> =
        restTestClient.get()
            .uri("/private/v1/overview/series")
            .exchange()
            .expectStatus().isOk()
            .returnResult<List<SeriesPointResponse>>()
            .responseBody!!
            .associate { point -> point.month to point.totalInvested }

    private fun summaryTotalCostBasis(): BigDecimal =
        restTestClient.get()
            .uri("/private/v1/overview")
            .exchange()
            .expectStatus().isOk()
            .returnResult<PortfolioSummaryResponse>()
            .responseBody!!
            .totalCostBasis

    private fun assertAmount(expected: String, actual: BigDecimal?) {
        assertEquals(
            BigDecimal(expected).setScale(8, RoundingMode.HALF_UP),
            actual?.setScale(8, RoundingMode.HALF_UP),
        )
    }

    private fun assertLastPointEqualsSummary(series: Map<String, BigDecimal>) {
        assertEquals(
            summaryTotalCostBasis().setScale(8, RoundingMode.HALF_UP),
            series.values.last().setScale(8, RoundingMode.HALF_UP),
        )
    }

    private fun seriesBody(): String =
        restTestClient.get()
            .uri("/private/v1/overview/series")
            .exchange()
            .expectStatus().isOk()
            .returnResult<String>()
            .responseBody!!

    @Test
    @Order(1)
    fun `the series in the anchor currency accumulates every kind month by month`() {
        assertEquals(
            """[{"month":"2025-01","totalInvested":1850.0000000000000000000000},{"month":"2025-02","totalInvested":2467.28000000000000000000000},{"month":"2025-03","totalInvested":5414.84000000000000000000000}]""",
            seriesBody(),
        )
    }

    @Test
    @Order(2)
    fun `the series in another display currency divides by that currency's rate`() {
        changePreferredCurrency("USD")

        assertEquals(
            """[{"month":"2025-01","totalInvested":264.2857142857142857130000},{"month":"2025-02","totalInvested":352.46857142857142856837920},{"month":"2025-03","totalInvested":773.54857142857142856837920}]""",
            seriesBody(),
        )
    }

    @Test
    @Order(3)
    fun `a partial withdrawal lowers the total from its month on by the exited cost basis`() {
        changePreferredCurrency("BRL")
        val walletId = createWallet("Series BRL Partial Exit", "STOCKS", "BRL")
        val holdingId = createStockHolding(walletId, "SRPRT3", lotDate = "2025-04-10", quantity = "10", price = "30.00")

        withdraw(
            walletId,
            "stock-holdings",
            holdingId,
            """{"resultDate":"2025-05-15","quantity":4,"unitPrice":45.00}""",
        )

        val series = seriesPoints()
        assertAmount("5414.84", series["2025-03"])
        assertAmount("5714.84", series["2025-04"])
        assertAmount("5594.84", series["2025-05"])
        assertLastPointEqualsSummary(series)
    }

    @Test
    @Order(4)
    fun `a full exit brings the holding's contribution back to zero from its exit month`() {
        val walletId = createWallet("Series BRL Full Exit", "CRYPTO", "BRL")
        val holdingId = createCryptoHolding(walletId, "SRETH", lotDate = "2025-06-05", quantity = "2", price = "250.00")

        withdraw(
            walletId,
            "crypto-holdings",
            holdingId,
            """{"resultDate":"2025-07-20","quantity":2,"unitPrice":300.00}""",
        )

        val series = seriesPoints()
        assertAmount("5594.84", series["2025-05"])
        assertAmount("6094.84", series["2025-06"])
        assertAmount("5594.84", series["2025-07"])
        assertLastPointEqualsSummary(series)
    }

    @Test
    @Order(5)
    fun `a reinvestment counts the reinvested money once, in the destination`() {
        val stocksWalletId = createWallet("Series BRL Reinvestment Source", "STOCKS", "BRL")
        val fundsWalletId = createWallet("Series BRL Reinvestment Destination", "FUNDS", "BRL")
        val sourceId = createStockHolding(stocksWalletId, "SRSRC3", lotDate = "2025-08-05", quantity = "10", price = "50.00")
        val destinationId = createFundHolding(fundsWalletId, "Series Destination Fund", contributionDate = "2025-08-06", amount = "100.00")

        restTestClient.post()
            .uri("/private/v1/reinvestments")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                """{"sourceKind":"STOCKS","sourceHoldingId":"$sourceId",
                   "destinationKind":"FUNDS","destinationHoldingId":"$destinationId",
                   "reinvestmentDate":"2025-09-10","quantity":5,"unitPrice":60.00}"""
            )
            .exchange()
            .expectStatus().isCreated()

        val series = seriesPoints()
        assertAmount("6194.84", series["2025-08"])
        assertAmount("6244.84", series["2025-09"])
        assertLastPointEqualsSummary(series)
    }
}
