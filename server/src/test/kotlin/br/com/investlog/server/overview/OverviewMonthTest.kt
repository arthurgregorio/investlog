package br.com.investlog.server.overview

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.cryptoholdings.rest.payloads.CryptoHoldingResponse
import br.com.investlog.server.jooq.finances.enums.WalletKind
import br.com.investlog.server.jooq.finances.tables.references.CRYPTO_HOLDINGS
import br.com.investlog.server.jooq.finances.tables.references.HOLDING_DAILY_SNAPSHOTS
import br.com.investlog.server.jooq.finances.tables.references.STOCK_HOLDINGS
import br.com.investlog.server.jooq.finances.tables.references.WALLETS
import br.com.investlog.server.jooq.finances.tables.references.WALLET_DAILY_SNAPSHOTS
import br.com.investlog.server.overview.rest.payloads.MonthSummaryResponse
import br.com.investlog.server.overview.rest.payloads.SeriesPointResponse
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
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OverviewMonthTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTestClient: RestTestClient

    @Autowired
    lateinit var dsl: DSLContext

    private val today: LocalDate = LocalDate.now(ZoneId.of("America/Sao_Paulo"))
    private val currentMonth: YearMonth = YearMonth.from(today)
    private val lastMonthEnd: LocalDate = currentMonth.atDay(1).minusDays(1)

    lateinit var stocksWalletId: UUID
    lateinit var cryptoWalletId: UUID
    lateinit var risingStockId: UUID
    lateinit var fallingStockId: UUID
    lateinit var cryptoHoldingId: UUID

    @BeforeAll
    fun setup() {
        val stockTypeId = restTestClient.post()
            .uri("/private/v1/holding-types")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Month Type","kinds":["STOCKS"]}""")
            .exchange()
            .returnResult<TypeResponse>()
            .responseBody!!
            .id

        stocksWalletId = createWallet("Month Stocks", "STOCKS")
        cryptoWalletId = createWallet("Month Crypto", "CRYPTO")

        risingStockId = createStock(stockTypeId, "RISE3", currentPrice = "12")
        fallingStockId = createStock(stockTypeId, "FALL3", currentPrice = "8")
        cryptoHoldingId = restTestClient.post()
            .uri("/private/v1/wallets/$cryptoWalletId/crypto-holdings")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"ticker":"MBTC","name":"Month Coin","currentPrice":110,"lot":{"lotDate":"2025-01-10","quantity":1,"price":100}}""")
            .exchange()
            .expectStatus().isCreated()
            .returnResult<CryptoHoldingResponse>()
            .responseBody!!
            .id
    }

    private fun createWallet(name: String, kind: String): UUID = restTestClient.post()
        .uri("/private/v1/wallets")
        .contentType(MediaType.APPLICATION_JSON)
        .body("""{"name":"$name","kind":"$kind","currency":"BRL"}""")
        .exchange()
        .returnResult<WalletResponse>()
        .responseBody!!
        .id

    private fun createStock(typeId: UUID, ticker: String, currentPrice: String): UUID = restTestClient.post()
        .uri("/private/v1/wallets/$stocksWalletId/stock-holdings")
        .contentType(MediaType.APPLICATION_JSON)
        .body("""{"typeId":"$typeId","ticker":"$ticker","currentPrice":$currentPrice,"lot":{"lotDate":"2025-01-10","quantity":10,"price":10}}""")
        .exchange()
        .expectStatus().isCreated()
        .returnResult<StockHoldingResponse>()
        .responseBody!!
        .id

    private fun month(): MonthSummaryResponse = restTestClient.get()
        .uri("/private/v1/overview/month")
        .exchange()
        .expectStatus().isOk()
        .returnResult<MonthSummaryResponse>()
        .responseBody!!

    private fun series(): List<SeriesPointResponse> = restTestClient.get()
        .uri("/private/v1/overview/series")
        .exchange()
        .expectStatus().isOk()
        .returnResult<List<SeriesPointResponse>>()
        .responseBody!!

    private fun walletId(externalId: UUID): Long =
        dsl.select(WALLETS.ID).from(WALLETS).where(WALLETS.EXTERNAL_ID.eq(externalId)).fetchSingle().value1()!!

    private fun recordWalletSnapshot(walletExternalId: UUID, gain: String) {
        dsl.insertInto(WALLET_DAILY_SNAPSHOTS)
            .set(WALLET_DAILY_SNAPSHOTS.WALLET_ID, walletId(walletExternalId))
            .set(WALLET_DAILY_SNAPSHOTS.SNAPSHOT_DATE, lastMonthEnd)
            .set(WALLET_DAILY_SNAPSHOTS.CURRENT_VALUE, BigDecimal.ZERO)
            .set(WALLET_DAILY_SNAPSHOTS.TOTAL_INVESTED, BigDecimal.ZERO)
            .set(WALLET_DAILY_SNAPSHOTS.GAIN, BigDecimal(gain))
            .set(WALLET_DAILY_SNAPSHOTS.GAIN_PCT, BigDecimal.ZERO)
            .execute()
    }

    private fun recordHoldingSnapshot(kind: WalletKind, holdingId: Long, walletExternalId: UUID, price: String) {
        dsl.insertInto(HOLDING_DAILY_SNAPSHOTS)
            .set(HOLDING_DAILY_SNAPSHOTS.KIND, kind)
            .set(HOLDING_DAILY_SNAPSHOTS.HOLDING_ID, holdingId)
            .set(HOLDING_DAILY_SNAPSHOTS.WALLET_ID, walletId(walletExternalId))
            .set(HOLDING_DAILY_SNAPSHOTS.SNAPSHOT_DATE, lastMonthEnd)
            .set(HOLDING_DAILY_SNAPSHOTS.CURRENT_PRICE, BigDecimal(price))
            .set(HOLDING_DAILY_SNAPSHOTS.COST_BASIS, BigDecimal.ZERO)
            .execute()
    }

    private fun stockId(externalId: UUID): Long =
        dsl.select(STOCK_HOLDINGS.ID).from(STOCK_HOLDINGS).where(STOCK_HOLDINGS.EXTERNAL_ID.eq(externalId)).fetchSingle().value1()!!

    private fun cryptoId(externalId: UUID): Long =
        dsl.select(CRYPTO_HOLDINGS.ID).from(CRYPTO_HOLDINGS).where(CRYPTO_HOLDINGS.EXTERNAL_ID.eq(externalId)).fetchSingle().value1()!!

    private fun cents(value: BigDecimal): BigDecimal = value.setScale(2, RoundingMode.HALF_UP)

    @Test
    @Order(1)
    fun `without snapshots or movement this month the month summary is empty`() {
        val month = month()

        assertEquals(currentMonth.toString(), month.month)
        assertEquals(BigDecimal("0.00"), cents(month.contributions))
        assertEquals(BigDecimal("0.00"), cents(month.realizedProfit))
        assertNull(month.currentResultChange)
        assertNull(month.bestMover)
        assertNull(month.worstMover)
        assertTrue(series().none { point -> point.marketValued })
    }

    @Test
    @Order(2)
    fun `with last month's snapshots the change and the movers are measured from them`() {
        recordWalletSnapshot(stocksWalletId, gain = "5")
        recordWalletSnapshot(cryptoWalletId, gain = "2")
        recordHoldingSnapshot(WalletKind.STOCKS, stockId(risingStockId), stocksWalletId, price = "10")
        recordHoldingSnapshot(WalletKind.STOCKS, stockId(fallingStockId), stocksWalletId, price = "10")
        recordHoldingSnapshot(WalletKind.CRYPTO, cryptoId(cryptoHoldingId), cryptoWalletId, price = "100")

        val month = month()

        assertEquals(BigDecimal("3.00"), cents(month.currentResultChange!!))
        assertEquals("RISE3", month.bestMover!!.ticker)
        assertEquals("STOCKS", month.bestMover!!.kind)
        assertEquals(BigDecimal("20.00"), cents(month.bestMover!!.changePct))
        assertEquals("FALL3", month.worstMover!!.ticker)
        assertEquals(BigDecimal("-20.00"), cents(month.worstMover!!.changePct))

        val previousMonth = series().single { point -> point.month == YearMonth.from(lastMonthEnd).toString() }
        assertTrue(previousMonth.marketValued)
        assertEquals(BigDecimal("7.00"), cents(previousMonth.result))
    }

    @Test
    @Order(3)
    fun `this month's contributions and realized profit come from this month's movements`() {
        restTestClient.post()
            .uri("/private/v1/wallets/$stocksWalletId/stock-holdings/$risingStockId/withdrawals")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"resultDate":"$today","quantity":2,"unitPrice":12}""")
            .exchange()
            .expectStatus().isCreated()
        restTestClient.post()
            .uri("/private/v1/wallets/$cryptoWalletId/crypto-holdings/$cryptoHoldingId/lots")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"lotDate":"$today","quantity":0.1,"price":110}""")
            .exchange()
            .expectStatus().isCreated()

        val month = month()

        assertEquals(BigDecimal("11.00"), cents(month.contributions))
        assertEquals(BigDecimal("4.00"), cents(month.realizedProfit))
    }
}
