package br.com.investlog.server.wallets.rest.controllers

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.typelists.rest.payloads.TypeResponse
import br.com.investlog.server.wallets.rest.payloads.WalletKind
import br.com.investlog.server.wallets.rest.payloads.WalletResponse
import org.junit.jupiter.api.Order
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.returnResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class WalletControllerTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTestClient: RestTestClient

    private fun createWallet(
        name: String = "My Stocks",
        kind: WalletKind = WalletKind.STOCKS,
        currency: String = "BRL"
    ): WalletResponse = restTestClient.post()
        .uri("/private/v1/wallets")
        .contentType(MediaType.APPLICATION_JSON)
        .body("""{"name":"$name","kind":"$kind","currency":"$currency"}""")
        .exchange()
        .expectStatus().isCreated()
        .returnResult<WalletResponse>()
        .responseBody!!

    @Test
    @Order(1)
    fun `creates a wallet and returns 201`() {

        val wallet = createWallet()

        assertNotNull(wallet.id)
        assertEquals("My Stocks", wallet.name)
        assertEquals("STOCKS", wallet.kind.text)
        assertEquals("BRL", wallet.currency)
        assertEquals(0, wallet.holdingCount)
    }

    @Test
    @Order(2)
    fun `lists wallets`() {
        restTestClient.get()
            .uri("/private/v1/wallets")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.page.totalElements").isEqualTo(1)
            .jsonPath("$.content[0].name").isEqualTo("My Stocks")
    }

    @Test
    @Order(3)
    fun `fetches a wallet by id`() {

        val wallet = createWallet("Crypto Bag", WalletKind.CRYPTO, "USD")

        restTestClient.get()
            .uri("/private/v1/wallets/${wallet.id}")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.id").isEqualTo(wallet.id.toString())
            .jsonPath("$.name").isEqualTo("Crypto Bag")
    }

    @Test
    @Order(4)
    fun `renames a wallet`() {

        val wallet = createWallet(name = "Old Name", currency = "EUR")

        restTestClient.patch()
            .uri("/private/v1/wallets/${wallet.id}")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"New Name"}""")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.name").isEqualTo("New Name")
    }

    @Test
    @Order(5)
    fun `deletes a wallet`() {

        val wallet = createWallet("To Delete")

        restTestClient.delete()
            .uri("/private/v1/wallets/${wallet.id}")
            .exchange()
            .expectStatus().isNoContent()

        restTestClient.get()
            .uri("/private/v1/wallets/${wallet.id}")
            .exchange()
            .expectStatus().isNotFound()
    }

    @Test
    @Order(6)
    fun `returns 400 when kind is missing`() {
        restTestClient.post()
            .uri("/private/v1/wallets")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"X","currency":"BRL"}""")
            .exchange()
            .expectStatus().isBadRequest()
    }

    @Test
    @Order(7)
    fun `wallet response includes currentValue and gain computed from its holdings`() {
        val wallet = createWallet("Wallet With Holdings", WalletKind.STOCKS, "BRL")

        val stockTypeId = restTestClient.post()
            .uri("/private/v1/stock-types")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Wallet Gain Test Type"}""")
            .exchange()
            .returnResult<TypeResponse>()
            .responseBody!!
            .id

        restTestClient.post()
            .uri("/private/v1/wallets/${wallet.id}/stock-holdings")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                """{"stockTypeId":"$stockTypeId","ticker":"WGAIN3",
                   "currentPrice":60.00,
                   "lot":{"lotDate":"2025-01-01","quantity":10,"price":50.00}}"""
            )
            .exchange()
            .expectStatus().isCreated()

        restTestClient.get()
            .uri("/private/v1/wallets/${wallet.id}")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.totalInvested").isEqualTo(500.0)
            .jsonPath("$.currentValue").isEqualTo(600.0)
            .jsonPath("$.gain").isEqualTo(100.0)
    }
}
