package br.com.investlog.server.stockholdings.rest.controllers

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.stockholdings.rest.payloads.LotResponse
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
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StockHoldingControllerTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTestClient: RestTestClient

    lateinit var walletId: UUID
    lateinit var stockTypeId: UUID

    @BeforeAll
    fun setup() {
        walletId = restTestClient.post()
            .uri("/private/v1/wallets")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Stocks Wallet","kind":"stocks","currency":"BRL"}""")
            .exchange()
            .returnResult<WalletResponse>()
            .responseBody!!
            .id

        stockTypeId = restTestClient.post()
            .uri("/private/v1/stock-types")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Ação ON"}""")
            .exchange()
            .returnResult<TypeResponse>()
            .responseBody!!
            .id
    }

    private fun createHolding(ticker: String = "PETR4"): StockHoldingResponse =
        restTestClient.post()
            .uri("/private/v1/wallets/$walletId/stock-holdings")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""
                {
                  "stockTypeId":"$stockTypeId",
                  "ticker":"$ticker",
                  "name":"Petrobras",
                  "currentPrice":38.50,
                  "lot":{"lotDate":"2024-01-15","quantity":100,"price":35.00}
                }
            """.trimIndent())
            .exchange()
            .expectStatus().isCreated()
            .returnResult<StockHoldingResponse>()
            .responseBody!!

    @Test
    @Order(1)
    fun `creates a stock holding with initial lot`() {
        val h = createHolding("PETR4")
        assertNotNull(h.id)
        assertEquals("PETR4", h.ticker)
        assertEquals(1, h.lots.size)
        assertEquals("2024-01-15", h.lots[0].lotDate.toString())
    }

    @Test
    @Order(2)
    fun `lists stock holdings for the wallet`() {
        restTestClient.get()
            .uri("/private/v1/wallets/$walletId/stock-holdings")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.page.totalElements").isEqualTo(1)
            .jsonPath("$.content[0].ticker").isEqualTo("PETR4")
            .jsonPath("$.content[0].lots").isArray()
    }

    @Test
    @Order(3)
    fun `adds a lot to an existing holding`() {
        val h = createHolding("VALE3")
        restTestClient.post()
            .uri("/private/v1/wallets/$walletId/stock-holdings/${h.id}/lots")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"lotDate":"2024-03-10","quantity":50,"price":70.00}""")
            .exchange()
            .expectStatus().isCreated()
            .expectBody()
            .jsonPath("$.lotDate").isEqualTo("2024-03-10")
            .jsonPath("$.quantity").isEqualTo(50)
    }

    @Test
    @Order(4)
    fun `updates ticker and current price`() {
        val h = createHolding("BBAS3")
        restTestClient.patch()
            .uri("/private/v1/wallets/$walletId/stock-holdings/${h.id}")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"ticker":"BBAS3","currentPrice":25.00}""")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.currentPrice").isEqualTo(25.00)
    }

    @Test
    @Order(5)
    fun `deletes a holding`() {
        val h = createHolding("ITUB4")
        restTestClient.delete()
            .uri("/private/v1/wallets/$walletId/stock-holdings/${h.id}")
            .exchange()
            .expectStatus().isNoContent()

        restTestClient.get()
            .uri("/private/v1/wallets/$walletId/stock-holdings")
            .exchange()
            .expectBody()
            .jsonPath("$.content[?(@.ticker == 'ITUB4')]").isEmpty()
    }

    @Test
    @Order(6)
    fun `deletes a lot`() {
        val h = createHolding("MGLU3")
        val lot = restTestClient.post()
            .uri("/private/v1/wallets/$walletId/stock-holdings/${h.id}/lots")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"lotDate":"2024-05-01","quantity":10,"price":12.00}""")
            .exchange()
            .returnResult<LotResponse>()
            .responseBody!!

        restTestClient.delete()
            .uri("/private/v1/wallets/$walletId/stock-holdings/${h.id}/lots/${lot.id}")
            .exchange()
            .expectStatus().isNoContent()
    }

    @Test
    @Order(7)
    fun `returns 404 for unknown wallet`() {
        restTestClient.get()
            .uri("/private/v1/wallets/${UUID.randomUUID()}/stock-holdings")
            .exchange()
            .expectStatus().isNotFound()
    }
}
