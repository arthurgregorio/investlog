package br.com.investlog.server.holdingsoverview

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
import kotlin.test.assertContains

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HoldingsGainControllerTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTestClient: RestTestClient

    lateinit var walletId: UUID
    lateinit var fundsWalletId: UUID
    lateinit var stockTypeId: UUID
    lateinit var fundTypeId: UUID

    @BeforeAll
    fun setup() {
        walletId = restTestClient.post()
            .uri("/private/v1/wallets")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Gain Test Wallet","kind":"STOCKS","currency":"BRL"}""")
            .exchange()
            .returnResult<WalletResponse>()
            .responseBody!!
            .id

        stockTypeId = restTestClient.post()
            .uri("/private/v1/stock-types")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Gain Test Type"}""")
            .exchange()
            .returnResult<TypeResponse>()
            .responseBody!!
            .id

        fundsWalletId = restTestClient.post()
            .uri("/private/v1/wallets")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Gain Test Funds Wallet","kind":"FUNDS","currency":"BRL"}""")
            .exchange()
            .returnResult<WalletResponse>()
            .responseBody!!
            .id

        fundTypeId = restTestClient.post()
            .uri("/private/v1/fund-types")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Gain Test Fund Type"}""")
            .exchange()
            .returnResult<TypeResponse>()
            .responseBody!!
            .id

        restTestClient.post()
            .uri("/private/v1/wallets/$fundsWalletId/fund-holdings")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                """{"fundTypeId":"$fundTypeId","name":"GNFND",
                   "currentValue":1100.00,
                   "contribution":{"contributionDate":"2025-01-15","amount":1000.00}}"""
            )
            .exchange()
            .expectStatus().isCreated()

        createStockHolding("GNLSS3", currentPrice = "30.00", quantity = "10", price = "45.00")
        createStockHolding("GNZGN3", currentPrice = "45.00", quantity = "10", price = "45.00")
        createStockHolding("GNMRG3", currentPrice = "50.00", quantity = "10", price = "45.00")
        createStockHolding("GNMRG3", currentPrice = "50.00", quantity = "5", price = "80.00")
        createStockHolding("GNPRC3", currentPrice = "50.00", quantity = "10", price = "45.00")
        createStockHolding("GNZRO3", currentPrice = "50.00", quantity = "10", price = "0")
        createStockHolding("GNNPR3", currentPrice = null, quantity = "10", price = "45.00")
    }

    private fun createStockHolding(ticker: String, currentPrice: String?, quantity: String, price: String) {
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

    private fun holdingsBody(path: String, ticker: String, inWalletId: UUID = walletId): String =
        restTestClient.get()
            .uri("$path?walletId={walletId}&search={ticker}", inWalletId, ticker)
            .exchange()
            .expectStatus().isOk()
            .returnResult<String>()
            .responseBody!!

    @Test
    @Order(1)
    fun `GET holdings reports gain and gainPct as exact decimals`() {
        val body = holdingsBody("/private/v1/holdings", "GNPRC3")

        assertContains(body, """"gain":50.00""")
        assertContains(body, """"gainPct":11.1111111100""")
    }

    @Test
    @Order(2)
    fun `GET holdings reports a null gainPct when the cost basis is zero`() {
        val body = holdingsBody("/private/v1/holdings", "GNZRO3")

        assertContains(body, """"gain":500.00""")
        assertContains(body, """"gainPct":null""")
    }

    @Test
    @Order(3)
    fun `GET holdings reports a null gain and gainPct when there is no current price`() {
        val body = holdingsBody("/private/v1/holdings", "GNNPR3")

        assertContains(body, """"currentValue":null""")
        assertContains(body, """"gain":null""")
        assertContains(body, """"gainPct":null""")
    }

    @Test
    @Order(4)
    fun `GET holdings report reports gain and gainPct as exact decimals`() {
        val body = holdingsBody("/private/v1/holdings/report", "GNPRC3")

        assertContains(body, """"gain":50.00""")
        assertContains(body, """"gainPct":11.1111111100""")
    }

    @Test
    @Order(5)
    fun `GET holdings report reports a null gainPct when the cost basis is zero`() {
        val body = holdingsBody("/private/v1/holdings/report", "GNZRO3")

        assertContains(body, """"gain":500.00""")
        assertContains(body, """"gainPct":null""")
    }

    @Test
    @Order(6)
    fun `GET holdings report reports a null gain and gainPct when there is no current price`() {
        val body = holdingsBody("/private/v1/holdings/report", "GNNPR3")

        assertContains(body, """"gain":null""")
        assertContains(body, """"gainPct":null""")
    }

    @Test
    @Order(7)
    fun `GET holdings reports a negative gain with its percentage`() {
        val body = holdingsBody("/private/v1/holdings", "GNLSS3")

        assertContains(body, """"gain":-150.00""")
        assertContains(body, """"gainPct":-33.3333333300""")
    }

    @Test
    @Order(8)
    fun `GET holdings reports a zero gain with a zero gainPct`() {
        val body = holdingsBody("/private/v1/holdings", "GNZGN3")

        assertContains(body, """"gain":0.00""")
        assertContains(body, """"gainPct":0E-10""")
    }

    @Test
    @Order(9)
    fun `GET holdings reports a fund gain from its manual current value`() {
        val body = holdingsBody("/private/v1/holdings", "GNFND", fundsWalletId)

        assertContains(body, """"gain":100.00""")
        assertContains(body, """"gainPct":10.0000000000""")
    }

    @Test
    @Order(10)
    fun `GET holdings report derives gain and gainPct from the merged sums`() {
        val body = holdingsBody("/private/v1/holdings/report", "GNMRG3")

        assertContains(body, """"costBasis":850.00""")
        assertContains(body, """"gain":-100.00""")
        assertContains(body, """"gainPct":-11.7647058800""")
    }

    @Test
    @Order(11)
    fun `GET holdings report reports a fund gain from its manual current value`() {
        val body = holdingsBody("/private/v1/holdings/report", "GNFND", fundsWalletId)

        assertContains(body, """"gain":100.00""")
        assertContains(body, """"gainPct":10.0000000000""")
    }
}
