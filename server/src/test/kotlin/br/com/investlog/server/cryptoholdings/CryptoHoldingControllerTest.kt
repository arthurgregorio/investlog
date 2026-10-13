package br.com.investlog.server.cryptoholdings

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
import java.math.BigDecimal
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

    @Test
    @Order(8)
    fun `updates a lot's date`() {
        val h = createHolding("DOGE")
        val lot = restTestClient.post()
            .uri("/private/v1/wallets/$walletId/crypto-holdings/${h.id}/lots")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"lotDate":"2024-03-01","quantity":1000.0,"price":0.10}""")
            .exchange()
            .returnResult<LotResponse>()
            .responseBody!!

        restTestClient.patch()
            .uri("/private/v1/wallets/$walletId/crypto-holdings/${h.id}/lots/${lot.id}")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"lotDate":"2024-03-20"}""")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.lotDate").isEqualTo("2024-03-20")
    }

    @Test
    @Order(9)
    fun `returns 404 when updating an unknown lot's date`() {
        val h = createHolding("LTC")
        restTestClient.patch()
            .uri("/private/v1/wallets/$walletId/crypto-holdings/${h.id}/lots/${UUID.randomUUID()}")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"lotDate":"2024-03-20"}""")
            .exchange()
            .expectStatus().isNotFound()
    }

    @Test
    @Order(10)
    fun `rejects a ticker with symbols or spaces on create`() {
        restTestClient.post()
            .uri("/private/v1/wallets/$walletId/crypto-holdings")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                """
                {
                  "ticker":"BTC/USD",
                  "lot":{"lotDate":"2024-01-01","quantity":0.5,"price":42000.00}
                }
            """.trimIndent()
            )
            .exchange()
            .expectStatus().isBadRequest()
    }

    @Test
    @Order(11)
    fun `rejects a ticker with symbols or spaces on update`() {
        val h = createHolding("AVAX")
        restTestClient.patch()
            .uri("/private/v1/wallets/$walletId/crypto-holdings/${h.id}")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"ticker":"AV AX"}""")
            .exchange()
            .expectStatus().isBadRequest()
    }

    @Test
    @Order(12)
    fun `returns 404 with the holding id when fetching an unknown holding`() {
        val unknownHoldingId = UUID.randomUUID()

        restTestClient.get()
            .uri("/private/v1/wallets/$walletId/crypto-holdings/$unknownHoldingId")
            .exchange()
            .expectStatus().isNotFound()
            .expectBody()
            .jsonPath("$.detail").isEqualTo("Posição de criptomoeda não encontrada: $unknownHoldingId")
    }

    @Test
    @Order(13)
    fun `returns 404 with the holding id when updating an unknown holding`() {
        val unknownHoldingId = UUID.randomUUID()

        restTestClient.patch()
            .uri("/private/v1/wallets/$walletId/crypto-holdings/$unknownHoldingId")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"currentPrice":25.00}""")
            .exchange()
            .expectStatus().isNotFound()
            .expectBody()
            .jsonPath("$.detail").isEqualTo("Posição de criptomoeda não encontrada: $unknownHoldingId")
    }

    @Test
    @Order(14)
    fun `returns 404 with the holding id when deleting an unknown holding`() {
        val unknownHoldingId = UUID.randomUUID()

        restTestClient.delete()
            .uri("/private/v1/wallets/$walletId/crypto-holdings/$unknownHoldingId")
            .exchange()
            .expectStatus().isNotFound()
            .expectBody()
            .jsonPath("$.detail").isEqualTo("Posição de criptomoeda não encontrada: $unknownHoldingId")
    }

    @Test
    @Order(15)
    fun `returns 404 with the holding id when adding a lot to an unknown holding`() {
        val unknownHoldingId = UUID.randomUUID()

        restTestClient.post()
            .uri("/private/v1/wallets/$walletId/crypto-holdings/$unknownHoldingId/lots")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"lotDate":"2024-03-10","quantity":0.25,"price":70000.00}""")
            .exchange()
            .expectStatus().isNotFound()
            .expectBody()
            .jsonPath("$.detail").isEqualTo("Posição de criptomoeda não encontrada: $unknownHoldingId")
    }

    @Test
    @Order(16)
    fun `returns 404 with the holding id when deleting a lot of an unknown holding`() {
        val unknownHoldingId = UUID.randomUUID()

        restTestClient.delete()
            .uri("/private/v1/wallets/$walletId/crypto-holdings/$unknownHoldingId/lots/${UUID.randomUUID()}")
            .exchange()
            .expectStatus().isNotFound()
            .expectBody()
            .jsonPath("$.detail").isEqualTo("Posição de criptomoeda não encontrada: $unknownHoldingId")
    }

    @Test
    @Order(17)
    fun `returns 404 with the lot id when deleting an unknown lot`() {
        val holding = createHolding("SOL")
        val unknownLotId = UUID.randomUUID()

        restTestClient.delete()
            .uri("/private/v1/wallets/$walletId/crypto-holdings/${holding.id}/lots/$unknownLotId")
            .exchange()
            .expectStatus().isNotFound()
            .expectBody()
            .jsonPath("$.detail").isEqualTo("Lote não encontrado: $unknownLotId")
    }

    @Test
    @Order(18)
    fun `returns 404 with the holding id when updating the lot date of an unknown holding`() {
        val unknownHoldingId = UUID.randomUUID()

        restTestClient.patch()
            .uri("/private/v1/wallets/$walletId/crypto-holdings/$unknownHoldingId/lots/${UUID.randomUUID()}")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"lotDate":"2024-02-15"}""")
            .exchange()
            .expectStatus().isNotFound()
            .expectBody()
            .jsonPath("$.detail").isEqualTo("Posição de criptomoeda não encontrada: $unknownHoldingId")
    }

    @Test
    @Order(19)
    fun `updates a lot's quantity and price and the holding's average price follows`() {
        val holding = createHolding("EDCA")
        val firstLot = holding.lots.single()
        restTestClient.post()
            .uri("/private/v1/wallets/$walletId/crypto-holdings/${holding.id}/lots")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"lotDate":"2024-02-01","quantity":1.5,"price":40000.00}""")
            .exchange()
            .expectStatus().isCreated()

        patchLot(holding.id, firstLot.id, """{"lotDate":"2024-01-05","quantity":1.0,"price":30000.00}""")
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.lotDate").isEqualTo("2024-01-05")
            .jsonPath("$.quantity").isEqualTo(1.0)
            .jsonPath("$.price").isEqualTo(30000.0)

        restTestClient.get()
            .uri("/private/v1/holdings?walletId=$walletId&search=EDCA")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content.length()").isEqualTo(1)
            .jsonPath("$.content[0].quantity").isEqualTo(2.5)
            .jsonPath("$.content[0].costBasis").isEqualTo(90000.0)
    }

    @Test
    @Order(20)
    fun `a date-only body changes only the lot's date`() {
        val holding = createHolding("EDCB")
        val lot = holding.lots.single()

        patchLot(holding.id, lot.id, """{"lotDate":"2024-01-09"}""")
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.lotDate").isEqualTo("2024-01-09")
            .jsonPath("$.quantity").isEqualTo(0.5)
            .jsonPath("$.price").isEqualTo(42000.0)
    }

    @Test
    @Order(21)
    fun `null quantity or price keeps the stored value`() {
        val holding = createHolding("EDCC")
        val lot = holding.lots.single()

        patchLot(holding.id, lot.id, """{"lotDate":"2024-01-01","quantity":0.75,"price":null}""")
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.quantity").isEqualTo(0.75)
            .jsonPath("$.price").isEqualTo(42000.0)

        patchLot(holding.id, lot.id, """{"lotDate":"2024-01-01","price":41000.00}""")
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.quantity").isEqualTo(0.75)
            .jsonPath("$.price").isEqualTo(41000.0)
    }

    @Test
    @Order(22)
    fun `rejects a zero quantity or a negative price`() {
        val holding = createHolding("EDCD")
        val lot = holding.lots.single()

        patchLot(holding.id, lot.id, """{"lotDate":"2024-01-01","quantity":0}""")
            .expectStatus().isBadRequest()

        patchLot(holding.id, lot.id, """{"lotDate":"2024-01-01","price":-1}""")
            .expectStatus().isBadRequest()

        val storedLot = findHolding(holding.id).lots.single()
        assertEquals(0, storedLot.quantity.compareTo(BigDecimal("0.5")))
        assertEquals(0, storedLot.price.compareTo(BigDecimal("42000")))
    }

    @Test
    @Order(23)
    fun `rejects lowering a lot below the quantity already sold`() {
        val holding = createHolding("EDCE")
        val lot = holding.lots.single()
        withdraw(holding.id, """{"resultDate":"2024-06-01","quantity":0.3,"unitPrice":90000.00}""")

        patchLot(holding.id, lot.id, """{"lotDate":"2024-01-01","quantity":0.2}""")
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.detail")
            .isEqualTo("A quantidade das compras não pode ficar abaixo da quantidade já vendida ou resgatada")

        assertEquals(0, findHolding(holding.id).lots.single().quantity.compareTo(BigDecimal("0.5")))

        patchLot(holding.id, lot.id, """{"lotDate":"2024-01-01","quantity":0.4}""")
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.quantity").isEqualTo(0.4)
    }

    @Test
    @Order(24)
    fun `edits a lot of a frozen holding`() {
        val holding = createHolding("EDCF")
        val lot = holding.lots.single()
        restTestClient.patch()
            .uri("/private/v1/wallets/$walletId/crypto-holdings/${holding.id}")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"frozen":true}""")
            .exchange()
            .expectStatus().isOk()

        patchLot(holding.id, lot.id, """{"lotDate":"2024-01-01","quantity":0.6,"price":43000.00}""")
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.quantity").isEqualTo(0.6)
            .jsonPath("$.price").isEqualTo(43000.0)
    }

    @Test
    @Order(25)
    fun `returns 404 with the lot id when editing the quantity of an unknown lot`() {
        val holding = createHolding("EDCG")
        val unknownLotId = UUID.randomUUID()

        patchLot(holding.id, unknownLotId, """{"lotDate":"2024-01-01","quantity":1,"price":1.00}""")
            .expectStatus().isNotFound()
            .expectBody()
            .jsonPath("$.detail").isEqualTo("Lote não encontrado: $unknownLotId")
    }

    @Test
    @Order(26)
    fun `editing a lot keeps the cost basis and profit of an existing sale`() {
        val holding = createHolding("EDCH")
        val lot = holding.lots.single()
        withdraw(holding.id, """{"resultDate":"2024-06-01","quantity":0.2,"unitPrice":50000.00}""")
        val saleBefore = findHolding(holding.id).withdrawals.single()

        patchLot(holding.id, lot.id, """{"lotDate":"2024-01-01","quantity":1.0,"price":45000.00}""")
            .expectStatus().isOk()

        val saleAfter = findHolding(holding.id).withdrawals.single()
        assertEquals(saleBefore.costBasis, saleAfter.costBasis)
        assertEquals(saleBefore.profit, saleAfter.profit)

        restTestClient.get()
            .uri("/private/v1/holdings?walletId=$walletId&search=EDCH")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content[0].quantity").isEqualTo(0.8)
            .jsonPath("$.content[0].costBasis").isEqualTo(36600.0)
    }

    private fun patchLot(holdingId: UUID, lotId: UUID, body: String) =
        restTestClient.patch()
            .uri("/private/v1/wallets/$walletId/crypto-holdings/$holdingId/lots/$lotId")
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .exchange()

    private fun withdraw(holdingId: UUID, body: String) {
        restTestClient.post()
            .uri("/private/v1/wallets/$walletId/crypto-holdings/$holdingId/withdrawals")
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .exchange()
            .expectStatus().isCreated()
    }

    private fun findHolding(holdingId: UUID): CryptoHoldingResponse =
        restTestClient.get()
            .uri("/private/v1/wallets/$walletId/crypto-holdings/$holdingId")
            .exchange()
            .expectStatus().isOk()
            .returnResult<CryptoHoldingResponse>()
            .responseBody!!
}
