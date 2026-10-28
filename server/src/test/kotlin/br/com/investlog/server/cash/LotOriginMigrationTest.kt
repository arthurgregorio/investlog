package br.com.investlog.server.cash

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.jooq.finances.enums.LotOrigin
import br.com.investlog.server.jooq.finances.tables.references.FUND_CONTRIBUTIONS
import br.com.investlog.server.jooq.finances.tables.references.FUND_HOLDINGS
import br.com.investlog.server.jooq.finances.tables.references.STOCK_HOLDINGS
import br.com.investlog.server.jooq.finances.tables.references.STOCK_LOTS
import br.com.investlog.server.jooq.finances.tables.references.WALLETS
import br.com.investlog.server.fundholdings.rest.payloads.FundHoldingResponse
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
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LotOriginMigrationTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTestClient: RestTestClient

    @Autowired
    lateinit var dsl: DSLContext

    lateinit var reinvestedHoldingId: UUID
    lateinit var stockWalletId: UUID
    lateinit var movedStockWalletId: UUID
    lateinit var movedFundWalletId: UUID

    @BeforeAll
    fun setup() {
        val stockTypeId = createType("/private/v1/stock-types", "Origem Ação")
        val fundTypeId = createType("/private/v1/fund-types", "Origem Fundo")

        stockWalletId = createWallet("Origem Ações", "stocks")
        val sourceId = createStock(stockWalletId, stockTypeId, "ORIG3")
        reinvestedHoldingId = createStock(stockWalletId, stockTypeId, "DEST3")
        post(
            "/private/v1/reinvestments",
            """
            {
              "sourceKind":"STOCKS","sourceHoldingId":"$sourceId",
              "destinationKind":"STOCKS","destinationHoldingId":"$reinvestedHoldingId",
              "reinvestmentDate":"2024-02-01","quantity":2,"unitPrice":20
            }
            """.trimIndent(),
        )

        movedStockWalletId = createWallet("Origem Ações Destino", "stocks")
        post("/private/v1/wallets/$stockWalletId/moves", """{"destinationWalletId":"$movedStockWalletId","items":[{"holdingId":"$sourceId","quantity":3}]}""")

        val fundWalletId = createWallet("Origem Fundos", "funds")
        movedFundWalletId = createWallet("Origem Fundos Destino", "funds")
        val fundId = restTestClient.post()
            .uri("/private/v1/wallets/$fundWalletId/fund-holdings")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                """{"fundTypeId":"$fundTypeId","name":"Fundo Origem","currentValue":500,
                "contribution":{"contributionDate":"2024-01-10","amount":500}}"""
            )
            .exchange()
            .returnResult<FundHoldingResponse>()
            .responseBody!!
            .id
        post("/private/v1/wallets/$fundWalletId/fund-holdings/$fundId/withdrawals", """{"resultDate":"2024-02-01","amount":100}""")
        restTestClient.post()
            .uri("/private/v1/wallets/$movedFundWalletId/fund-holdings")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                """{"fundTypeId":"$fundTypeId","name":"Fundo Origem","currentValue":50,
                "contribution":{"contributionDate":"2024-01-11","amount":50}}"""
            )
            .exchange()
            .expectStatus().isCreated()
        post("/private/v1/wallets/$fundWalletId/moves", """{"destinationWalletId":"$movedFundWalletId","items":[{"holdingId":"$fundId"}]}""")

        dsl.update(STOCK_LOTS).set(STOCK_LOTS.ORIGIN, LotOrigin.PURCHASE).execute()
        dsl.update(FUND_CONTRIBUTIONS).set(FUND_CONTRIBUTIONS.ORIGIN, LotOrigin.PURCHASE).execute()
    }

    private fun createType(uri: String, name: String): UUID = restTestClient.post()
        .uri(uri)
        .contentType(MediaType.APPLICATION_JSON)
        .body("""{"name":"$name"}""")
        .exchange()
        .returnResult<TypeResponse>()
        .responseBody!!
        .id

    private fun createWallet(name: String, kind: String): UUID = restTestClient.post()
        .uri("/private/v1/wallets")
        .contentType(MediaType.APPLICATION_JSON)
        .body("""{"name":"$name","kind":"$kind","currency":"BRL"}""")
        .exchange()
        .returnResult<WalletResponse>()
        .responseBody!!
        .id

    private fun createStock(walletId: UUID, stockTypeId: UUID, ticker: String): UUID = restTestClient.post()
        .uri("/private/v1/wallets/$walletId/stock-holdings")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            """{"stockTypeId":"$stockTypeId","ticker":"$ticker","name":"Holding $ticker","currentPrice":20,
            "lot":{"lotDate":"2024-01-10","quantity":10,"price":10}}"""
        )
        .exchange()
        .expectStatus().isCreated()
        .returnResult<StockHoldingResponse>()
        .responseBody!!
        .id

    private fun post(uri: String, body: String) {
        restTestClient.post()
            .uri(uri)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .exchange()
            .expectStatus().isCreated()
    }

    private fun runMigration() {
        dsl.execute(javaClass.classLoader.getResource(MIGRATION_SQL)!!.readText())
    }

    private fun stockOrigins(walletId: UUID): Map<String, List<LotOrigin?>> =
        dsl.select(STOCK_HOLDINGS.TICKER, STOCK_LOTS.ORIGIN)
            .from(STOCK_LOTS)
            .join(STOCK_HOLDINGS).on(STOCK_HOLDINGS.ID.eq(STOCK_LOTS.STOCK_HOLDING_ID))
            .join(WALLETS).on(WALLETS.ID.eq(STOCK_HOLDINGS.WALLET_ID))
            .where(WALLETS.EXTERNAL_ID.eq(walletId))
            .orderBy(STOCK_LOTS.ID)
            .fetchGroups(STOCK_HOLDINGS.TICKER, STOCK_LOTS.ORIGIN)
            .mapKeys { (ticker, _) -> ticker!! }

    private fun fundOrigins(walletId: UUID): List<LotOrigin?> =
        dsl.select(FUND_CONTRIBUTIONS.ORIGIN)
            .from(FUND_CONTRIBUTIONS)
            .join(FUND_HOLDINGS).on(FUND_HOLDINGS.ID.eq(FUND_CONTRIBUTIONS.FUND_HOLDING_ID))
            .join(WALLETS).on(WALLETS.ID.eq(FUND_HOLDINGS.WALLET_ID))
            .where(WALLETS.EXTERNAL_ID.eq(walletId))
            .orderBy(FUND_CONTRIBUTIONS.ID)
            .fetch(FUND_CONTRIBUTIONS.ORIGIN)

    @Test
    fun `the migration tags reinvestment and move lots and leaves purchases alone, also on a second run`() {
        repeat(2) {
            runMigration()

            assertEquals(
                mapOf(
                    "ORIG3" to listOf(LotOrigin.PURCHASE),
                    "DEST3" to listOf(LotOrigin.PURCHASE, LotOrigin.REINVESTMENT),
                ),
                stockOrigins(stockWalletId),
            )
            assertEquals(mapOf("ORIG3" to listOf(LotOrigin.MOVE)), stockOrigins(movedStockWalletId))
            assertEquals(listOf(LotOrigin.PURCHASE, LotOrigin.MOVE), fundOrigins(movedFundWalletId))
        }
    }

    companion object {
        private const val MIGRATION_SQL = "db/changelog/changes/2026/10/25-1000-tag-lot-origins.sql"
    }
}
