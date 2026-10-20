package br.com.investlog.server.results

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.cryptoholdings.rest.payloads.CryptoHoldingResponse
import br.com.investlog.server.fundholdings.rest.payloads.FundHoldingResponse
import br.com.investlog.server.jooq.finances.enums.HoldingStatus
import br.com.investlog.server.jooq.finances.enums.WalletKind
import br.com.investlog.server.jooq.finances.tables.references.CRYPTO_HOLDINGS
import br.com.investlog.server.jooq.finances.tables.references.FUND_HOLDINGS
import br.com.investlog.server.jooq.finances.tables.references.HOLDINGS_OVERVIEW
import br.com.investlog.server.jooq.finances.tables.references.RESULTS
import br.com.investlog.server.jooq.finances.tables.references.STOCK_HOLDINGS
import br.com.investlog.server.stockholdings.rest.payloads.StockHoldingResponse
import br.com.investlog.server.typelists.rest.payloads.TypeResponse
import br.com.investlog.server.wallets.rest.payloads.WalletResponse
import org.jooq.DSLContext
import org.jooq.TableField
import org.jooq.impl.DSL
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
import kotlin.test.assertNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FullExitResidueMigrationTest : BaseIntegrationTest() {

    companion object {
        private const val MIGRATION_SQL = "db/changelog/changes/2026/10/20-1100-net-full-exit-cost-basis-residue.sql"
    }

    @Autowired
    lateinit var restTestClient: RestTestClient

    @Autowired
    lateinit var dsl: DSLContext

    lateinit var stocksWalletId: UUID
    lateinit var cryptoWalletId: UUID
    lateinit var fundsWalletId: UUID
    lateinit var stockTypeId: UUID
    lateinit var fundTypeId: UUID

    private data class HoldingRef(val kind: WalletKind, val holdingId: Long)

    @BeforeAll
    fun setup() {
        stocksWalletId = createWallet("Stocks Wallet", "stocks")
        cryptoWalletId = createWallet("Crypto Wallet", "crypto")
        fundsWalletId = createWallet("Funds Wallet", "funds")
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

    private fun createWallet(name: String, kind: String): UUID = restTestClient.post()
        .uri("/private/v1/wallets")
        .contentType(MediaType.APPLICATION_JSON)
        .body("""{"name":"$name","kind":"$kind","currency":"BRL"}""")
        .exchange()
        .returnResult<WalletResponse>()
        .responseBody!!
        .id

    private fun createStockHolding(ticker: String, quantity: String, price: String): HoldingRef {
        val externalId = restTestClient.post()
            .uri("/private/v1/wallets/$stocksWalletId/stock-holdings")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                """
                {
                  "stockTypeId":"$stockTypeId",
                  "ticker":"$ticker",
                  "name":"Holding $ticker",
                  "currentPrice":$price,
                  "lot":{"lotDate":"2024-01-15","quantity":$quantity,"price":$price}
                }
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isCreated()
            .returnResult<StockHoldingResponse>()
            .responseBody!!
            .id
        val holdingId = dsl.select(STOCK_HOLDINGS.ID)
            .from(STOCK_HOLDINGS)
            .where(STOCK_HOLDINGS.EXTERNAL_ID.eq(externalId))
            .fetchSingle(STOCK_HOLDINGS.ID)!!
        return HoldingRef(WalletKind.STOCKS, holdingId)
    }

    private fun createCryptoHolding(ticker: String, quantity: String, price: String): HoldingRef {
        val externalId = restTestClient.post()
            .uri("/private/v1/wallets/$cryptoWalletId/crypto-holdings")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                """
                {
                  "ticker":"$ticker",
                  "name":"Holding $ticker",
                  "currentPrice":$price,
                  "lot":{"lotDate":"2024-01-15","quantity":$quantity,"price":$price}
                }
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isCreated()
            .returnResult<CryptoHoldingResponse>()
            .responseBody!!
            .id
        val holdingId = dsl.select(CRYPTO_HOLDINGS.ID)
            .from(CRYPTO_HOLDINGS)
            .where(CRYPTO_HOLDINGS.EXTERNAL_ID.eq(externalId))
            .fetchSingle(CRYPTO_HOLDINGS.ID)!!
        return HoldingRef(WalletKind.CRYPTO, holdingId)
    }

    private fun createFundHolding(name: String, amount: String): HoldingRef {
        val externalId = restTestClient.post()
            .uri("/private/v1/wallets/$fundsWalletId/fund-holdings")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                """
                {
                  "fundTypeId":"$fundTypeId",
                  "name":"$name",
                  "currentValue":$amount,
                  "contribution":{"contributionDate":"2024-01-10","amount":$amount}
                }
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isCreated()
            .returnResult<FundHoldingResponse>()
            .responseBody!!
            .id
        val holdingId = dsl.select(FUND_HOLDINGS.ID)
            .from(FUND_HOLDINGS)
            .where(FUND_HOLDINGS.EXTERNAL_ID.eq(externalId))
            .fetchSingle(FUND_HOLDINGS.ID)!!
        return HoldingRef(WalletKind.FUNDS, holdingId)
    }

    private fun externalIdOf(holding: HoldingRef): UUID = dsl.select(HOLDINGS_OVERVIEW.EXTERNAL_ID)
        .from(HOLDINGS_OVERVIEW)
        .where(HOLDINGS_OVERVIEW.KIND.eq(holding.kind))
        .and(HOLDINGS_OVERVIEW.HOLDING_ID.eq(holding.holdingId))
        .fetchSingle(HOLDINGS_OVERVIEW.EXTERNAL_ID)!!

    private fun withdraw(holding: HoldingRef, resultDate: String, quantity: String): Long {
        val (walletId, segment) = when (holding.kind) {
            WalletKind.STOCKS -> stocksWalletId to "stock-holdings"
            WalletKind.CRYPTO -> cryptoWalletId to "crypto-holdings"
            WalletKind.FUNDS -> fundsWalletId to "fund-holdings"
        }
        val body = if (holding.kind == WalletKind.FUNDS) {
            """{"resultDate":"$resultDate","amount":$quantity}"""
        } else {
            """{"resultDate":"$resultDate","quantity":$quantity,"unitPrice":50}"""
        }
        restTestClient.post()
            .uri("/private/v1/wallets/$walletId/$segment/${externalIdOf(holding)}/withdrawals")
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .exchange()
            .expectStatus().isCreated()
        return dsl.select(DSL.max(RESULTS.ID)).from(RESULTS).fetchSingle().value1()!!
    }

    private fun holdingColumnOf(kind: WalletKind): TableField<*, Long?> = when (kind) {
        WalletKind.STOCKS -> RESULTS.STOCK_HOLDING_ID
        WalletKind.CRYPTO -> RESULTS.CRYPTO_HOLDING_ID
        WalletKind.FUNDS -> RESULTS.FUND_HOLDING_ID
    }

    private fun shiftCostBasis(resultId: Long, delta: String) {
        dsl.update(RESULTS)
            .set(RESULTS.COST_BASIS, RESULTS.COST_BASIS.plus(BigDecimal(delta)))
            .where(RESULTS.ID.eq(resultId))
            .execute()
    }

    private fun costBasisOfResult(resultId: Long): BigDecimal =
        dsl.select(RESULTS.COST_BASIS).from(RESULTS).where(RESULTS.ID.eq(resultId)).fetchSingle(RESULTS.COST_BASIS)!!

    private fun overviewCostBasis(holding: HoldingRef): BigDecimal = dsl.select(HOLDINGS_OVERVIEW.COST_BASIS)
        .from(HOLDINGS_OVERVIEW)
        .where(HOLDINGS_OVERVIEW.KIND.eq(holding.kind))
        .and(HOLDINGS_OVERVIEW.HOLDING_ID.eq(holding.holdingId))
        .fetchSingle(HOLDINGS_OVERVIEW.COST_BASIS)!!

    private fun overviewGainPct(holding: HoldingRef): BigDecimal? = dsl.select(HOLDINGS_OVERVIEW.GAIN_PCT)
        .from(HOLDINGS_OVERVIEW)
        .where(HOLDINGS_OVERVIEW.KIND.eq(holding.kind))
        .and(HOLDINGS_OVERVIEW.HOLDING_ID.eq(holding.holdingId))
        .fetchSingle(HOLDINGS_OVERVIEW.GAIN_PCT)

    private fun resultCostBases(holding: HoldingRef): Map<Long, BigDecimal> =
        dsl.select(RESULTS.ID, RESULTS.COST_BASIS)
            .from(RESULTS)
            .where(holdingColumnOf(holding.kind).eq(holding.holdingId))
            .fetch()
            .associate { record -> record.get(RESULTS.ID)!! to record.get(RESULTS.COST_BASIS)!! }

    private fun assertAmount(expected: String, actual: BigDecimal?) {
        assertEquals(0, BigDecimal(expected).compareTo(actual), "expected $expected, got $actual")
    }

    private fun runMigration() {
        val sql = javaClass.classLoader.getResource(MIGRATION_SQL)!!.readText()
        dsl.execute(sql)
    }

    @Test
    @Order(1)
    fun `the migration moves each completed stock or crypto residue onto its most recent result and nothing else`() {
        val stockWithResidue = createStockHolding("PETR4", "3", "10")
        val cryptoSharingItsId = createCryptoHolding("BTC", "2", "100")
        assertEquals(stockWithResidue.holdingId, cryptoSharingItsId.holdingId)

        val laterDatedResult = withdraw(stockWithResidue, "2024-06-10", "1")
        val earlierDatedResult = withdraw(stockWithResidue, "2024-06-01", "2")
        shiftCostBasis(earlierDatedResult, "-0.0000000003")

        withdraw(cryptoSharingItsId, "2024-06-20", "2")
        val cryptoTwinBefore = resultCostBases(cryptoSharingItsId)

        val cryptoOverRecorded = createCryptoHolding("ETH", "4", "25")
        val cryptoOverRecordedResult = withdraw(cryptoOverRecorded, "2024-06-01", "4")
        shiftCostBasis(cryptoOverRecordedResult, "0.0000000004")

        val activeWithResidue = createStockHolding("VALE3", "2", "10")
        val activeResult = withdraw(activeWithResidue, "2024-06-01", "2")
        shiftCostBasis(activeResult, "-0.0000000005")
        dsl.update(STOCK_HOLDINGS)
            .set(STOCK_HOLDINGS.STATUS, HoldingStatus.ACTIVE)
            .where(STOCK_HOLDINGS.ID.eq(activeWithResidue.holdingId))
            .execute()

        val completedAtThreshold = createStockHolding("ITUB4", "2", "10")
        val thresholdResult = withdraw(completedAtThreshold, "2024-06-01", "2")
        shiftCostBasis(thresholdResult, "-0.000001")

        val fundWithResidue = createFundHolding("Fundo XP", "100")
        val fundResult = withdraw(fundWithResidue, "2024-06-01", "100")
        shiftCostBasis(fundResult, "-0.0000000006")

        assertAmount("0.0000000003", overviewCostBasis(stockWithResidue))
        assertAmount("-0.0000000004", overviewCostBasis(cryptoOverRecorded))

        runMigration()

        assertAmount("10.0000000003", costBasisOfResult(laterDatedResult))
        assertAmount("19.9999999997", costBasisOfResult(earlierDatedResult))
        assertAmount("0", overviewCostBasis(stockWithResidue))
        assertNull(overviewGainPct(stockWithResidue))

        assertEquals(cryptoTwinBefore, resultCostBases(cryptoSharingItsId))

        assertAmount("100", costBasisOfResult(cryptoOverRecordedResult))
        assertAmount("0", overviewCostBasis(cryptoOverRecorded))
        assertNull(overviewGainPct(cryptoOverRecorded))

        assertAmount("0.0000000005", overviewCostBasis(activeWithResidue))
        assertAmount("0.000001", overviewCostBasis(completedAtThreshold))
        assertAmount("0.0000000006", overviewCostBasis(fundWithResidue))

        val remainingResidues = dsl.fetchCount(
            dsl.selectFrom(HOLDINGS_OVERVIEW)
                .where(HOLDINGS_OVERVIEW.STATUS.eq(HoldingStatus.COMPLETED))
                .and(HOLDINGS_OVERVIEW.KIND.`in`(WalletKind.STOCKS, WalletKind.CRYPTO))
                .and(HOLDINGS_OVERVIEW.COST_BASIS.ne(BigDecimal.ZERO))
                .and(DSL.abs(HOLDINGS_OVERVIEW.COST_BASIS).lt(BigDecimal("0.000001")))
        )
        assertEquals(0, remainingResidues)
    }

    @Test
    @Order(2)
    fun `running the migration again changes nothing`() {
        val before = dsl.select(RESULTS.ID, RESULTS.COST_BASIS).from(RESULTS).orderBy(RESULTS.ID).fetch().intoMaps()

        runMigration()

        val after = dsl.select(RESULTS.ID, RESULTS.COST_BASIS).from(RESULTS).orderBy(RESULTS.ID).fetch().intoMaps()
        assertEquals(before, after)
    }
}
