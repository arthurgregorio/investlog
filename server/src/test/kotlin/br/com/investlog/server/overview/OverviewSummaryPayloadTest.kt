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
class OverviewSummaryPayloadTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTestClient: RestTestClient

    lateinit var stockTypeId: UUID

    @BeforeAll
    fun setup() {
        restTestClient.put()
            .uri("/private/v1/currency-rates/USD")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"rate":7.00}""")
            .exchange()
            .expectStatus().isOk()

        stockTypeId = restTestClient.post()
            .uri("/private/v1/stock-types")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Summary Payload Type"}""")
            .exchange()
            .returnResult<TypeResponse>()
            .responseBody!!
            .id

        val brlWalletId = createWallet("Summary BRL", "BRL")
        val usdWalletId = createWallet("Summary USD", "USD")

        createStockHolding(brlWalletId, "SMBRL3", currentPrice = "50.00", quantity = "10", price = "45.00")
        createStockHolding(usdWalletId, "SMUSD3", currentPrice = "110.00", quantity = "2", price = "100.00")
        createStockHolding(usdWalletId, "SMNPR3", currentPrice = null, quantity = "3", price = "33.33")
    }

    private fun createWallet(name: String, currency: String): UUID =
        restTestClient.post()
            .uri("/private/v1/wallets")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"$name","kind":"STOCKS","currency":"$currency"}""")
            .exchange()
            .returnResult<WalletResponse>()
            .responseBody!!
            .id

    private fun createStockHolding(walletId: UUID, ticker: String, currentPrice: String?, quantity: String, price: String) {
        val currentPriceField = currentPrice?.let { """"currentPrice":$it,""" } ?: ""
        restTestClient.post()
            .uri("/private/v1/wallets/$walletId/stock-holdings")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                """{"stockTypeId":"$stockTypeId","ticker":"$ticker",$currentPriceField
                   "lot":{"lotDate":"2025-01-15","quantity":$quantity,"price":$price}}"""
            )
            .exchange()
            .expectStatus().isCreated()
    }

    private fun summaryBody(): String =
        restTestClient.get()
            .uri("/private/v1/overview")
            .exchange()
            .expectStatus().isOk()
            .returnResult<String>()
            .responseBody!!

    @Test
    @Order(1)
    fun `the summary in the anchor currency converts every wallet at its stored rate`() {
        assertEquals(
            """{"displayCurrency":"BRL","totalCostBasis":2549.9300000000000000000000,"totalCurrentValue":2040.0000000000000000000000,"totalGain":-509.9300000000000000000000,"totalGainPct":-19.9978038600,"kindSummaries":[{"kind":"STOCKS","holdingCount":3,"totalCostBasis":2549.9300000000000000000000,"totalCurrentValue":2040.0000000000000000000000,"totalGain":-509.9300000000000000000000,"totalGainPct":-19.9978038600}]}""",
            summaryBody(),
        )
    }

    @Test
    @Order(2)
    fun `the summary in another display currency divides by that currency's rate`() {
        restTestClient.patch()
            .uri("/private/v1/profile")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"preferredCurrency":"USD"}""")
            .exchange()
            .expectStatus().isOk()

        assertEquals(
            """{"displayCurrency":"USD","totalCostBasis":364.2757142857142857130000,"totalCurrentValue":291.4285714285714285700000,"totalGain":-72.8471428571428571430000,"totalGainPct":-19.9978038600,"kindSummaries":[{"kind":"STOCKS","holdingCount":3,"totalCostBasis":364.2757142857142857130000,"totalCurrentValue":291.4285714285714285700000,"totalGain":-72.8471428571428571430000,"totalGainPct":-19.9978038600}]}""",
            summaryBody(),
        )
    }
}
