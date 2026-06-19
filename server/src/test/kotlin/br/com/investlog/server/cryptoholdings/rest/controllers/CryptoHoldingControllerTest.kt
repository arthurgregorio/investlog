package br.com.investlog.server.cryptoholdings.rest.controllers

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.cryptoholdings.rest.payloads.CryptoHoldingResponse
import br.com.investlog.server.stockholdings.rest.payloads.LotResponse
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
import kotlin.test.assertNotNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CryptoHoldingControllerTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTestClient: RestTestClient

    lateinit var walletId: UUID

    @BeforeAll
    fun setup() {
        walletId = restTestClient.post()
            .uri("/private/v1/wallets")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Crypto Wallet","kind":"CRYPTO","currency":"USD"}""")
            .exchange()
            .returnResult<WalletResponse>()
            .responseBody!!
            .id
    }

    private fun createHolding(ticker: String = "BTC"): CryptoHoldingResponse =
        restTestClient.post()
            .uri("/private/v1/wallets/$walletId/crypto-holdings")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""
                {
                  "ticker":"$ticker",
                  "name":"Bitcoin",
                  "currentPrice":95000.00,
                  "lot":{"lotDate":"2024-01-01","quantity":0.5,"price":42000.00}
                }
            """.trimIndent())
            .exchange()
            .expectStatus().isCreated()
            .returnResult<CryptoHoldingResponse>()
            .responseBody!!

    @Test
    @Order(1)
    fun `creates a crypto holding with initial lot`() {
        val h = createHolding("BTC")
        assertNotNull(h.id)
        assertEquals("BTC", h.ticker)
        assertEquals(1, h.lots.size)
    }

    @Test
    @Order(2)
    fun `lists crypto holdings`() {
        restTestClient.get()
            .uri("/private/v1/wallets/$walletId/crypto-holdings")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.page.totalElements").isEqualTo(1)
            .jsonPath("$.content[0].ticker").isEqualTo("BTC")
    }

    @Test
    @Order(3)
    fun `adds a lot`() {
        val h = createHolding("ETH")
        restTestClient.post()
            .uri("/private/v1/wallets/$walletId/crypto-holdings/${h.id}/lots")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"lotDate":"2024-06-01","quantity":2.0,"price":3500.00}""")
            .exchange()
            .expectStatus().isCreated()
            .expectBody()
            .jsonPath("$.quantity").isEqualTo(2.0)
    }

    @Test
    @Order(4)
    fun `updates current price`() {
        val h = createHolding("SOL")
        restTestClient.patch()
            .uri("/private/v1/wallets/$walletId/crypto-holdings/${h.id}")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"currentPrice":180.00}""")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.currentPrice").isEqualTo(180.00)
    }

    @Test
    @Order(5)
    fun `deletes a holding`() {
        val h = createHolding("ADA")
        restTestClient.delete()
            .uri("/private/v1/wallets/$walletId/crypto-holdings/${h.id}")
            .exchange()
            .expectStatus().isNoContent()
    }

    @Test
    @Order(6)
    fun `deletes a lot`() {
        val h = createHolding("XRP")
        val lot = restTestClient.post()
            .uri("/private/v1/wallets/$walletId/crypto-holdings/${h.id}/lots")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"lotDate":"2024-08-01","quantity":100.0,"price":0.55}""")
            .exchange()
            .returnResult<LotResponse>()
            .responseBody!!

        restTestClient.delete()
            .uri("/private/v1/wallets/$walletId/crypto-holdings/${h.id}/lots/${lot.id}")
            .exchange()
            .expectStatus().isNoContent()
    }

    @Test
    @Order(7)
    fun `returns 404 for unknown wallet`() {
        restTestClient.get()
            .uri("/private/v1/wallets/${UUID.randomUUID()}/crypto-holdings")
            .exchange()
            .expectStatus().isNotFound()
    }
}
