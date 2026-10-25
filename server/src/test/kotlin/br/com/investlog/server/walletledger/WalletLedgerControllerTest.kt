package br.com.investlog.server.walletledger

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.auth.rest.payloads.SessionResponse
import br.com.investlog.server.auth.rest.payloads.TotpEnrollResponse
import br.com.investlog.server.cryptoholdings.rest.payloads.CryptoHoldingResponse
import br.com.investlog.server.fundholdings.rest.payloads.FundHoldingResponse
import br.com.investlog.server.jooq.finances.enums.HoldingStatus
import br.com.investlog.server.jooq.finances.tables.references.RESULTS
import br.com.investlog.server.jooq.finances.tables.references.STOCK_HOLDINGS
import br.com.investlog.server.stockholdings.rest.payloads.StockHoldingResponse
import br.com.investlog.server.typelists.rest.payloads.TypeResponse
import br.com.investlog.server.wallets.rest.payloads.WalletResponse
import dev.samstevens.totp.code.DefaultCodeGenerator
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
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WalletLedgerControllerTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTestClient: RestTestClient

    @Autowired
    lateinit var dsl: DSLContext

    lateinit var stockTypeId: UUID
    lateinit var fundTypeId: UUID

    private data class Ledger(val rows: List<Map<*, *>>, val totalElements: Int)

    @BeforeAll
    fun setup() {
        stockTypeId = createHoldingType("Ledger Ação", "STOCKS")
        fundTypeId = createHoldingType("Ledger Fundo", "FUNDS")
    }

    private fun createHoldingType(name: String, kind: String): UUID = restTestClient.post()
        .uri("/private/v1/holding-types")
        .contentType(MediaType.APPLICATION_JSON)
        .body("""{"name":"$name","kinds":["$kind"]}""")
        .exchange()
        .returnResult<TypeResponse>()
        .responseBody!!
        .id

    private fun createWallet(name: String, kind: String, cookie: String? = null): UUID = restTestClient.post()
        .uri("/private/v1/wallets")
        .contentType(MediaType.APPLICATION_JSON)
        .apply { cookie?.let { header("Cookie", it) } }
        .body("""{"name":"$name","kind":"$kind","currency":"BRL"}""")
        .exchange()
        .returnResult<WalletResponse>()
        .responseBody!!
        .id

    private fun createStockHolding(walletId: UUID, ticker: String, lotDate: String, quantity: String, price: String): UUID =
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
                  "lot":{"lotDate":"$lotDate","quantity":$quantity,"price":$price}
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
                  "lot":{"lotDate":"2024-02-20","quantity":$quantity,"price":$price}
                }
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isCreated()
            .returnResult<CryptoHoldingResponse>()
            .responseBody!!
            .id

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

    private fun withdrawFromStock(walletId: UUID, holdingId: UUID, quantity: String, unitPrice: String) {
        restTestClient.post()
            .uri("/private/v1/wallets/$walletId/stock-holdings/$holdingId/withdrawals")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"resultDate":"2024-06-01","quantity":$quantity,"unitPrice":$unitPrice}""")
            .exchange()
            .expectStatus().isCreated()
    }

    private fun withdrawFromFund(walletId: UUID, holdingId: UUID, amount: String) {
        restTestClient.post()
            .uri("/private/v1/wallets/$walletId/fund-holdings/$holdingId/withdrawals")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"resultDate":"2024-06-01","amount":$amount}""")
            .exchange()
            .expectStatus().isCreated()
    }

    private fun reinvestFromStock(sourceId: UUID, destinationKind: String, destinationId: UUID, quantity: String, unitPrice: String) {
        restTestClient.post()
            .uri("/private/v1/reinvestments")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                """
                {
                  "sourceKind":"STOCKS","sourceHoldingId":"$sourceId",
                  "destinationKind":"$destinationKind","destinationHoldingId":"$destinationId",
                  "reinvestmentDate":"2024-07-01","quantity":$quantity,"unitPrice":$unitPrice
                }
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isCreated()
    }

    private fun move(originWalletId: UUID, body: String) {
        restTestClient.post()
            .uri("/private/v1/wallets/$originWalletId/moves")
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .exchange()
            .expectStatus().isCreated()
    }

    private fun ledger(walletId: UUID, query: String = "?size=100"): Ledger {
        val body = restTestClient.get()
            .uri("/private/v1/wallets/$walletId/ledger$query")
            .exchange()
            .expectStatus().isOk()
            .returnResult<Map<String, Any?>>()
            .responseBody!!

        return Ledger(
            rows = (body["content"] as List<*>).map { row -> row as Map<*, *> },
            totalElements = ((body["page"] as Map<*, *>)["totalElements"] as Number).toInt(),
        )
    }

    private fun assertAmount(expected: String, actual: Any?) {
        assertNotNull(actual)
        assertEquals(
            BigDecimal(expected).setScale(8, RoundingMode.HALF_UP),
            BigDecimal(actual.toString()).setScale(8, RoundingMode.HALF_UP),
        )
    }

    private fun holdingStatus(holdingId: UUID): HoldingStatus =
        dsl.select(STOCK_HOLDINGS.STATUS)
            .from(STOCK_HOLDINGS)
            .where(STOCK_HOLDINGS.EXTERNAL_ID.eq(holdingId))
            .fetchSingle(STOCK_HOLDINGS.STATUS)!!

    private fun storedProfit(resultId: String): BigDecimal =
        dsl.select(RESULTS.PROFIT)
            .from(RESULTS)
            .where(RESULTS.EXTERNAL_ID.eq(UUID.fromString(resultId)))
            .fetchSingle(RESULTS.PROFIT)!!

    @Test
    @Order(1)
    fun `stock purchases and withdrawals are listed newest first with net amount and profit`() {
        val walletId = createWallet("Ledger Ações", "stocks")
        val holdingId = createStockHolding(walletId, "PETR4", "2024-01-15", "10", "30")
        withdrawFromStock(walletId, holdingId, "4", "50")

        val ledger = ledger(walletId)

        assertEquals(2, ledger.totalElements)
        assertEquals(listOf("WITHDRAWAL", "PURCHASE"), ledger.rows.map { row -> row["type"] })

        val withdrawal = ledger.rows[0]
        assertEquals("2024-06-01", withdrawal["date"])
        assertEquals("STOCKS", withdrawal["kind"])
        assertEquals(holdingId.toString(), withdrawal["holdingId"])
        assertEquals("Holding PETR4", withdrawal["holdingName"])
        assertEquals("PETR4", withdrawal["ticker"])
        assertAmount("4", withdrawal["quantity"])
        assertAmount("200", withdrawal["amount"])
        assertAmount("80", withdrawal["profit"])
        assertNull(withdrawal["counterpartName"])

        val purchase = ledger.rows[1]
        assertEquals("2024-01-15", purchase["date"])
        assertEquals(holdingId.toString(), purchase["holdingId"])
        assertAmount("10", purchase["quantity"])
        assertAmount("300", purchase["amount"])
        assertNull(purchase["profit"])
        assertNull(purchase["counterpartName"])
    }

    @Test
    @Order(2)
    fun `crypto purchases, fund contributions and fund withdrawals are listed in their own wallets`() {
        val cryptoWalletId = createWallet("Ledger Cripto", "crypto")
        val cryptoHoldingId = createCryptoHolding(cryptoWalletId, "BTC", "0.5", "100")
        val fundWalletId = createWallet("Ledger Fundos", "funds")
        val fundHoldingId = createFundHolding(fundWalletId, "Tesouro Selic", "1000", "1100")
        withdrawFromFund(fundWalletId, fundHoldingId, "550")

        val cryptoLedger = ledger(cryptoWalletId)
        assertEquals(1, cryptoLedger.totalElements)
        val cryptoPurchase = cryptoLedger.rows.single()
        assertEquals("PURCHASE", cryptoPurchase["type"])
        assertEquals("CRYPTO", cryptoPurchase["kind"])
        assertEquals(cryptoHoldingId.toString(), cryptoPurchase["holdingId"])
        assertEquals("BTC", cryptoPurchase["ticker"])
        assertAmount("0.5", cryptoPurchase["quantity"])
        assertAmount("50", cryptoPurchase["amount"])

        val fundLedger = ledger(fundWalletId)
        assertEquals(2, fundLedger.totalElements)
        assertEquals(listOf("WITHDRAWAL", "CONTRIBUTION"), fundLedger.rows.map { row -> row["type"] })

        val fundWithdrawal = fundLedger.rows[0]
        assertEquals("FUNDS", fundWithdrawal["kind"])
        assertEquals(fundHoldingId.toString(), fundWithdrawal["holdingId"])
        assertNull(fundWithdrawal["ticker"])
        assertNull(fundWithdrawal["quantity"])
        assertAmount("550", fundWithdrawal["amount"])
        assertAmount(storedProfit(fundWithdrawal["id"] as String).toPlainString(), fundWithdrawal["profit"])

        val contribution = fundLedger.rows[1]
        assertEquals("2024-01-10", contribution["date"])
        assertEquals("Tesouro Selic", contribution["holdingName"])
        assertNull(contribution["quantity"])
        assertAmount("1000", contribution["amount"])
    }

    @Test
    @Order(3)
    fun `a fully withdrawn holding keeps its purchases and its withdrawal in the ledger`() {
        val walletId = createWallet("Ledger Resgatada", "stocks")
        val holdingId = createStockHolding(walletId, "ITSA4", "2024-01-15", "10", "10")
        withdrawFromStock(walletId, holdingId, "10", "12")

        assertEquals(HoldingStatus.COMPLETED, holdingStatus(holdingId))
        restTestClient.get()
            .uri("/private/v1/holdings?walletId=$walletId")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.page.totalElements").isEqualTo(0)

        val ledger = ledger(walletId)

        assertEquals(2, ledger.totalElements)
        assertEquals(listOf("WITHDRAWAL", "PURCHASE"), ledger.rows.map { row -> row["type"] })
        assertEquals(listOf(holdingId.toString(), holdingId.toString()), ledger.rows.map { row -> row["holdingId"] })
        assertAmount("120", ledger.rows[0]["amount"])
        assertAmount("20", ledger.rows[0]["profit"])
    }

    @Test
    @Order(4)
    fun `a move is listed as out in the origin and in in the destination with the other wallet as counterpart`() {
        val originId = createWallet("Ledger Origem", "stocks")
        val destinationId = createWallet("Ledger Destino", "stocks")
        val partialHoldingId = createStockHolding(originId, "VALE3", "2024-01-15", "10", "60")
        val wholeHoldingId = createStockHolding(originId, "BBAS3", "2024-02-15", "5", "20")
        val movedAt = LocalDate.now(ZoneId.of("America/Sao_Paulo")).toString()

        move(originId, """{"destinationWalletId":"$destinationId","items":[{"holdingId":"$partialHoldingId","quantity":4}]}""")
        move(originId, """{"destinationWalletId":"$destinationId","items":[{"holdingId":"$wholeHoldingId"}]}""")

        val originLedger = ledger(originId)
        assertEquals(3, originLedger.totalElements)
        assertEquals(listOf("MOVE_OUT", "MOVE_OUT", "PURCHASE"), originLedger.rows.map { row -> row["type"] })

        val wholeMoveOut = originLedger.rows[0]
        assertEquals(movedAt, wholeMoveOut["date"])
        assertEquals("BBAS3", wholeMoveOut["ticker"])
        assertEquals(wholeHoldingId.toString(), wholeMoveOut["holdingId"])
        assertNull(wholeMoveOut["quantity"])
        assertNull(wholeMoveOut["amount"])
        assertNull(wholeMoveOut["profit"])
        assertEquals("Ledger Destino", wholeMoveOut["counterpartName"])

        val partialMoveOut = originLedger.rows[1]
        assertEquals("VALE3", partialMoveOut["ticker"])
        assertEquals(partialHoldingId.toString(), partialMoveOut["holdingId"])
        assertAmount("4", partialMoveOut["quantity"])
        assertEquals("Ledger Destino", partialMoveOut["counterpartName"])

        assertEquals("VALE3", originLedger.rows[2]["ticker"])
        assertEquals("2024-01-15", originLedger.rows[2]["date"])

        val destinationLedger = ledger(destinationId)
        assertEquals(4, destinationLedger.totalElements)

        val moveIns = destinationLedger.rows.filter { row -> row["type"] == "MOVE_IN" }
        assertEquals(setOf("VALE3", "BBAS3"), moveIns.map { row -> row["ticker"] }.toSet())
        moveIns.forEach { row ->
            assertEquals(movedAt, row["date"])
            assertEquals("Ledger Origem", row["counterpartName"])
        }
        assertEquals(wholeHoldingId.toString(), moveIns.single { row -> row["ticker"] == "BBAS3" }["holdingId"])

        val purchases = destinationLedger.rows.filter { row -> row["type"] == "PURCHASE" }
        val splitPurchase = purchases.single { row -> row["ticker"] == "VALE3" }
        assertEquals(movedAt, splitPurchase["date"])
        assertAmount("4", splitPurchase["quantity"])
        assertAmount("240", splitPurchase["amount"])
        val carriedPurchase = purchases.single { row -> row["ticker"] == "BBAS3" }
        assertEquals("2024-02-15", carriedPurchase["date"])
        assertEquals(wholeHoldingId.toString(), carriedPurchase["holdingId"])
        assertEquals("BBAS3", destinationLedger.rows.last()["ticker"])
    }

    @Test
    @Order(5)
    fun `a reinvestment names its destination holding, which lists the money as an ordinary purchase or contribution`() {
        val sourceWalletId = createWallet("Ledger Fonte", "stocks")
        val sourceHoldingId = createStockHolding(sourceWalletId, "WEGE3", "2024-01-15", "10", "20")
        val stockDestinationWalletId = createWallet("Ledger Destino Ações", "stocks")
        val stockDestinationId = createStockHolding(stockDestinationWalletId, "TAEE11", "2024-01-20", "1", "40")
        val fundDestinationWalletId = createWallet("Ledger Destino Fundos", "funds")
        val fundDestinationId = createFundHolding(fundDestinationWalletId, "CDB Liquidez", "500", "500")

        reinvestFromStock(sourceHoldingId, "STOCKS", stockDestinationId, "2", "40")
        reinvestFromStock(sourceHoldingId, "FUNDS", fundDestinationId, "5", "30")

        val sourceLedger = ledger(sourceWalletId)
        assertEquals(3, sourceLedger.totalElements)
        val reinvestments = sourceLedger.rows.filter { row -> row["type"] == "REINVESTMENT" }
        assertEquals(setOf("TAEE11", "CDB Liquidez"), reinvestments.map { row -> row["counterpartName"] }.toSet())

        val intoFund = reinvestments.single { row -> row["counterpartName"] == "CDB Liquidez" }
        assertEquals("2024-07-01", intoFund["date"])
        assertEquals(sourceHoldingId.toString(), intoFund["holdingId"])
        assertAmount("5", intoFund["quantity"])
        assertAmount("150", intoFund["amount"])
        assertAmount("50", intoFund["profit"])

        val intoStock = reinvestments.single { row -> row["counterpartName"] == "TAEE11" }
        assertAmount("80", intoStock["amount"])
        assertAmount("40", intoStock["profit"])

        val stockDestinationLedger = ledger(stockDestinationWalletId)
        assertEquals(listOf("PURCHASE", "PURCHASE"), stockDestinationLedger.rows.map { row -> row["type"] })
        assertEquals("2024-07-01", stockDestinationLedger.rows[0]["date"])
        assertAmount("80", stockDestinationLedger.rows[0]["amount"])
        assertNull(stockDestinationLedger.rows[0]["counterpartName"])

        val fundDestinationLedger = ledger(fundDestinationWalletId)
        assertEquals(listOf("CONTRIBUTION", "CONTRIBUTION"), fundDestinationLedger.rows.map { row -> row["type"] })
        assertEquals("2024-07-01", fundDestinationLedger.rows[0]["date"])
        assertAmount("150", fundDestinationLedger.rows[0]["amount"])
    }

    @Test
    @Order(6)
    fun `same-day events fall back to creation order and pages never overlap`() {
        val walletId = createWallet("Ledger Paginada", "stocks")
        val holdingId = createStockHolding(walletId, "BBDC4", "2024-01-01", "3", "10")
        addStockLot(walletId, holdingId, "2024-03-01", "1", "10")
        addStockLot(walletId, holdingId, "2024-03-01", "2", "10")
        addStockLot(walletId, holdingId, "2024-05-01", "4", "10")

        val firstPage = ledger(walletId, "?page=0&size=2")
        val secondPage = ledger(walletId, "?page=1&size=2")

        assertEquals(4, firstPage.totalElements)
        assertEquals(4, secondPage.totalElements)
        val quantities = (firstPage.rows + secondPage.rows).map { row -> BigDecimal(row["quantity"].toString()).toInt() }
        assertEquals(listOf(4, 2, 1, 3), quantities)
    }

    @Test
    @Order(7)
    fun `another user's wallet or an unknown wallet responds 404`() {
        val otherUserCookie = registerApproveAndLogin("ledger-outro@example.com", "Senha123")
        val otherWalletId = createWallet("Ledger Outro Usuário", "stocks", otherUserCookie)

        restTestClient.get()
            .uri("/private/v1/wallets/$otherWalletId/ledger")
            .exchange()
            .expectStatus().isNotFound()

        restTestClient.get()
            .uri("/private/v1/wallets/${UUID.randomUUID()}/ledger")
            .exchange()
            .expectStatus().isNotFound()

        restTestClient.get()
            .uri("/private/v1/wallets/$otherWalletId/ledger")
            .header("Cookie", otherUserCookie)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.page.totalElements").isEqualTo(0)
    }

    private fun registerApproveAndLogin(email: String, password: String): String {
        restTestClient.post()
            .uri("/private/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Teste","email":"$email","password":"$password"}""")
            .exchange()
            .expectStatus().isCreated()

        val targetId = (
            restTestClient.get()
                .uri("/private/v1/users?size=200")
                .exchange()
                .expectStatus().isOk()
                .returnResult<Map<String, Any?>>()
                .responseBody
                ?.get("content") as List<*>
            )
            .map { it as Map<*, *> }
            .single { it["email"] == email }["id"] as String

        restTestClient.patch()
            .uri("/private/v1/users/$targetId/approve")
            .exchange()
            .expectStatus().isOk()

        val secret = restTestClient.post()
            .uri("/private/v1/auth/totp/enroll")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"email":"$email","password":"$password"}""")
            .exchange()
            .expectStatus().isOk()
            .returnResult<TotpEnrollResponse>()
            .responseBody
            ?.secretKey
            ?: error("Enroll did not return a secret")

        val code = DefaultCodeGenerator().generate(secret, System.currentTimeMillis() / 1000L / 30L)

        return restTestClient.post()
            .uri("/private/v1/auth/totp/verify")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"email":"$email","password":"$password","code":"$code"}""")
            .exchange()
            .expectStatus().isOk()
            .returnResult<SessionResponse>()
            .responseHeaders
            .getFirst("Set-Cookie")
            ?.substringBefore(";")
            ?: error("Verify did not set a session cookie")
    }
}
