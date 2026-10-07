package br.com.investlog.server.overview

import br.com.investlog.server.BaseIntegrationTest
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

    private fun createStockHolding(walletId: UUID, ticker: String, lotDate: String, quantity: String, price: String) {
        restTestClient.post()
            .uri("/private/v1/wallets/$walletId/stock-holdings")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                """{"stockTypeId":"$stockTypeId","ticker":"$ticker","currentPrice":50.00,
                   "lot":{"lotDate":"$lotDate","quantity":$quantity,"price":$price}}"""
            )
            .exchange()
            .expectStatus().isCreated()
    }

    private fun createCryptoHolding(walletId: UUID, ticker: String, lotDate: String, quantity: String, price: String) {
        restTestClient.post()
            .uri("/private/v1/wallets/$walletId/crypto-holdings")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                """{"ticker":"$ticker","name":"Series $ticker","currentPrice":$price,
                   "lot":{"lotDate":"$lotDate","quantity":$quantity,"price":$price}}"""
            )
            .exchange()
            .expectStatus().isCreated()
    }

    private fun createFundHolding(walletId: UUID, name: String, contributionDate: String, amount: String) {
        restTestClient.post()
            .uri("/private/v1/wallets/$walletId/fund-holdings")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                """{"fundTypeId":"$fundTypeId","name":"$name","currentValue":$amount,
                   "contribution":{"contributionDate":"$contributionDate","amount":$amount}}"""
            )
            .exchange()
            .expectStatus().isCreated()
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
        restTestClient.patch()
            .uri("/private/v1/profile")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"preferredCurrency":"USD"}""")
            .exchange()
            .expectStatus().isOk()

        assertEquals(
            """[{"month":"2025-01","totalInvested":264.2857142857142857130000},{"month":"2025-02","totalInvested":352.46857142857142856837920},{"month":"2025-03","totalInvested":773.54857142857142856837920}]""",
            seriesBody(),
        )
    }
}
