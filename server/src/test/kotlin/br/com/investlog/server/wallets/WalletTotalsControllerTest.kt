package br.com.investlog.server.wallets

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
import kotlin.test.assertContains
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WalletTotalsControllerTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTestClient: RestTestClient

    lateinit var stockTypeId: UUID
    lateinit var emptyWalletId: UUID
    lateinit var pricedWalletId: UUID
    lateinit var unpricedWalletId: UUID
    lateinit var mixedWalletId: UUID
    lateinit var completedWalletId: UUID

    @BeforeAll
    fun setup() {
        stockTypeId = restTestClient.post()
            .uri("/private/v1/stock-types")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Totals Test Type"}""")
            .exchange()
            .returnResult<TypeResponse>()
            .responseBody!!
            .id

        emptyWalletId = createWallet("Totals Empty")
        pricedWalletId = createWallet("Totals Priced")
        unpricedWalletId = createWallet("Totals Unpriced")
        mixedWalletId = createWallet("Totals Mixed")
        completedWalletId = createWallet("Totals Completed")

        createStockHolding(pricedWalletId, "TTPRC3", currentPrice = "50.00")
        createStockHolding(unpricedWalletId, "TTNPR3", currentPrice = null)
        createStockHolding(mixedWalletId, "TTMXA3", currentPrice = "50.00")
        createStockHolding(mixedWalletId, "TTMXB3", currentPrice = null)

        val completedHoldingId = createStockHolding(completedWalletId, "TTCMP3", currentPrice = "50.00")
        restTestClient.post()
            .uri("/private/v1/wallets/$completedWalletId/stock-holdings/$completedHoldingId/withdrawals")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"resultDate":"2026-09-19","quantity":10,"unitPrice":50.00,"fees":0,"taxes":0}""")
            .exchange()
            .expectStatus().isCreated()
    }

    private fun createWallet(name: String): UUID =
        restTestClient.post()
            .uri("/private/v1/wallets")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"$name","kind":"STOCKS","currency":"BRL"}""")
            .exchange()
            .returnResult<WalletResponse>()
            .responseBody!!
            .id

    private fun createStockHolding(walletId: UUID, ticker: String, currentPrice: String?): UUID {
        val currentPriceField = currentPrice?.let { """"currentPrice":$it,""" } ?: ""
        return restTestClient.post()
            .uri("/private/v1/wallets/$walletId/stock-holdings")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                """{"stockTypeId":"$stockTypeId","ticker":"$ticker",$currentPriceField
                   "lot":{"lotDate":"2025-01-15","quantity":10,"price":45.00}}"""
            )
            .exchange()
            .expectStatus().isCreated()
            .returnResult<StockHoldingResponse>()
            .responseBody!!
            .id
    }

    private fun walletBody(walletId: UUID): String =
        restTestClient.get()
            .uri("/private/v1/wallets/{walletId}", walletId)
            .exchange()
            .expectStatus().isOk()
            .returnResult<String>()
            .responseBody!!

    @Test
    @Order(1)
    fun `an empty wallet reports no holdings, nothing invested and no value`() {
        val body = walletBody(emptyWalletId)

        assertContains(body, """"holdingCount":0""")
        assertContains(body, """"totalInvested":0,""")
        assertContains(body, """"currentValue":null""")
        assertContains(body, """"gain":null""")
        assertContains(body, """"gainPct":null""")
    }

    @Test
    @Order(2)
    fun `a priced wallet reports exact totals, gain and gainPct`() {
        val body = walletBody(pricedWalletId)

        assertContains(body, """"holdingCount":1""")
        assertContains(body, """"totalInvested":450.00""")
        assertContains(body, """"currentValue":500.00""")
        assertContains(body, """"gain":50.00""")
        assertContains(body, """"gainPct":11.1111111100""")
    }

    @Test
    @Order(3)
    fun `a wallet holding only unpriced holdings reports a null value and a null gain`() {
        val body = walletBody(unpricedWalletId)

        assertContains(body, """"holdingCount":1""")
        assertContains(body, """"totalInvested":450.00""")
        assertContains(body, """"currentValue":null""")
        assertContains(body, """"gain":null""")
        assertContains(body, """"gainPct":null""")
    }

    @Test
    @Order(4)
    fun `a wallet with priced and unpriced holdings sums the priced value against every cost`() {
        val body = walletBody(mixedWalletId)

        assertContains(body, """"holdingCount":2""")
        assertContains(body, """"totalInvested":900.00""")
        assertContains(body, """"currentValue":500.00""")
        assertContains(body, """"gain":-400.00""")
        assertContains(body, """"gainPct":-44.4444444400""")
    }

    @Test
    @Order(5)
    fun `a wallet whose holdings are all completed reports the shape of an empty wallet`() {
        val body = walletBody(completedWalletId)

        assertContains(body, """"holdingCount":0""")
        assertContains(body, """"totalInvested":0,""")
        assertContains(body, """"currentValue":null""")
        assertContains(body, """"gain":null""")
    }

    @Test
    @Order(6)
    fun `listing, creating and renaming a wallet return the same figures as fetching it`() {
        val listed = restTestClient.get()
            .uri("/private/v1/wallets?size=100")
            .exchange()
            .expectStatus().isOk()
            .returnResult<String>()
            .responseBody!!
        assertContains(listed, """"gainPct":11.1111111100""")
        assertContains(listed, """"gainPct":-44.4444444400""")

        val renamed = restTestClient.patch()
            .uri("/private/v1/wallets/{walletId}", pricedWalletId)
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Totals Priced Renamed"}""")
            .exchange()
            .expectStatus().isOk()
            .returnResult<String>()
            .responseBody!!
        assertContains(renamed, """"totalInvested":450.00""")
        assertContains(renamed, """"currentValue":500.00""")
        assertContains(renamed, """"gainPct":11.1111111100""")

        val created = restTestClient.post()
            .uri("/private/v1/wallets")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Totals Fresh","kind":"STOCKS","currency":"BRL"}""")
            .exchange()
            .expectStatus().isCreated()
            .returnResult<WalletResponse>()
            .responseBody!!
        assertEquals(0, created.holdingCount)
    }
}
