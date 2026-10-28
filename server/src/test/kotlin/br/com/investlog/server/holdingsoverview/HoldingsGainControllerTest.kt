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
    lateinit var sortWalletId: UUID
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
            .uri("/private/v1/holding-types")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Gain Test Type","kinds":["STOCKS"]}""")
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
            .uri("/private/v1/holding-types")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Gain Test Fund Type","kinds":["FUNDS"]}""")
            .exchange()
            .returnResult<TypeResponse>()
            .responseBody!!
            .id

        restTestClient.post()
            .uri("/private/v1/wallets/$fundsWalletId/fund-holdings")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                """{"typeId":"$fundTypeId","name":"GNFND",
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

        sortWalletId = restTestClient.post()
            .uri("/private/v1/wallets")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Gain Sort Wallet","kind":"STOCKS","currency":"BRL"}""")
            .exchange()
            .returnResult<WalletResponse>()
            .responseBody!!
            .id

        createStockHolding("SRTSML3", currentPrice = "20.00", quantity = "1", price = "10.00", inWalletId = sortWalletId)
        createStockHolding("SRTLRG3", currentPrice = "15.00", quantity = "100", price = "10.00", inWalletId = sortWalletId)
        createStockHolding("SRTNEG3", currentPrice = "8.00", quantity = "10", price = "10.00", inWalletId = sortWalletId)
        createStockHolding("SRTNUL3", currentPrice = null, quantity = "10", price = "10.00", inWalletId = sortWalletId)

        createStockHolding("SRTQA3", currentPrice = "10.00", quantity = "1", price = "10.00")
        createStockHolding("SRTQC3", currentPrice = "10.00", quantity = "1", price = "10.00")
        createFundHolding("srtqb fund")
        createFundHolding("SRTQD Fund")
    }

    private fun createFundHolding(name: String) {
        restTestClient.post()
            .uri("/private/v1/wallets/$fundsWalletId/fund-holdings")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                """{"typeId":"$fundTypeId","name":"$name",
                   "currentValue":100.00,
                   "contribution":{"contributionDate":"2025-01-15","amount":100.00}}"""
            )
            .exchange()
            .expectStatus().isCreated()
    }

    private fun createStockHolding(
        ticker: String,
        currentPrice: String?,
        quantity: String,
        price: String,
        inWalletId: UUID = walletId,
    ) {
        val currentPriceField = currentPrice?.let { """"currentPrice":$it,""" } ?: ""
        restTestClient.post()
            .uri("/private/v1/wallets/$inWalletId/stock-holdings")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                """{"typeId":"$stockTypeId","ticker":"$ticker",$currentPriceField
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

    @Test
    @Order(12)
    fun `GET holdings sorted by gainPct descending orders by percentage with nulls last`() {
        restTestClient.get()
            .uri("/private/v1/holdings?walletId={walletId}&sort=gainPct,desc", sortWalletId)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content[*].ticker").isEqualTo(listOf("SRTSML3", "SRTLRG3", "SRTNEG3", "SRTNUL3"))
    }

    @Test
    @Order(13)
    fun `GET holdings sorted by gainPct ascending orders by percentage with nulls last`() {
        restTestClient.get()
            .uri("/private/v1/holdings?walletId={walletId}&sort=gainPct,asc", sortWalletId)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content[*].ticker").isEqualTo(listOf("SRTNEG3", "SRTLRG3", "SRTSML3", "SRTNUL3"))
    }

    @Test
    @Order(14)
    fun `GET holdings sorted by gain descending still orders by absolute gain`() {
        restTestClient.get()
            .uri("/private/v1/holdings?walletId={walletId}&sort=gain,desc", sortWalletId)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content[*].ticker").isEqualTo(listOf("SRTLRG3", "SRTSML3", "SRTNEG3", "SRTNUL3"))
    }

    @Test
    @Order(15)
    fun `GET holdings sorted by asset ascending interleaves tickers and fund names ignoring case`() {
        restTestClient.get()
            .uri("/private/v1/holdings?search=srtq&sort=asset,asc")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content.length()").isEqualTo(4)
            .jsonPath("$.content[0].ticker").isEqualTo("SRTQA3")
            .jsonPath("$.content[1].name").isEqualTo("srtqb fund")
            .jsonPath("$.content[2].ticker").isEqualTo("SRTQC3")
            .jsonPath("$.content[3].name").isEqualTo("SRTQD Fund")
    }

    @Test
    @Order(16)
    fun `GET holdings sorted by asset descending reverses the alphabetical order`() {
        restTestClient.get()
            .uri("/private/v1/holdings?search=srtq&sort=asset,desc")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content.length()").isEqualTo(4)
            .jsonPath("$.content[0].name").isEqualTo("SRTQD Fund")
            .jsonPath("$.content[1].ticker").isEqualTo("SRTQC3")
            .jsonPath("$.content[2].name").isEqualTo("srtqb fund")
            .jsonPath("$.content[3].ticker").isEqualTo("SRTQA3")
    }
}
