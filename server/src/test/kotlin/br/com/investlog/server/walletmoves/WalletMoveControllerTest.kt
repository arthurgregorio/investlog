package br.com.investlog.server.walletmoves

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.fundholdings.rest.payloads.FundHoldingResponse
import br.com.investlog.server.jooq.finances.enums.HoldingStatus
import br.com.investlog.server.jooq.finances.tables.references.HOLDINGS_OVERVIEW
import br.com.investlog.server.jooq.finances.tables.references.RESULTS
import br.com.investlog.server.jooq.finances.tables.references.STOCK_HOLDINGS
import br.com.investlog.server.jooq.finances.tables.references.STOCK_LOTS
import br.com.investlog.server.jooq.finances.tables.references.WALLETS
import br.com.investlog.server.jooq.finances.tables.references.WALLET_MOVES
import br.com.investlog.server.stockholdings.rest.payloads.StockHoldingResponse
import br.com.investlog.server.typelists.rest.payloads.TypeResponse
import br.com.investlog.server.wallets.rest.payloads.WalletResponse
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.returnResult
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WalletMoveControllerTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTestClient: RestTestClient

    @Autowired
    lateinit var dsl: DSLContext

    lateinit var stockTypeId: UUID
    lateinit var fundTypeId: UUID

    private data class Position(val quantity: BigDecimal?, val costBasis: BigDecimal, val status: HoldingStatus)

    @BeforeAll
    fun setup() {
        stockTypeId = createType("/private/v1/stock-types", "Ação ON")
        fundTypeId = createType("/private/v1/fund-types", "Fundos Imobiliários")
    }

    private fun createType(uri: String, name: String): UUID = restTestClient.post()
        .uri(uri)
        .contentType(MediaType.APPLICATION_JSON)
        .body("""{"name":"$name"}""")
        .exchange()
        .returnResult<TypeResponse>()
        .responseBody!!
        .id

    private fun createWallet(name: String, kind: String, currency: String = "BRL"): UUID = restTestClient.post()
        .uri("/private/v1/wallets")
        .contentType(MediaType.APPLICATION_JSON)
        .body("""{"name":"$name","kind":"$kind","currency":"$currency"}""")
        .exchange()
        .returnResult<WalletResponse>()
        .responseBody!!
        .id

    private fun createStockHolding(walletId: UUID, ticker: String, quantity: String, price: String): UUID =
        restTestClient.post()
            .uri("/private/v1/wallets/$walletId/stock-holdings")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                """
                {
                  "stockTypeId":"$stockTypeId",
                  "ticker":"$ticker",
                  "name":"Holding $ticker",
                  "currentPrice":40,
                  "lot":{"lotDate":"2024-01-15","quantity":$quantity,"price":$price}
                }
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isCreated()
            .returnResult<StockHoldingResponse>()
            .responseBody!!
            .id

    private fun addStockLot(walletId: UUID, holdingId: UUID, lotDate: String, quantity: String, price: String) {
        restTestClient.post()
            .uri("/private/v1/wallets/$walletId/stock-holdings/$holdingId/lots")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"lotDate":"$lotDate","quantity":$quantity,"price":$price}""")
            .exchange()
            .expectStatus().isCreated()
    }

    private fun withdrawFromStock(walletId: UUID, holdingId: UUID, quantity: String) {
        restTestClient.post()
            .uri("/private/v1/wallets/$walletId/stock-holdings/$holdingId/withdrawals")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"resultDate":"2024-06-01","quantity":$quantity,"unitPrice":50}""")
            .exchange()
            .expectStatus().isCreated()
    }

    private fun createFundHolding(walletId: UUID, name: String, contribution: String, currentValue: String): UUID =
        restTestClient.post()
            .uri("/private/v1/wallets/$walletId/fund-holdings")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                """
                {
                  "fundTypeId":"$fundTypeId",
                  "name":"$name",
                  "currentValue":$currentValue,
                  "contribution":{"contributionDate":"2024-01-10","amount":$contribution}
                }
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isCreated()
            .returnResult<FundHoldingResponse>()
            .responseBody!!
            .id

    private fun move(originWalletId: UUID, body: String) = restTestClient.post()
        .uri("/private/v1/wallets/$originWalletId/moves")
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .exchange()

    private fun positions(walletId: UUID, name: String): List<Position> {
        val overview = HOLDINGS_OVERVIEW.`as`("overview")
        val wallets = WALLETS.`as`("wallets")

        return dsl.select(overview.QUANTITY, overview.COST_BASIS, overview.STATUS)
            .from(overview)
            .join(wallets).on(wallets.ID.eq(overview.WALLET_ID))
            .where(wallets.EXTERNAL_ID.eq(walletId))
            .and(overview.NAME.eq(name))
            .orderBy(overview.HOLDING_ID)
            .fetch { record ->
                Position(record.get(overview.QUANTITY), record.get(overview.COST_BASIS)!!, record.get(overview.STATUS)!!)
            }
    }

    private fun activePosition(walletId: UUID, name: String): Position =
        positions(walletId, name).single { position -> position.status == HoldingStatus.ACTIVE }

    private fun assertAmount(expected: String, actual: BigDecimal?) {
        assertNotNull(actual)
        assertEquals(
            BigDecimal(expected).setScale(8, RoundingMode.HALF_UP),
            actual.setScale(8, RoundingMode.HALF_UP),
        )
    }

    private fun countRows(table: org.jooq.Table<*>): Int = dsl.fetchCount(dsl.selectFrom(table))

    @Test
    @Order(1)
    fun `moving a whole holding reassigns it with its lots untouched`() {
        val origin = createWallet("Origem 1", "stocks")
        val destination = createWallet("Destino 1", "stocks")
        val holdingId = createStockHolding(origin, "PETR4", "100", "35.00")
        val lotsBefore = dsl.selectFrom(STOCK_LOTS).orderBy(STOCK_LOTS.ID).fetch().map { lot -> lot.intoMap() }

        move(origin, """{"destinationWalletId":"$destination","items":[{"holdingId":"$holdingId"}]}""")
            .expectStatus().isCreated()

        val walletOfHolding = dsl.select(WALLETS.EXTERNAL_ID)
            .from(STOCK_HOLDINGS)
            .join(WALLETS).on(WALLETS.ID.eq(STOCK_HOLDINGS.WALLET_ID))
            .where(STOCK_HOLDINGS.EXTERNAL_ID.eq(holdingId))
            .fetchSingle(WALLETS.EXTERNAL_ID)
        assertEquals(destination, walletOfHolding)

        val lotsAfter = dsl.selectFrom(STOCK_LOTS).orderBy(STOCK_LOTS.ID).fetch().map { lot -> lot.intoMap() }
        assertEquals(lotsBefore, lotsAfter)

        restTestClient.get()
            .uri("/private/v1/holdings?walletId=$destination")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.page.totalElements").isEqualTo(1)
            .jsonPath("$.content[0].ticker").isEqualTo("PETR4")
    }

    @Test
    @Order(2)
    fun `moving into a wallet holding the same ticker merges both sets of lots`() {
        val origin = createWallet("Origem 2", "stocks")
        val destination = createWallet("Destino 2", "stocks")
        val holdingId = createStockHolding(origin, "vale3", "10", "60")
        createStockHolding(destination, "VALE3", "30", "40")

        move(origin, """{"destinationWalletId":"$destination","items":[{"holdingId":"$holdingId"}]}""")
            .expectStatus().isCreated()

        assertEquals(0, positions(origin, "Holding vale3").size)
        val merged = positions(destination, "Holding VALE3")
        assertEquals(1, merged.size)
        assertAmount("40", merged.single().quantity)
        assertAmount("1800", merged.single().costBasis)
    }

    @Test
    @Order(3)
    fun `a partial move splits the position at an unchanged average price`() {
        val origin = createWallet("Origem 3", "stocks")
        val destination = createWallet("Destino 3", "stocks")
        val holdingId = createStockHolding(origin, "ITUB4", "10", "20")
        addStockLot(origin, holdingId, "2024-02-01", "20", "35")

        move(origin, """{"destinationWalletId":"$destination","items":[{"holdingId":"$holdingId","quantity":10}]}""")
            .expectStatus().isCreated()

        val kept = activePosition(origin, "Holding ITUB4")
        val moved = activePosition(destination, "Holding ITUB4")

        assertAmount("20", kept.quantity)
        assertAmount("600", kept.costBasis)
        assertAmount("10", moved.quantity)
        assertAmount("300", moved.costBasis)
    }

    @Test
    @Order(4)
    fun `one call listing every holding empties the origin but keeps it`() {
        val origin = createWallet("Origem 4", "stocks")
        val destination = createWallet("Destino 4", "stocks")
        val first = createStockHolding(origin, "BBAS3", "5", "25")
        val second = createStockHolding(origin, "WEGE3", "8", "30")

        move(
            origin,
            """{"destinationWalletId":"$destination","items":[{"holdingId":"$first"},{"holdingId":"$second"}]}""",
        ).expectStatus().isCreated()

        restTestClient.get()
            .uri("/private/v1/wallets/$origin")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.holdingCount").isEqualTo(0)

        restTestClient.get()
            .uri("/private/v1/wallets/$destination")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.holdingCount").isEqualTo(2)
    }

    @Test
    @Order(5)
    fun `moves between different kinds or currencies are rejected and write nothing`() {
        val origin = createWallet("Origem 5", "stocks")
        val crypto = createWallet("Cripto 5", "crypto")
        val dollars = createWallet("Dólar 5", "stocks", "USD")
        val holdingId = createStockHolding(origin, "ABEV3", "10", "12")
        val movesBefore = countRows(WALLET_MOVES)

        move(origin, """{"destinationWalletId":"$crypto","items":[{"holdingId":"$holdingId"}]}""")
            .expectStatus().isBadRequest()
        move(origin, """{"destinationWalletId":"$dollars","items":[{"holdingId":"$holdingId"}]}""")
            .expectStatus().isBadRequest()
        move(origin, """{"destinationWalletId":"$origin","items":[{"holdingId":"$holdingId"}]}""")
            .expectStatus().isBadRequest()

        assertEquals(movesBefore, countRows(WALLET_MOVES))
        assertAmount("10", activePosition(origin, "Holding ABEV3").quantity)
    }

    @Test
    @Order(6)
    fun `a rejected item rolls back every item in the same call`() {
        val origin = createWallet("Origem 6", "stocks")
        val destination = createWallet("Destino 6", "stocks")
        val valid = createStockHolding(origin, "RENT3", "10", "50")
        val tooLarge = createStockHolding(origin, "LREN3", "10", "20")

        move(
            origin,
            """{"destinationWalletId":"$destination","items":[{"holdingId":"$valid"},{"holdingId":"$tooLarge","quantity":11}]}""",
        ).expectStatus().isBadRequest()

        assertEquals(1, positions(origin, "Holding RENT3").size)
        assertEquals(0, positions(destination, "Holding RENT3").size)
    }

    @Test
    @Order(7)
    fun `a quantity on a fund holding is rejected`() {
        val origin = createWallet("Fundos 7", "funds")
        val destination = createWallet("Fundos Destino 7", "funds")
        val holdingId = createFundHolding(origin, "HGLG11", "1000", "1100")

        move(origin, """{"destinationWalletId":"$destination","items":[{"holdingId":"$holdingId","quantity":1}]}""")
            .expectStatus().isBadRequest()
    }

    @Test
    @Order(8)
    fun `a completed holding cannot be moved`() {
        val origin = createWallet("Origem 8", "stocks")
        val destination = createWallet("Destino 8", "stocks")
        val holdingId = createStockHolding(origin, "MGLU3", "10", "5")
        withdrawFromStock(origin, holdingId, "10")

        move(origin, """{"destinationWalletId":"$destination","items":[{"holdingId":"$holdingId"}]}""")
            .expectStatus().isBadRequest()
    }

    @Test
    @Order(9)
    fun `a fund merge adds contributions and current value to the destination`() {
        val origin = createWallet("Fundos 9", "funds")
        val destination = createWallet("Fundos Destino 9", "funds")
        val holdingId = createFundHolding(origin, "KNRI11", "1000", "1200")
        createFundHolding(destination, "KNRI11", "500", "550")

        move(origin, """{"destinationWalletId":"$destination","items":[{"holdingId":"$holdingId"}]}""")
            .expectStatus().isCreated()

        assertEquals(0, positions(origin, "KNRI11").size)
        val merged = activePosition(destination, "KNRI11")
        assertAmount("1500", merged.costBasis)

        restTestClient.get()
            .uri("/private/v1/holdings?walletId=$destination")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content[0].currentValue").isEqualTo(1750)
    }

    @Test
    @Order(10)
    fun `a partial move of a holding with withdrawals keeps its history and its net average`() {
        val origin = createWallet("Origem 10", "stocks")
        val destination = createWallet("Destino 10", "stocks")
        val holdingId = createStockHolding(origin, "SUZB3", "10", "10")
        withdrawFromStock(origin, holdingId, "5")
        addStockLot(origin, holdingId, "2024-07-01", "5", "20")
        val resultsBefore = countRows(RESULTS)

        move(origin, """{"destinationWalletId":"$destination","items":[{"holdingId":"$holdingId","quantity":4}]}""")
            .expectStatus().isCreated()

        val kept = activePosition(origin, "Holding SUZB3")
        val moved = activePosition(destination, "Holding SUZB3")

        assertAmount("6", kept.quantity)
        assertAmount("90", kept.costBasis)
        assertAmount("4", moved.quantity)
        assertAmount("60", moved.costBasis)
        assertEquals(resultsBefore, countRows(RESULTS))
    }

    @Test
    @Order(11)
    fun `merging a holding with withdrawals completes the origin instead of deleting its history`() {
        val origin = createWallet("Origem 11", "stocks")
        val destination = createWallet("Destino 11", "stocks")
        val holdingId = createStockHolding(origin, "GGBR4", "10", "10")
        withdrawFromStock(origin, holdingId, "4")
        createStockHolding(destination, "GGBR4", "4", "25")
        val resultsBefore = countRows(RESULTS)

        move(origin, """{"destinationWalletId":"$destination","items":[{"holdingId":"$holdingId"}]}""")
            .expectStatus().isCreated()

        val origins = positions(origin, "Holding GGBR4")
        assertEquals(1, origins.size)
        assertEquals(HoldingStatus.COMPLETED, origins.single().status)
        assertAmount("0", origins.single().quantity)
        assertAmount("0", origins.single().costBasis)

        val merged = activePosition(destination, "Holding GGBR4")
        assertAmount("10", merged.quantity)
        assertAmount("160", merged.costBasis)
        assertEquals(resultsBefore, countRows(RESULTS))
    }

    @Test
    @Order(12)
    fun `every move is recorded and readable from both wallets without creating results`() {
        val origin = createWallet("Origem 12", "stocks")
        val destination = createWallet("Destino 12", "stocks")
        val holdingId = createStockHolding(origin, "EGIE3", "30", "40")
        val resultsBefore = countRows(RESULTS)

        move(origin, """{"destinationWalletId":"$destination","items":[{"holdingId":"$holdingId","quantity":10}]}""")
            .expectStatus().isCreated()

        assertEquals(resultsBefore, countRows(RESULTS))

        restTestClient.get()
            .uri("/private/v1/wallets/$origin/moves")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.page.totalElements").isEqualTo(1)
            .jsonPath("$.content[0].direction").isEqualTo("OUT")
            .jsonPath("$.content[0].ticker").isEqualTo("EGIE3")
            .jsonPath("$.content[0].quantity").isEqualTo(10)
            .jsonPath("$.content[0].destinationWalletName").isEqualTo("Destino 12")

        restTestClient.get()
            .uri("/private/v1/wallets/$destination/moves")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.page.totalElements").isEqualTo(1)
            .jsonPath("$.content[0].direction").isEqualTo("IN")
            .jsonPath("$.content[0].originWalletName").isEqualTo("Origem 12")
    }

    @Test
    @Order(13)
    fun `deleting the emptied origin wallet keeps the move in the destination history`() {
        val origin = createWallet("Origem 13", "stocks")
        val destination = createWallet("Destino 13", "stocks")
        val holdingId = createStockHolding(origin, "TAEE11", "10", "30")

        move(origin, """{"destinationWalletId":"$destination","items":[{"holdingId":"$holdingId"}]}""")
            .expectStatus().isCreated()

        restTestClient.delete().uri("/private/v1/wallets/$origin").exchange().expectStatus().isNoContent()

        restTestClient.get()
            .uri("/private/v1/wallets/$destination/moves")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.page.totalElements").isEqualTo(1)
            .jsonPath("$.content[0].originWalletId").doesNotExist()

        val recorded = dsl.selectFrom(WALLET_MOVES)
            .where(WALLET_MOVES.ORIGIN_HOLDING_EXTERNAL_ID.eq(holdingId))
            .fetchSingle()
        assertNull(recorded.originWalletId)
        assertNull(recorded.quantity)
    }
}
