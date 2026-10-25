package br.com.investlog.server.cash

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.fundholdings.rest.payloads.FundHoldingResponse
import br.com.investlog.server.jooq.finances.enums.LotOrigin
import br.com.investlog.server.jooq.finances.tables.references.FUND_CONTRIBUTIONS
import br.com.investlog.server.jooq.finances.tables.references.FUND_HOLDINGS
import br.com.investlog.server.jooq.finances.tables.references.STOCK_HOLDINGS
import br.com.investlog.server.jooq.finances.tables.references.STOCK_LOTS
import br.com.investlog.server.jooq.finances.tables.references.WALLETS
import br.com.investlog.server.overview.rest.payloads.PortfolioSummaryResponse
import br.com.investlog.server.stockholdings.rest.payloads.StockHoldingResponse
import br.com.investlog.server.typelists.rest.payloads.TypeResponse
import br.com.investlog.server.wallets.rest.payloads.WalletResponse
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeAll
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
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CashControllerTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTestClient: RestTestClient

    @Autowired
    lateinit var dsl: DSLContext

    lateinit var stockTypeId: UUID
    lateinit var fundTypeId: UUID

    @BeforeAll
    fun setup() {
        stockTypeId = createType("/private/v1/stock-types", "Caixa Ação")
        fundTypeId = createType("/private/v1/fund-types", "Caixa Fundo")

        restTestClient.put()
            .uri("/private/v1/currency-rates/USD")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"rate":5.00}""")
            .exchange()
            .expectStatus().isOk()
    }

    private fun createType(uri: String, name: String): UUID = restTestClient.post()
        .uri(uri)
        .contentType(MediaType.APPLICATION_JSON)
        .body("""{"name":"$name"}""")
        .exchange()
        .returnResult<TypeResponse>()
        .responseBody!!
        .id

    private fun createWallet(name: String, kind: String = "stocks", currency: String = "BRL"): UUID = restTestClient.post()
        .uri("/private/v1/wallets")
        .contentType(MediaType.APPLICATION_JSON)
        .body("""{"name":"$name","kind":"$kind","currency":"$currency"}""")
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
                  "stockTypeId":"$stockTypeId",
                  "ticker":"$ticker",
                  "name":"Holding $ticker",
                  "currentPrice":50,
                  "lot":{"lotDate":"$lotDate","quantity":$quantity,"price":$price}
                }
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isCreated()
            .returnResult<StockHoldingResponse>()
            .responseBody!!
            .id

    private fun createFundHolding(walletId: UUID, name: String, contributionDate: String, amount: String): UUID =
        restTestClient.post()
            .uri("/private/v1/wallets/$walletId/fund-holdings")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                """
                {
                  "fundTypeId":"$fundTypeId",
                  "name":"$name",
                  "currentValue":$amount,
                  "contribution":{"contributionDate":"$contributionDate","amount":$amount}
                }
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isCreated()
            .returnResult<FundHoldingResponse>()
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

    private fun withdrawFromStock(walletId: UUID, holdingId: UUID, resultDate: String, quantity: String, unitPrice: String) {
        restTestClient.post()
            .uri("/private/v1/wallets/$walletId/stock-holdings/$holdingId/withdrawals")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"resultDate":"$resultDate","quantity":$quantity,"unitPrice":$unitPrice}""")
            .exchange()
            .expectStatus().isCreated()
    }

    private fun transfer(originId: UUID, body: String) = restTestClient.post()
        .uri("/private/v1/wallets/$originId/cash-transfers")
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .exchange()

    private fun withdrawToBank(walletId: UUID, body: String) = restTestClient.post()
        .uri("/private/v1/wallets/$walletId/cash-withdrawals")
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .exchange()

    private fun wallet(walletId: UUID): WalletResponse = restTestClient.get()
        .uri("/private/v1/wallets/$walletId")
        .exchange()
        .expectStatus().isOk()
        .returnResult<WalletResponse>()
        .responseBody!!

    private fun ledger(walletId: UUID): List<Map<*, *>> {
        val body = restTestClient.get()
            .uri("/private/v1/wallets/$walletId/ledger?size=100")
            .exchange()
            .expectStatus().isOk()
            .returnResult<Map<String, Any?>>()
            .responseBody!!

        return (body["content"] as List<*>).map { row -> row as Map<*, *> }
    }

    private fun reconciliation(): List<Map<*, *>> = restTestClient.get()
        .uri("/private/v1/cash-reconciliation")
        .exchange()
        .expectStatus().isOk()
        .returnResult<List<Map<String, Any?>>>()
        .responseBody!!

    private fun detail(body: Map<*, *>, key: String): String? = body[key]?.toString()

    private fun assertAmount(expected: String, actual: Any?) {
        assertNotNull(actual)
        assertEquals(
            BigDecimal(expected).setScale(8, RoundingMode.HALF_UP),
            BigDecimal(actual.toString()).setScale(8, RoundingMode.HALF_UP),
        )
    }

    @Test
    fun `a withdrawal credits cash and a later purchase is paid from it before any new money`() {
        val walletId = createWallet("Caixa Simples")
        val holdingId = createStockHolding(walletId, "PETR4", "2024-01-10", "10", "30")
        withdrawFromStock(walletId, holdingId, "2024-03-01", "4", "50")
        addStockLot(walletId, holdingId, "2024-04-01", "5", "50")

        val rows = ledger(walletId)
        val purchase = rows.first { row -> row["type"] == "PURCHASE" && row["date"] == "2024-04-01" }
        assertAmount("200", purchase["cashUsed"])
        assertAmount("50", purchase["newMoney"])
        assertAmount("0", purchase["cashBalance"])

        val withdrawal = rows.first { row -> row["type"] == "WITHDRAWAL" }
        assertAmount("200", withdrawal["cashBalance"])

        val firstPurchase = rows.first { row -> row["date"] == "2024-01-10" }
        assertAmount("0", firstPurchase["cashUsed"])
        assertAmount("300", firstPurchase["newMoney"])

        assertAmount("0", wallet(walletId).cash)
    }

    @Test
    fun `cash counts in the wallet and overview current value but not in their gain`() {
        val walletId = createWallet("Caixa Valor")
        val holdingId = createStockHolding(walletId, "VALE3", "2024-01-10", "10", "40")
        val overviewBefore = overview()
        withdrawFromStock(walletId, holdingId, "2024-03-01", "2", "50")

        val wallet = wallet(walletId)
        assertAmount("100", wallet.cash)
        assertAmount("500", wallet.currentValue)
        assertAmount("80", wallet.gain)
        assertTrue(wallet.cashReconciled)

        val detail = restTestClient.get()
            .uri("/private/v1/wallets/$walletId/detail")
            .exchange()
            .expectStatus().isOk()
            .returnResult<Map<String, Any?>>()
            .responseBody!!
        assertAmount("100", detail["cash"])
        assertAmount("500", detail["currentValue"])
        assertAmount("80", detail["gain"])

        val overviewAfter = overview()
        assertAmount((overviewBefore.totalCash + BigDecimal("100")).toPlainString(), overviewAfter.totalCash)
        assertEquals(
            0,
            (overviewAfter.totalCurrentValue - overviewAfter.totalCash)
                .compareTo(overviewBefore.totalCurrentValue - overviewBefore.totalCash - BigDecimal("100")),
        )
    }

    private fun overview(): PortfolioSummaryResponse = restTestClient.get()
        .uri("/private/v1/overview")
        .exchange()
        .expectStatus().isOk()
        .returnResult<PortfolioSummaryResponse>()
        .responseBody!!

    @Test
    fun `two exits transferred across currencies pay for a purchase in the destination wallet`() {
        val dollarWalletId = createWallet("Caixa Dólar", currency = "USD")
        val first = createStockHolding(dollarWalletId, "MSFT", "2024-01-10", "10", "40")
        val second = createStockHolding(dollarWalletId, "KO", "2024-01-11", "10", "20")
        withdrawFromStock(dollarWalletId, first, "2024-02-01", "5", "50")
        withdrawFromStock(dollarWalletId, second, "2024-02-02", "5", "30")

        val realWalletId = createWallet("Caixa Real")
        val created = transfer(
            dollarWalletId,
            """{"destinationWalletId":"$realWalletId","transferDate":"2024-02-05","amount":400,"rate":5.2}""",
        )
            .expectStatus().isCreated()
            .returnResult<Map<String, Any?>>()
            .responseBody!!
        assertAmount("2080", created["destinationAmount"])

        createStockHolding(realWalletId, "ITUB4", "2024-02-06", "50", "45")

        assertAmount("0", wallet(dollarWalletId).cash)
        assertAmount("0", wallet(realWalletId).cash)

        val realRows = ledger(realWalletId)
        val purchase = realRows.first { row -> row["type"] == "PURCHASE" }
        assertAmount("2080", purchase["cashUsed"])
        assertAmount("170", purchase["newMoney"])

        val transferIn = realRows.first { row -> row["type"] == "TRANSFER_IN" }
        assertEquals("Caixa Dólar", transferIn["counterpartName"])
        assertEquals("USD", transferIn["originalCurrency"])
        assertAmount("400", transferIn["originalAmount"])
        assertAmount("5.2", transferIn["rate"])
        assertAmount("2080", transferIn["amount"])
        assertNull(transferIn["holdingName"])

        val transferOut = ledger(dollarWalletId).first { row -> row["type"] == "TRANSFER_OUT" }
        assertEquals("Caixa Real", transferOut["counterpartName"])
        assertAmount("400", transferOut["amount"])
        assertAmount("0", transferOut["cashBalance"])
    }

    @Test
    fun `a transfer is refused when it breaks the rate rules or targets the same or an unknown wallet`() {
        val realWalletId = createWallet("Caixa Regras")
        val otherRealWalletId = createWallet("Caixa Regras Destino")
        val dollarWalletId = createWallet("Caixa Regras Dólar", currency = "USD")

        transfer(realWalletId, """{"destinationWalletId":"$otherRealWalletId","transferDate":"2024-01-01","amount":10,"rate":2}""")
            .expectStatus().isBadRequest()
        transfer(realWalletId, """{"destinationWalletId":"$dollarWalletId","transferDate":"2024-01-01","amount":10}""")
            .expectStatus().isBadRequest()
        transfer(realWalletId, """{"destinationWalletId":"$realWalletId","transferDate":"2024-01-01","amount":10}""")
            .expectStatus().isBadRequest()
        transfer(realWalletId, """{"destinationWalletId":"${UUID.randomUUID()}","transferDate":"2024-01-01","amount":10}""")
            .expectStatus().isNotFound()
        transfer(UUID.randomUUID(), """{"destinationWalletId":"$realWalletId","transferDate":"2024-01-01","amount":10}""")
            .expectStatus().isNotFound()
        transfer(realWalletId, """{"destinationWalletId":"$otherRealWalletId","transferDate":"2024-01-01","amount":0}""")
            .expectStatus().isBadRequest()
    }

    @Test
    fun `a transfer above the cash available on its date is refused`() {
        val originId = createWallet("Caixa Insuficiente")
        val destinationId = createWallet("Caixa Insuficiente Destino")
        val holdingId = createStockHolding(originId, "WEGE3", "2024-01-10", "10", "30")
        withdrawFromStock(originId, holdingId, "2024-03-01", "2", "50")

        val problem = transfer(originId, """{"destinationWalletId":"$destinationId","transferDate":"2024-02-01","amount":50}""")
            .expectStatus().isBadRequest()
            .returnResult<Map<String, Any?>>()
            .responseBody!!
        assertEquals("Maior que o caixa disponível em 01/02/2024 (R$ 0,00)", problem["detail"])

        assertAmount("100", wallet(originId).cash)
    }

    @Test
    fun `a bank withdrawal lowers cash, shows its bank and can be deleted`() {
        val walletId = createWallet("Caixa Banco")
        val holdingId = createStockHolding(walletId, "BBAS3", "2024-01-10", "10", "20")
        withdrawFromStock(walletId, holdingId, "2024-03-01", "5", "30")

        val created = withdrawToBank(walletId, """{"withdrawalDate":"2024-03-05","amount":100,"bankName":"  Itaú  "}""")
            .expectStatus().isCreated()
            .returnResult<Map<String, Any?>>()
            .responseBody!!
        assertEquals("Itaú", created["bankName"])
        assertAmount("50", wallet(walletId).cash)

        val row = ledger(walletId).first { entry -> entry["type"] == "CASH_WITHDRAWAL" }
        assertEquals("Itaú", row["counterpartName"])
        assertAmount("100", row["amount"])
        assertAmount("50", row["cashBalance"])

        val problem = withdrawToBank(walletId, """{"withdrawalDate":"2024-03-06","amount":60}""")
            .expectStatus().isBadRequest()
            .returnResult<Map<String, Any?>>()
            .responseBody!!
        assertEquals("Maior que o caixa disponível em 06/03/2024 (R$ 50,00)", problem["detail"])

        withdrawToBank(walletId, """{"withdrawalDate":"2024-03-06","amount":10,"bankName":"   "}""")
            .expectStatus().isCreated()
            .returnResult<Map<String, Any?>>()
            .responseBody!!
            .let { body -> assertNull(body["bankName"]) }

        restTestClient.delete()
            .uri("/private/v1/wallets/$walletId/cash-withdrawals/${created["id"]}")
            .exchange()
            .expectStatus().isNoContent()
        assertAmount("140", wallet(walletId).cash)

        restTestClient.delete()
            .uri("/private/v1/wallets/$walletId/cash-withdrawals/${created["id"]}")
            .exchange()
            .expectStatus().isNotFound()
    }

    @Test
    fun `deleting a transfer whose cash the destination already sent on is refused, otherwise it is undone`() {
        val originId = createWallet("Caixa Desfazer")
        val destinationId = createWallet("Caixa Desfazer Destino")
        val holdingId = createStockHolding(originId, "SUZB3", "2024-01-10", "10", "40")
        withdrawFromStock(originId, holdingId, "2024-03-01", "4", "50")

        val transferId = transfer(originId, """{"destinationWalletId":"$destinationId","transferDate":"2024-03-02","amount":150}""")
            .expectStatus().isCreated()
            .returnResult<Map<String, Any?>>()
            .responseBody!!["id"]
        val bankId = withdrawToBank(destinationId, """{"withdrawalDate":"2024-03-03","amount":100}""")
            .expectStatus().isCreated()
            .returnResult<Map<String, Any?>>()
            .responseBody!!["id"]

        restTestClient.delete()
            .uri("/private/v1/wallets/$destinationId/cash-transfers/$transferId")
            .exchange()
            .expectStatus().isEqualTo(409)

        restTestClient.delete()
            .uri("/private/v1/wallets/$destinationId/cash-withdrawals/$bankId")
            .exchange()
            .expectStatus().isNoContent()

        restTestClient.delete()
            .uri("/private/v1/wallets/$originId/cash-transfers/$transferId")
            .exchange()
            .expectStatus().isNoContent()

        assertAmount("200", wallet(originId).cash)
        assertAmount("0", wallet(destinationId).cash)

        restTestClient.delete()
            .uri("/private/v1/wallets/$originId/cash-transfers/$transferId")
            .exchange()
            .expectStatus().isNotFound()
    }

    @Test
    fun `undoing a withdrawal whose cash already left the wallet is refused`() {
        val walletId = createWallet("Caixa Resgate Usado")
        val holdingId = createStockHolding(walletId, "B3SA3", "2024-01-10", "10", "10")
        withdrawFromStock(walletId, holdingId, "2024-03-01", "5", "20")
        withdrawToBank(walletId, """{"withdrawalDate":"2024-03-05","amount":100}""").expectStatus().isCreated()

        val resultId = ledger(walletId).first { row -> row["type"] == "WITHDRAWAL" }["id"]

        val problem = restTestClient.delete()
            .uri("/private/v1/wallets/$walletId/stock-holdings/$holdingId/withdrawals/$resultId")
            .exchange()
            .expectStatus().isEqualTo(409)
            .returnResult<Map<String, Any?>>()
            .responseBody!!
        assertEquals(
            "O caixa deste resgate já saiu da carteira em 05/03/2024. Exclua a transferência ou o saque antes de desfazer o resgate.",
            problem["detail"],
        )
        assertAmount("0", wallet(walletId).cash)
    }

    @Test
    fun `reinvestments and partial moves write lots that never touch cash`() {
        val walletId = createWallet("Caixa Reinveste")
        val source = createStockHolding(walletId, "ABEV3", "2024-01-10", "10", "10")
        val destination = createStockHolding(walletId, "SBSP3", "2024-01-11", "1", "50")

        restTestClient.post()
            .uri("/private/v1/reinvestments")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                """
                {
                  "sourceKind":"STOCKS","sourceHoldingId":"$source",
                  "destinationKind":"STOCKS","destinationHoldingId":"$destination",
                  "reinvestmentDate":"2024-02-01","quantity":5,"unitPrice":20
                }
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isCreated()

        assertEquals(setOf(LotOrigin.PURCHASE, LotOrigin.REINVESTMENT), lotOrigins(destination))
        assertAmount("0", wallet(walletId).cash)

        val destinationWalletId = createWallet("Caixa Reinveste Destino")
        restTestClient.post()
            .uri("/private/v1/wallets/$walletId/moves")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"destinationWalletId":"$destinationWalletId","items":[{"holdingId":"$source","quantity":2}]}""")
            .exchange()
            .expectStatus().isCreated()

        val movedHolding = dsl.select(STOCK_HOLDINGS.EXTERNAL_ID)
            .from(STOCK_HOLDINGS)
            .join(WALLETS).on(WALLETS.ID.eq(STOCK_HOLDINGS.WALLET_ID))
            .where(WALLETS.EXTERNAL_ID.eq(destinationWalletId))
            .fetchSingle(STOCK_HOLDINGS.EXTERNAL_ID)!!
        assertEquals(setOf(LotOrigin.MOVE), lotOrigins(movedHolding))
        assertAmount("0", wallet(destinationWalletId).cash)
    }

    @Test
    fun `a fund move writes a contribution tagged as a move`() {
        val originId = createWallet("Caixa Fundo Origem", kind = "funds")
        val destinationId = createWallet("Caixa Fundo Destino", kind = "funds")
        val fundId = createFundHolding(originId, "Fundo Caixa", "2024-01-10", "1000")
        createFundHolding(destinationId, "Fundo Caixa", "2024-01-12", "300")
        restTestClient.post()
            .uri("/private/v1/wallets/$originId/fund-holdings/$fundId/withdrawals")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"resultDate":"2024-02-01","amount":100}""")
            .exchange()
            .expectStatus().isCreated()

        restTestClient.post()
            .uri("/private/v1/wallets/$originId/moves")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"destinationWalletId":"$destinationId","items":[{"holdingId":"$fundId"}]}""")
            .exchange()
            .expectStatus().isCreated()

        val origins = dsl.select(FUND_CONTRIBUTIONS.ORIGIN)
            .from(FUND_CONTRIBUTIONS)
            .join(FUND_HOLDINGS).on(FUND_HOLDINGS.ID.eq(FUND_CONTRIBUTIONS.FUND_HOLDING_ID))
            .join(WALLETS).on(WALLETS.ID.eq(FUND_HOLDINGS.WALLET_ID))
            .where(WALLETS.EXTERNAL_ID.eq(destinationId))
            .fetch(FUND_CONTRIBUTIONS.ORIGIN)
            .toSet()
        assertEquals(setOf(LotOrigin.PURCHASE, LotOrigin.MOVE), origins)
        assertAmount("100", wallet(originId).cash)
        assertAmount("0", wallet(destinationId).cash)
    }

    @Test
    fun `moving a holding whose withdrawn cash already left the origin is refused`() {
        val originId = createWallet("Caixa Mover Origem")
        val destinationId = createWallet("Caixa Mover Destino")
        val holdingId = createStockHolding(originId, "RENT3", "2024-01-10", "10", "30")
        withdrawFromStock(originId, holdingId, "2024-03-01", "2", "50")
        withdrawToBank(originId, """{"withdrawalDate":"2024-03-02","amount":100}""").expectStatus().isCreated()

        val problem = restTestClient.post()
            .uri("/private/v1/wallets/$originId/moves")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"destinationWalletId":"$destinationId","items":[{"holdingId":"$holdingId"}]}""")
            .exchange()
            .expectStatus().isBadRequest()
            .returnResult<Map<String, Any?>>()
            .responseBody!!
        assertEquals(
            "O caixa dos resgates desse investimento já saiu da carteira de origem em 02/03/2024. " +
                "Exclua a transferência ou o saque antes de mover.",
            problem["detail"],
        )
        assertEquals(0, ledger(destinationId).size)
    }

    private fun lotOrigins(holdingId: UUID): Set<LotOrigin?> =
        dsl.select(STOCK_LOTS.ORIGIN)
            .from(STOCK_LOTS)
            .join(STOCK_HOLDINGS).on(STOCK_HOLDINGS.ID.eq(STOCK_LOTS.STOCK_HOLDING_ID))
            .where(STOCK_HOLDINGS.EXTERNAL_ID.eq(holdingId))
            .fetch(STOCK_LOTS.ORIGIN)
            .toSet()

    @Test
    fun `historical cash is listed for reconciliation with its sources and a suggested destination until resolved`() {
        val dollarWalletId = createWallet("Conciliar Dólar", currency = "USD")
        val first = createStockHolding(dollarWalletId, "AAPL", "2023-01-10", "10", "40")
        val second = createStockHolding(dollarWalletId, "NVDA", "2023-01-11", "10", "20")
        withdrawFromStock(dollarWalletId, first, "2023-06-01", "5", "50")
        withdrawFromStock(dollarWalletId, second, "2023-06-11", "5", "30")

        val realWalletId = createWallet("Conciliar Real")
        createStockHolding(realWalletId, "ITSA4", "2023-06-20", "200", "10")
        createStockHolding(realWalletId, "TAEE11", "2023-06-21", "10", "30")

        val fundWalletId = createWallet("Conciliar Fundo", kind = "funds")
        val fundId = createFundHolding(fundWalletId, "Fundo Conciliar", "2023-01-10", "5000")
        restTestClient.post()
            .uri("/private/v1/wallets/$fundWalletId/fund-holdings/$fundId/withdrawals")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"resultDate":"2023-03-01","amount":1200}""")
            .exchange()
            .expectStatus().isCreated()

        dsl.update(WALLETS)
            .set(WALLETS.CASH_RECONCILED, false)
            .where(WALLETS.EXTERNAL_ID.`in`(dollarWalletId, fundWalletId, realWalletId))
            .execute()

        val entries = reconciliation().associateBy { entry -> entry["walletName"] }
        assertEquals(setOf("Conciliar Dólar", "Conciliar Fundo"), entries.keys)

        val dollar = entries.getValue("Conciliar Dólar")
        assertEquals("USD", dollar["currency"])
        assertEquals("STOCKS", dollar["kind"])
        assertAmount("400", dollar["cash"])
        val sources = (dollar["sources"] as List<*>).map { source -> source as Map<*, *> }
        assertEquals(listOf("AAPL", "NVDA"), sources.map { source -> source["ticker"] })
        assertEquals(listOf("2023-06-01", "2023-06-11"), sources.map { source -> source["date"] })
        assertAmount("250", sources[0]["amount"])

        val suggestion = dollar["suggestion"] as Map<*, *>
        assertEquals("Conciliar Real", suggestion["walletName"])
        assertEquals(realWalletId.toString(), suggestion["walletId"])
        assertEquals("BRL", suggestion["walletCurrency"])
        assertEquals("ITSA4", suggestion["ticker"])
        assertEquals("2023-06-20", suggestion["purchaseDate"])
        assertAmount("2000", suggestion["newMoney"])
        assertAmount("5", suggestion["rate"])

        val fund = entries.getValue("Conciliar Fundo")
        assertAmount("1200", fund["cash"])
        assertNull(fund["suggestion"])
        assertEquals("Fundo Conciliar", ((fund["sources"] as List<*>).single() as Map<*, *>)["holdingName"])

        restTestClient.post()
            .uri("/private/v1/wallets/$fundWalletId/cash-reconciliation")
            .exchange()
            .expectStatus().isNoContent()
        transfer(dollarWalletId, """{"destinationWalletId":"$realWalletId","transferDate":"2023-06-20","amount":400,"rate":5}""")
            .expectStatus().isCreated()

        assertEquals(emptyList(), reconciliation().filter { entry -> detail(entry, "walletName")!!.startsWith("Conciliar") })
        assertAmount("1200", wallet(fundWalletId).cash)
        assertTrue(wallet(fundWalletId).cashReconciled)

        val funded = ledger(realWalletId).first { row -> row["date"] == "2023-06-20" && row["type"] == "PURCHASE" }
        assertAmount("2000", funded["cashUsed"])
        assertAmount("0", funded["newMoney"])

        restTestClient.post()
            .uri("/private/v1/wallets/${UUID.randomUUID()}/cash-reconciliation")
            .exchange()
            .expectStatus().isNotFound()
    }
}
