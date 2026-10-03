package br.com.investlog.server.stockholdings

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
            .body(
                """
                {
                  "stockTypeId":"$stockTypeId",
                  "ticker":"$ticker",
                  "name":"Petrobras",
                  "currentPrice":38.50,
                  "lot":{"lotDate":"2024-01-15","quantity":100,"price":35.00}
                }
            """.trimIndent()
            )
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

    @Test
    @Order(8)
    fun `updates a lot's date`() {
        val h = createHolding("RENT3")
        val lot = restTestClient.post()
            .uri("/private/v1/wallets/$walletId/stock-holdings/${h.id}/lots")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"lotDate":"2024-02-01","quantity":20,"price":18.00}""")
            .exchange()
            .returnResult<LotResponse>()
            .responseBody!!

        restTestClient.patch()
            .uri("/private/v1/wallets/$walletId/stock-holdings/${h.id}/lots/${lot.id}")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"lotDate":"2024-02-15"}""")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.lotDate").isEqualTo("2024-02-15")
    }

    @Test
    @Order(9)
    fun `returns 404 when updating an unknown lot's date`() {
        val h = createHolding("CSAN3")
        restTestClient.patch()
            .uri("/private/v1/wallets/$walletId/stock-holdings/${h.id}/lots/${UUID.randomUUID()}")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"lotDate":"2024-02-15"}""")
            .exchange()
            .expectStatus().isNotFound()
    }

    @Test
    @Order(10)
    fun `rejects a ticker with symbols or spaces on create`() {
        restTestClient.post()
            .uri("/private/v1/wallets/$walletId/stock-holdings")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                """
                {
                  "stockTypeId":"$stockTypeId",
                  "ticker":"PETR-4",
                  "lot":{"lotDate":"2024-01-15","quantity":100,"price":35.00}
                }
            """.trimIndent()
            )
            .exchange()
            .expectStatus().isBadRequest()
    }

    @Test
    @Order(11)
    fun `rejects a ticker with symbols or spaces on update`() {
        val h = createHolding("WEGE3")
        restTestClient.patch()
            .uri("/private/v1/wallets/$walletId/stock-holdings/${h.id}")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"ticker":"WEGE 3"}""")
            .exchange()
            .expectStatus().isBadRequest()
    }

    @Test
    @Order(12)
    fun `returns 404 with the holding id when fetching an unknown holding`() {
        val unknownHoldingId = UUID.randomUUID()

        restTestClient.get()
            .uri("/private/v1/wallets/$walletId/stock-holdings/$unknownHoldingId")
            .exchange()
            .expectStatus().isNotFound()
            .expectBody()
            .jsonPath("$.detail").isEqualTo("Posição de ação não encontrada: $unknownHoldingId")
    }

    @Test
    @Order(13)
    fun `returns 404 with the type id when creating a holding with an unknown stock type`() {
        val unknownTypeId = UUID.randomUUID()

        restTestClient.post()
            .uri("/private/v1/wallets/$walletId/stock-holdings")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                """
                {
                  "stockTypeId":"$unknownTypeId",
                  "ticker":"PETR4",
                  "lot":{"lotDate":"2024-01-15","quantity":100,"price":35.00}
                }
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isNotFound()
            .expectBody()
            .jsonPath("$.detail").isEqualTo("Tipo de ação não encontrado: $unknownTypeId")
    }

    @Test
    @Order(14)
    fun `returns 404 with the type id when updating a holding to an unknown stock type`() {
        val holding = createHolding("ABEV3")
        val unknownTypeId = UUID.randomUUID()

        restTestClient.patch()
            .uri("/private/v1/wallets/$walletId/stock-holdings/${holding.id}")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"stockTypeId":"$unknownTypeId"}""")
            .exchange()
            .expectStatus().isNotFound()
            .expectBody()
            .jsonPath("$.detail").isEqualTo("Tipo de ação não encontrado: $unknownTypeId")
    }

    @Test
    @Order(15)
    fun `returns 404 with the holding id when updating an unknown holding`() {
        val unknownHoldingId = UUID.randomUUID()

        restTestClient.patch()
            .uri("/private/v1/wallets/$walletId/stock-holdings/$unknownHoldingId")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"currentPrice":25.00}""")
            .exchange()
            .expectStatus().isNotFound()
            .expectBody()
            .jsonPath("$.detail").isEqualTo("Posição de ação não encontrada: $unknownHoldingId")
    }

    @Test
    @Order(16)
    fun `returns 404 with the holding id when deleting an unknown holding`() {
        val unknownHoldingId = UUID.randomUUID()

        restTestClient.delete()
            .uri("/private/v1/wallets/$walletId/stock-holdings/$unknownHoldingId")
            .exchange()
            .expectStatus().isNotFound()
            .expectBody()
            .jsonPath("$.detail").isEqualTo("Posição de ação não encontrada: $unknownHoldingId")
    }

    @Test
    @Order(17)
    fun `returns 404 with the holding id when adding a lot to an unknown holding`() {
        val unknownHoldingId = UUID.randomUUID()

        restTestClient.post()
            .uri("/private/v1/wallets/$walletId/stock-holdings/$unknownHoldingId/lots")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"lotDate":"2024-03-10","quantity":50,"price":70.00}""")
            .exchange()
            .expectStatus().isNotFound()
            .expectBody()
            .jsonPath("$.detail").isEqualTo("Posição de ação não encontrada: $unknownHoldingId")
    }

    @Test
    @Order(18)
    fun `returns 404 with the holding id when deleting a lot of an unknown holding`() {
        val unknownHoldingId = UUID.randomUUID()

        restTestClient.delete()
            .uri("/private/v1/wallets/$walletId/stock-holdings/$unknownHoldingId/lots/${UUID.randomUUID()}")
            .exchange()
            .expectStatus().isNotFound()
            .expectBody()
            .jsonPath("$.detail").isEqualTo("Posição de ação não encontrada: $unknownHoldingId")
    }

    @Test
    @Order(19)
    fun `returns 404 with the lot id when deleting an unknown lot`() {
        val holding = createHolding("EQTL3")
        val unknownLotId = UUID.randomUUID()

        restTestClient.delete()
            .uri("/private/v1/wallets/$walletId/stock-holdings/${holding.id}/lots/$unknownLotId")
            .exchange()
            .expectStatus().isNotFound()
            .expectBody()
            .jsonPath("$.detail").isEqualTo("Lote não encontrado: $unknownLotId")
    }

    @Test
    @Order(20)
    fun `returns 404 with the holding id when updating the lot date of an unknown holding`() {
        val unknownHoldingId = UUID.randomUUID()

        restTestClient.patch()
            .uri("/private/v1/wallets/$walletId/stock-holdings/$unknownHoldingId/lots/${UUID.randomUUID()}")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"lotDate":"2024-02-15"}""")
            .exchange()
            .expectStatus().isNotFound()
            .expectBody()
            .jsonPath("$.detail").isEqualTo("Posição de ação não encontrada: $unknownHoldingId")
    }
}
