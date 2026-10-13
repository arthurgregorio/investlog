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
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

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

    @Test
    @Order(21)
    fun `creates a holding without a segment by default`() {
        val holding = createHolding("SEGM3")

        assertNull(holding.stockSegmentId)
        assertNull(holding.stockSegmentName)
    }

    @Test
    @Order(22)
    fun `creates a holding with a segment`() {
        val segment = createSegment("Energia")

        val holding = restTestClient.post()
            .uri("/private/v1/wallets/$walletId/stock-holdings")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                """
                {
                  "stockTypeId":"$stockTypeId",
                  "stockSegmentId":"${segment.id}",
                  "ticker":"TAEE11",
                  "lot":{"lotDate":"2024-01-15","quantity":10,"price":35.00}
                }
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isCreated()
            .returnResult<StockHoldingResponse>()
            .responseBody!!

        assertEquals(segment.id, holding.stockSegmentId)
        assertEquals("Energia", holding.stockSegmentName)
    }

    @Test
    @Order(23)
    fun `returns 404 with the segment id when creating a holding with an unknown segment`() {
        val unknownSegmentId = UUID.randomUUID()

        restTestClient.post()
            .uri("/private/v1/wallets/$walletId/stock-holdings")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                """
                {
                  "stockTypeId":"$stockTypeId",
                  "stockSegmentId":"$unknownSegmentId",
                  "ticker":"PETR4",
                  "lot":{"lotDate":"2024-01-15","quantity":100,"price":35.00}
                }
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isNotFound()
            .expectBody()
            .jsonPath("$.detail").isEqualTo("Segmento não encontrado: $unknownSegmentId")
    }

    @Test
    @Order(24)
    fun `sets, changes and clears the segment of a holding`() {
        val holding = createHolding("WEGE3")
        val industry = createSegment("Indústria")
        val technology = createSegment("Tecnologia")

        val withIndustry = updateSegment(holding.id, "\"${industry.id}\"")
        assertEquals(industry.id, withIndustry.stockSegmentId)
        assertEquals("Indústria", withIndustry.stockSegmentName)

        val withTechnology = updateSegment(holding.id, "\"${technology.id}\"")
        assertEquals(technology.id, withTechnology.stockSegmentId)
        assertEquals("Tecnologia", withTechnology.stockSegmentName)

        val cleared = updateSegment(holding.id, "null")
        assertNull(cleared.stockSegmentId)
        assertNull(cleared.stockSegmentName)

        val fetched = restTestClient.get()
            .uri("/private/v1/wallets/$walletId/stock-holdings/${holding.id}")
            .exchange()
            .expectStatus().isOk()
            .returnResult<StockHoldingResponse>()
            .responseBody!!
        assertNull(fetched.stockSegmentId)
    }

    @Test
    @Order(25)
    fun `returns 404 with the segment id when setting an unknown segment`() {
        val holding = createHolding("ITSA4")
        val unknownSegmentId = UUID.randomUUID()

        restTestClient.put()
            .uri("/private/v1/wallets/$walletId/stock-holdings/${holding.id}/segment")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"stockSegmentId":"$unknownSegmentId"}""")
            .exchange()
            .expectStatus().isNotFound()
            .expectBody()
            .jsonPath("$.detail").isEqualTo("Segmento não encontrado: $unknownSegmentId")
    }

    @Test
    @Order(26)
    fun `returns 404 with the holding id when setting the segment of an unknown holding`() {
        val unknownHoldingId = UUID.randomUUID()

        restTestClient.put()
            .uri("/private/v1/wallets/$walletId/stock-holdings/$unknownHoldingId/segment")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"stockSegmentId":null}""")
            .exchange()
            .expectStatus().isNotFound()
            .expectBody()
            .jsonPath("$.detail").isEqualTo("Posição de ação não encontrada: $unknownHoldingId")
    }

    @Test
    @Order(27)
    fun `updates a lot's quantity and price and the holding's average price follows`() {
        val holding = createHolding("EDTA3")
        val firstLot = holding.lots.single()
        addLot(holding.id, """{"lotDate":"2024-02-01","quantity":100,"price":50.00}""")

        patchLot(holding.id, firstLot.id, """{"lotDate":"2024-01-20","quantity":300,"price":30.00}""")
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.lotDate").isEqualTo("2024-01-20")
            .jsonPath("$.quantity").isEqualTo(300)
            .jsonPath("$.price").isEqualTo(30.0)

        restTestClient.get()
            .uri("/private/v1/holdings?walletId=$walletId&search=EDTA3")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content.length()").isEqualTo(1)
            .jsonPath("$.content[0].quantity").isEqualTo(400.0)
            .jsonPath("$.content[0].costBasis").isEqualTo(14000.0)
    }

    @Test
    @Order(28)
    fun `a date-only body changes only the lot's date`() {
        val holding = createHolding("EDTB3")
        val lot = holding.lots.single()

        patchLot(holding.id, lot.id, """{"lotDate":"2024-01-25"}""")
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.lotDate").isEqualTo("2024-01-25")
            .jsonPath("$.quantity").isEqualTo(100)
            .jsonPath("$.price").isEqualTo(35.0)
    }

    @Test
    @Order(29)
    fun `null quantity or price keeps the stored value`() {
        val holding = createHolding("EDTC3")
        val lot = holding.lots.single()

        patchLot(holding.id, lot.id, """{"lotDate":"2024-01-15","quantity":150,"price":null}""")
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.quantity").isEqualTo(150)
            .jsonPath("$.price").isEqualTo(35.0)

        patchLot(holding.id, lot.id, """{"lotDate":"2024-01-15","price":20.00}""")
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.quantity").isEqualTo(150)
            .jsonPath("$.price").isEqualTo(20.0)
    }

    @Test
    @Order(30)
    fun `rejects a zero quantity or a negative price`() {
        val holding = createHolding("EDTD3")
        val lot = holding.lots.single()

        patchLot(holding.id, lot.id, """{"lotDate":"2024-01-15","quantity":0}""")
            .expectStatus().isBadRequest()

        patchLot(holding.id, lot.id, """{"lotDate":"2024-01-15","price":-1}""")
            .expectStatus().isBadRequest()

        val storedLot = findHolding(holding.id).lots.single()
        assertEquals(0, storedLot.quantity.compareTo(BigDecimal("100")))
        assertEquals(0, storedLot.price.compareTo(BigDecimal("35")))
    }

    @Test
    @Order(31)
    fun `rejects lowering a lot below the quantity already sold`() {
        val holding = createHolding("EDTE3")
        val lot = holding.lots.single()
        withdraw(holding.id, """{"resultDate":"2024-06-01","quantity":60,"unitPrice":40.00}""")

        patchLot(holding.id, lot.id, """{"lotDate":"2024-01-15","quantity":50}""")
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.detail")
            .isEqualTo("A quantidade das compras não pode ficar abaixo da quantidade já vendida ou resgatada")

        assertEquals(0, findHolding(holding.id).lots.single().quantity.compareTo(BigDecimal("100")))

        patchLot(holding.id, lot.id, """{"lotDate":"2024-01-15","quantity":70}""")
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.quantity").isEqualTo(70)
    }

    @Test
    @Order(32)
    fun `edits a lot of a frozen holding`() {
        val holding = createHolding("EDTF3")
        val lot = holding.lots.single()
        restTestClient.patch()
            .uri("/private/v1/wallets/$walletId/stock-holdings/${holding.id}")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"frozen":true}""")
            .exchange()
            .expectStatus().isOk()

        patchLot(holding.id, lot.id, """{"lotDate":"2024-01-15","quantity":120,"price":33.00}""")
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.quantity").isEqualTo(120)
            .jsonPath("$.price").isEqualTo(33.0)
    }

    @Test
    @Order(33)
    fun `returns 404 with the lot id when editing the quantity of an unknown lot`() {
        val holding = createHolding("EDTG3")
        val unknownLotId = UUID.randomUUID()

        patchLot(holding.id, unknownLotId, """{"lotDate":"2024-01-15","quantity":1,"price":1.00}""")
            .expectStatus().isNotFound()
            .expectBody()
            .jsonPath("$.detail").isEqualTo("Lote não encontrado: $unknownLotId")
    }

    @Test
    @Order(34)
    fun `editing a lot keeps the cost basis and profit of an existing sale`() {
        val holding = createHolding("EDTH3")
        val lot = holding.lots.single()
        withdraw(holding.id, """{"resultDate":"2024-06-01","quantity":40,"unitPrice":50.00}""")
        val saleBefore = findHolding(holding.id).withdrawals.single()

        patchLot(holding.id, lot.id, """{"lotDate":"2024-01-15","quantity":120,"price":40.00}""")
            .expectStatus().isOk()

        val saleAfter = findHolding(holding.id).withdrawals.single()
        assertEquals(saleBefore.costBasis, saleAfter.costBasis)
        assertEquals(saleBefore.profit, saleAfter.profit)

        restTestClient.get()
            .uri("/private/v1/holdings?walletId=$walletId&search=EDTH3")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content[0].quantity").isEqualTo(80.0)
            .jsonPath("$.content[0].costBasis").isEqualTo(3400.0)
    }

    private fun addLot(holdingId: UUID, body: String): LotResponse =
        restTestClient.post()
            .uri("/private/v1/wallets/$walletId/stock-holdings/$holdingId/lots")
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .exchange()
            .expectStatus().isCreated()
            .returnResult<LotResponse>()
            .responseBody!!

    private fun patchLot(holdingId: UUID, lotId: UUID, body: String) =
        restTestClient.patch()
            .uri("/private/v1/wallets/$walletId/stock-holdings/$holdingId/lots/$lotId")
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .exchange()

    private fun withdraw(holdingId: UUID, body: String) {
        restTestClient.post()
            .uri("/private/v1/wallets/$walletId/stock-holdings/$holdingId/withdrawals")
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .exchange()
            .expectStatus().isCreated()
    }

    private fun findHolding(holdingId: UUID): StockHoldingResponse =
        restTestClient.get()
            .uri("/private/v1/wallets/$walletId/stock-holdings/$holdingId")
            .exchange()
            .expectStatus().isOk()
            .returnResult<StockHoldingResponse>()
            .responseBody!!

    private fun createSegment(name: String): TypeResponse =
        restTestClient.post()
            .uri("/private/v1/stock-segments")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"$name"}""")
            .exchange()
            .expectStatus().isCreated()
            .returnResult<TypeResponse>()
            .responseBody!!

    private fun updateSegment(holdingId: UUID, stockSegmentIdJson: String): StockHoldingResponse =
        restTestClient.put()
            .uri("/private/v1/wallets/$walletId/stock-holdings/$holdingId/segment")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"stockSegmentId":$stockSegmentIdJson}""")
            .exchange()
            .expectStatus().isOk()
            .returnResult<StockHoldingResponse>()
            .responseBody!!
}
