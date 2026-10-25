package br.com.investlog.server.walletmoves

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.cryptoholdings.rest.payloads.CryptoHoldingResponse
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
        stockTypeId = createHoldingType("Ação ON", "STOCKS")
        fundTypeId = createHoldingType("Fundos Imobiliários", "FUNDS")
    }

    private fun createHoldingType(name: String, kind: String): UUID = restTestClient.post()
        .uri("/private/v1/holding-types")
        .contentType(MediaType.APPLICATION_JSON)
        .body("""{"name":"$name","kinds":["$kind"]}""")
        .exchange()
        .returnResult<TypeResponse>()
        .responseBody!!
        .id

    private fun createSegment(name: String): UUID = restTestClient.post()
        .uri("/private/v1/segments")
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
                  "typeId":"$stockTypeId",
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
                  "typeId":"$fundTypeId",
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

    private fun createCryptoHolding(walletId: UUID, ticker: String, quantity: String, price: String): UUID =
        restTestClient.post()
            .uri("/private/v1/wallets/$walletId/crypto-holdings")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                """
                {
                  "ticker":"$ticker",
                  "name":"Holding $ticker",
                  "currentPrice":150,
                  "lot":{"lotDate":"2024-01-15","quantity":$quantity,"price":$price}
                }
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isCreated()
            .returnResult<CryptoHoldingResponse>()
            .responseBody!!
            .id

    private fun withdrawFromFund(walletId: UUID, holdingId: UUID, amount: String) {
        restTestClient.post()
            .uri("/private/v1/wallets/$walletId/fund-holdings/$holdingId/withdrawals")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"resultDate":"2024-06-01","amount":$amount}""")
            .exchange()
            .expectStatus().isCreated()
    }

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

    @Test
    @Order(14)
    fun `moves involving an unknown wallet, an unknown holding or a repeated holding are rejected and write nothing`() {
        val origin = createWallet("Origem 14", "stocks")
        val destination = createWallet("Destino 14", "stocks")
        val holdingId = createStockHolding(origin, "CMIG4", "10", "8")
        val movesBefore = countRows(WALLET_MOVES)

        move(UUID.randomUUID(), """{"destinationWalletId":"$destination","items":[{"holdingId":"$holdingId"}]}""")
            .expectStatus().isNotFound()
        move(origin, """{"destinationWalletId":"${UUID.randomUUID()}","items":[{"holdingId":"$holdingId"}]}""")
            .expectStatus().isNotFound()
        move(origin, """{"destinationWalletId":"$destination","items":[{"holdingId":"${UUID.randomUUID()}"}]}""")
            .expectStatus().isNotFound()
        move(
            origin,
            """{"destinationWalletId":"$destination","items":[{"holdingId":"$holdingId"},{"holdingId":"$holdingId"}]}""",
        ).expectStatus().isBadRequest()

        assertEquals(movesBefore, countRows(WALLET_MOVES))
        assertAmount("10", activePosition(origin, "Holding CMIG4").quantity)
    }

    @Test
    @Order(15)
    fun `listing the moves of an unknown wallet responds 404`() {
        restTestClient.get()
            .uri("/private/v1/wallets/${UUID.randomUUID()}/moves")
            .exchange()
            .expectStatus().isNotFound()
    }

    @Test
    @Order(16)
    fun `a partial crypto move into an empty wallet copies the holding and splits the cost`() {
        val origin = createWallet("Cripto Origem 16", "crypto")
        val destination = createWallet("Cripto Destino 16", "crypto")
        val holdingId = createCryptoHolding(origin, "BTC", "10", "100")

        move(origin, """{"destinationWalletId":"$destination","items":[{"holdingId":"$holdingId","quantity":4}]}""")
            .expectStatus().isCreated()

        val kept = activePosition(origin, "Holding BTC")
        val moved = activePosition(destination, "Holding BTC")

        assertAmount("6", kept.quantity)
        assertAmount("600", kept.costBasis)
        assertAmount("4", moved.quantity)
        assertAmount("400", moved.costBasis)
    }

    @Test
    @Order(17)
    fun `moving a whole crypto holding into a wallet holding the same ticker merges the lots and drops the origin`() {
        val origin = createWallet("Cripto Origem 17", "crypto")
        val destination = createWallet("Cripto Destino 17", "crypto")
        val holdingId = createCryptoHolding(origin, "ETH", "10", "100")
        createCryptoHolding(destination, "eth", "5", "200")

        move(origin, """{"destinationWalletId":"$destination","items":[{"holdingId":"$holdingId"}]}""")
            .expectStatus().isCreated()

        assertEquals(0, positions(origin, "Holding ETH").size)
        val merged = positions(destination, "Holding eth").single()
        assertAmount("15", merged.quantity)
        assertAmount("2000", merged.costBasis)
    }

    @Test
    @Order(18)
    fun `merging a fund that has withdrawals keeps its history and moves only the remaining cost and value`() {
        val origin = createWallet("Fundos Origem 18", "funds")
        val destination = createWallet("Fundos Destino 18", "funds")
        val holdingId = createFundHolding(origin, "XPML11", "1000", "1200")
        withdrawFromFund(origin, holdingId, "600")
        createFundHolding(destination, "XPML11", "500", "550")
        val resultsBefore = countRows(RESULTS)

        move(origin, """{"destinationWalletId":"$destination","items":[{"holdingId":"$holdingId"}]}""")
            .expectStatus().isCreated()

        val origins = positions(origin, "XPML11")
        assertEquals(1, origins.size)
        assertEquals(HoldingStatus.COMPLETED, origins.single().status)
        assertAmount("0", origins.single().costBasis)

        assertAmount("1000", activePosition(destination, "XPML11").costBasis)
        assertEquals(resultsBefore, countRows(RESULTS))

        restTestClient.get()
            .uri("/private/v1/holdings?walletId=$destination")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content[0].currentValue").isEqualTo(1150)
    }

    @Test
    @Order(19)
    fun `a partial move keeps the segment on the copy`() {
        val origin = createWallet("Origem Segmento", "stocks")
        val destination = createWallet("Destino Segmento", "stocks")
        val holdingId = createStockHolding(origin, "TAEE11", "10", "30")
        val segmentId = createSegment("Energia")
        restTestClient.put()
            .uri("/private/v1/wallets/$origin/stock-holdings/$holdingId/segment")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"segmentId":"$segmentId"}""")
            .exchange()
            .expectStatus().isOk()

        move(origin, """{"destinationWalletId":"$destination","items":[{"holdingId":"$holdingId","quantity":4}]}""")
            .expectStatus().isCreated()

        restTestClient.get()
            .uri("/private/v1/holdings?walletId=$destination")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.page.totalElements").isEqualTo(1)
            .jsonPath("$.content[0].segmentLabel").isEqualTo("Energia")
    }

    @Test
    @Order(20)
    fun `fully withdrawing a moved position with a repeating average price nets the holding to exactly zero`() {
        val origin = createWallet("Origem Dízima", "stocks")
        val destination = createWallet("Destino Dízima", "stocks")
        val holdingId = createStockHolding(origin, "BBAS3", "4", "10")
        addStockLot(origin, holdingId, "2024-02-01", "5", "12")

        move(origin, """{"destinationWalletId":"$destination","items":[{"holdingId":"$holdingId","quantity":3}]}""")
            .expectStatus().isCreated()

        val overview = HOLDINGS_OVERVIEW.`as`("overview")
        val wallets = WALLETS.`as`("wallets")
        val movedHoldingId = dsl.select(overview.EXTERNAL_ID)
            .from(overview)
            .join(wallets).on(wallets.ID.eq(overview.WALLET_ID))
            .where(wallets.EXTERNAL_ID.eq(destination))
            .fetchSingle(overview.EXTERNAL_ID)!!
        val movedCostBasis = activePosition(destination, "Holding BBAS3").costBasis
        assertEquals(0, BigDecimal("33.33333333333333333333").compareTo(movedCostBasis))

        withdrawFromStock(destination, movedHoldingId, "3")

        val result = dsl.selectFrom(RESULTS).orderBy(RESULTS.ID.desc()).limit(1).fetchSingle()
        assertEquals(0, movedCostBasis.compareTo(result.costBasis))

        val completed = dsl.select(overview.COST_BASIS, overview.GAIN_PCT, overview.STATUS)
            .from(overview)
            .where(overview.EXTERNAL_ID.eq(movedHoldingId))
            .fetchSingle()
        assertEquals(HoldingStatus.COMPLETED, completed.get(overview.STATUS))
        assertEquals(0, BigDecimal.ZERO.compareTo(completed.get(overview.COST_BASIS)))
        assertNull(completed.get(overview.GAIN_PCT))
    }

    @Test
    @Order(21)
    fun `a partial stock move keeps the type and the segment on the copy`() {
        val origin = createWallet("Origem Tipo Ação", "stocks")
        val destination = createWallet("Destino Tipo Ação", "stocks")
        val holdingId = createStockHolding(origin, "CPLE6", "10", "8")
        setSegment(origin, "stock-holdings", holdingId, createSegment("Elétricas"))

        move(origin, """{"destinationWalletId":"$destination","items":[{"holdingId":"$holdingId","quantity":4}]}""")
            .expectStatus().isCreated()

        assertEquals(listOf(Classification("Ação ON", "Elétricas")), activeClassifications(destination))
        assertEquals(listOf(Classification("Ação ON", "Elétricas")), activeClassifications(origin))
    }

    @Test
    @Order(22)
    fun `a partial crypto move keeps the type and the segment on the copy`() {
        val origin = createWallet("Origem Tipo Cripto", "crypto")
        val destination = createWallet("Destino Tipo Cripto", "crypto")
        val holdingId = createCryptoHolding(origin, "DOT", "10", "5")
        val cryptoTypeId = createHoldingType("Cripto Movida", "CRYPTO")
        restTestClient.patch()
            .uri("/private/v1/wallets/$origin/crypto-holdings/$holdingId")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"typeId":"$cryptoTypeId"}""")
            .exchange()
            .expectStatus().isOk()
        setSegment(origin, "crypto-holdings", holdingId, createSegment("Interoperabilidade"))

        move(origin, """{"destinationWalletId":"$destination","items":[{"holdingId":"$holdingId","quantity":3}]}""")
            .expectStatus().isCreated()

        assertEquals(listOf(Classification("Cripto Movida", "Interoperabilidade")), activeClassifications(destination))
    }

    @Test
    @Order(23)
    fun `a whole fund move keeps the type and the segment and merges only into a fund of the same type`() {
        val origin = createWallet("Origem Tipo Fundo", "funds")
        val destination = createWallet("Destino Tipo Fundo", "funds")
        val otherFundTypeId = createHoldingType("Outro Fundo", "FUNDS")
        createFundHoldingOfType(destination, "Fundo Gêmeo", otherFundTypeId)
        val holdingId = createFundHolding(origin, "Fundo Gêmeo", "1000", "1000")
        setSegment(origin, "fund-holdings", holdingId, createSegment("Lajes Corporativas"))

        move(origin, """{"destinationWalletId":"$destination","items":[{"holdingId":"$holdingId"}]}""")
            .expectStatus().isCreated()

        assertEquals(
            listOf(Classification("Fundos Imobiliários", "Lajes Corporativas"), Classification("Outro Fundo", null)),
            activeClassifications(destination).sortedBy { classification -> classification.typeLabel },
        )

        val secondOrigin = createWallet("Origem Fundo Igual", "funds")
        val sameTypeHoldingId = createFundHolding(secondOrigin, "Fundo Gêmeo", "500", "500")

        move(secondOrigin, """{"destinationWalletId":"$destination","items":[{"holdingId":"$sameTypeHoldingId"}]}""")
            .expectStatus().isCreated()

        val twins = positions(destination, "Fundo Gêmeo")
            .filter { position -> position.status == HoldingStatus.ACTIVE }
            .sortedBy { position -> position.costBasis }
        assertEquals(2, twins.size)
        assertAmount("200", twins[0].costBasis)
        assertAmount("1500", twins[1].costBasis)
    }

    private data class Classification(val typeLabel: String?, val segmentLabel: String?)

    private fun activeClassifications(walletId: UUID): List<Classification> {
        val overview = HOLDINGS_OVERVIEW.`as`("overview")
        val wallets = WALLETS.`as`("wallets")

        return dsl.select(overview.TYPE_LABEL, overview.SEGMENT_LABEL)
            .from(overview)
            .join(wallets).on(wallets.ID.eq(overview.WALLET_ID))
            .where(wallets.EXTERNAL_ID.eq(walletId))
            .and(overview.STATUS.eq(HoldingStatus.ACTIVE))
            .orderBy(overview.HOLDING_ID)
            .fetch { record -> Classification(record.get(overview.TYPE_LABEL), record.get(overview.SEGMENT_LABEL)) }
    }

    private fun setSegment(walletId: UUID, holdingsPath: String, holdingId: UUID, segmentId: UUID) {
        restTestClient.put()
            .uri("/private/v1/wallets/$walletId/$holdingsPath/$holdingId/segment")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"segmentId":"$segmentId"}""")
            .exchange()
            .expectStatus().isOk()
    }

    private fun createFundHoldingOfType(walletId: UUID, name: String, typeId: UUID) {
        restTestClient.post()
            .uri("/private/v1/wallets/$walletId/fund-holdings")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                """
                {
                  "typeId":"$typeId",
                  "name":"$name",
                  "currentValue":200,
                  "contribution":{"contributionDate":"2024-01-10","amount":200}
                }
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isCreated()
    }
}
