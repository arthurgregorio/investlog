package br.com.investlog.server.reinvestments

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.cryptoholdings.rest.payloads.CryptoHoldingResponse
import br.com.investlog.server.fundholdings.rest.payloads.FundHoldingResponse
import br.com.investlog.server.jooq.finances.enums.HoldingStatus
import br.com.investlog.server.jooq.finances.enums.ResultType
import br.com.investlog.server.jooq.finances.tables.references.CRYPTO_LOTS
import br.com.investlog.server.jooq.finances.tables.references.FUND_CONTRIBUTIONS
import br.com.investlog.server.jooq.finances.tables.references.FUND_HOLDINGS
import br.com.investlog.server.jooq.finances.tables.references.REINVESTMENTS
import br.com.investlog.server.jooq.finances.tables.references.RESULTS
import br.com.investlog.server.jooq.finances.tables.references.STOCK_HOLDINGS
import br.com.investlog.server.jooq.finances.tables.references.STOCK_LOTS
import br.com.investlog.server.stockholdings.rest.payloads.StockHoldingResponse
import br.com.investlog.server.typelists.rest.payloads.TypeResponse
import br.com.investlog.server.wallets.rest.payloads.WalletResponse
import org.jooq.DSLContext
import org.jooq.Table
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

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReinvestmentControllerTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTestClient: RestTestClient

    @Autowired
    lateinit var dsl: DSLContext

    lateinit var stocksWalletId: UUID
    lateinit var otherStocksWalletId: UUID
    lateinit var dollarStocksWalletId: UUID
    lateinit var cryptoWalletId: UUID
    lateinit var fundsWalletId: UUID
    lateinit var stockTypeId: UUID
    lateinit var fundTypeId: UUID
    lateinit var treasuryFundId: UUID

    @BeforeAll
    fun setup() {
        stocksWalletId = createWallet("Stocks Wallet", "stocks")
        otherStocksWalletId = createWallet("Other Stocks Wallet", "stocks")
        dollarStocksWalletId = createWallet("Dollar Stocks Wallet", "stocks", "USD")
        cryptoWalletId = createWallet("Crypto Wallet", "crypto")
        fundsWalletId = createWallet("Funds Wallet", "funds")

        stockTypeId = createType("/private/v1/stock-types", "Ação Reinvestimento")
        fundTypeId = createType("/private/v1/fund-types", "Fundo Reinvestimento")

        treasuryFundId = createFundHolding("Tesouro Selic", "1000", "1000")
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

    private fun createStockHolding(
        walletId: UUID,
        ticker: String,
        quantity: String,
        price: String,
        currentPrice: String?,
    ): UUID = restTestClient.post()
        .uri("/private/v1/wallets/$walletId/stock-holdings")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            """
            {
              "stockTypeId":"$stockTypeId",
              "ticker":"$ticker",
              "name":"Holding $ticker",
              "currentPrice":$currentPrice,
              "lot":{"lotDate":"2024-01-15","quantity":$quantity,"price":$price}
            }
            """.trimIndent()
        )
        .exchange()
        .expectStatus().isCreated()
        .returnResult<StockHoldingResponse>()
        .responseBody!!
        .id

    private fun createCryptoHolding(ticker: String, quantity: String, price: String, currentPrice: String?): UUID =
        restTestClient.post()
            .uri("/private/v1/wallets/$cryptoWalletId/crypto-holdings")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                """
                {
                  "ticker":"$ticker",
                  "name":"Holding $ticker",
                  "currentPrice":$currentPrice,
                  "lot":{"lotDate":"2024-01-15","quantity":$quantity,"price":$price}
                }
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isCreated()
            .returnResult<CryptoHoldingResponse>()
            .responseBody!!
            .id

    private fun createFundHolding(name: String, contribution: String, currentValue: String): UUID =
        restTestClient.post()
            .uri("/private/v1/wallets/$fundsWalletId/fund-holdings")
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

    private fun reinvest(body: String) = restTestClient.post()
        .uri("/private/v1/reinvestments")
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .exchange()

    private fun stockReinvestment(
        sourceId: UUID,
        destinationKind: String,
        destinationId: UUID,
        quantity: String,
        unitPrice: String,
        extra: String = "",
        date: String = "2026-09-30",
    ) = reinvest(
        """
        {
          "sourceKind":"STOCKS","sourceHoldingId":"$sourceId",
          "destinationKind":"$destinationKind","destinationHoldingId":"$destinationId",
          "reinvestmentDate":"$date","quantity":$quantity,"unitPrice":$unitPrice$extra
        }
        """.trimIndent()
    )

    private fun countRows(table: Table<*>): Int = dsl.fetchCount(dsl.selectFrom(table))

    private fun stockHoldingInternalId(externalId: UUID): Long =
        dsl.select(STOCK_HOLDINGS.ID).from(STOCK_HOLDINGS).where(STOCK_HOLDINGS.EXTERNAL_ID.eq(externalId)).fetchSingle(STOCK_HOLDINGS.ID)!!

    private fun fundHoldingInternalId(externalId: UUID): Long =
        dsl.select(FUND_HOLDINGS.ID).from(FUND_HOLDINGS).where(FUND_HOLDINGS.EXTERNAL_ID.eq(externalId)).fetchSingle(FUND_HOLDINGS.ID)!!

    private fun latestReinvestment() =
        dsl.selectFrom(REINVESTMENTS).orderBy(REINVESTMENTS.ID.desc()).limit(1).fetchSingle()

    private fun assertAmount(expected: String, actual: BigDecimal?) {
        assertNotNull(actual)
        assertEquals(
            BigDecimal(expected).setScale(8, RoundingMode.HALF_UP),
            actual.setScale(8, RoundingMode.HALF_UP),
        )
    }

    private fun assertNothingWritten(action: () -> Unit) {
        val resultsBefore = countRows(RESULTS)
        val reinvestmentsBefore = countRows(REINVESTMENTS)
        val stockLotsBefore = countRows(STOCK_LOTS)
        val cryptoLotsBefore = countRows(CRYPTO_LOTS)
        val contributionsBefore = countRows(FUND_CONTRIBUTIONS)

        action()

        assertEquals(resultsBefore, countRows(RESULTS))
        assertEquals(reinvestmentsBefore, countRows(REINVESTMENTS))
        assertEquals(stockLotsBefore, countRows(STOCK_LOTS))
        assertEquals(cryptoLotsBefore, countRows(CRYPTO_LOTS))
        assertEquals(contributionsBefore, countRows(FUND_CONTRIBUTIONS))
    }

    @Test
    @Order(1)
    fun `reinvesting part of a stock into a fund reduces the stock and contributes the net amount`() {

        val petrobrasId = createStockHolding(stocksWalletId, "PETR4", "100", "35.00", "38.50")

        stockReinvestment(
            petrobrasId, "FUNDS", treasuryFundId, "40", "50.00",
            extra = ""","fees":10,"taxes":90""",
        ).expectStatus().isCreated()

        restTestClient.get()
            .uri("/private/v1/holdings?walletId=$stocksWalletId&search=PETR4")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.page.totalElements").isEqualTo(1)
            .jsonPath("$.content[0].quantity").isEqualTo(60)

        val result = dsl.selectFrom(RESULTS).orderBy(RESULTS.ID.desc()).limit(1).fetchSingle()
        assertEquals(ResultType.REINVESTMENT, result.resultType)
        assertAmount("2000", result.grossAmount)
        assertAmount("1400", result.costBasis)
        assertAmount("1900", result.netAmount)

        val reinvestment = latestReinvestment()
        assertEquals(result.id, reinvestment.resultId)
        assertEquals(fundHoldingInternalId(treasuryFundId), reinvestment.destinationFundHoldingId)
        assertAmount("1900", reinvestment.amount)

        val contributions = dsl.selectFrom(FUND_CONTRIBUTIONS)
            .where(FUND_CONTRIBUTIONS.FUND_HOLDING_ID.eq(fundHoldingInternalId(treasuryFundId)))
            .orderBy(FUND_CONTRIBUTIONS.ID)
            .fetch()
        assertEquals(2, contributions.size)
        assertAmount("1900", contributions.last().amount)

        val fund = dsl.selectFrom(FUND_HOLDINGS).where(FUND_HOLDINGS.EXTERNAL_ID.eq(treasuryFundId)).fetchSingle()
        assertAmount("2900", fund.currentValue)
    }

    @Test
    @Order(2)
    fun `reinvesting the whole remaining position completes the source and drops it from overview`() {

        val holdingId = createStockHolding(stocksWalletId, "WEGE3", "10", "30.00", "40.00")

        stockReinvestment(holdingId, "FUNDS", treasuryFundId, "10", "40.00").expectStatus().isCreated()

        restTestClient.get()
            .uri("/private/v1/holdings?walletId=$stocksWalletId&search=WEGE3")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.page.totalElements").isEqualTo(0)

        val status = dsl.select(STOCK_HOLDINGS.STATUS).from(STOCK_HOLDINGS)
            .where(STOCK_HOLDINGS.EXTERNAL_ID.eq(holdingId))
            .fetchSingle(STOCK_HOLDINGS.STATUS)
        assertEquals(HoldingStatus.COMPLETED, status)
    }

    @Test
    @Order(3)
    fun `reinvesting into crypto buys at its current price and, with no fees or taxes, moves the full gross`() {

        val valeId = createStockHolding(stocksWalletId, "VALE3", "10", "20.00", "25.00")
        val bitcoinId = createCryptoHolding("BTC", "1", "80", "100")

        stockReinvestment(valeId, "CRYPTO", bitcoinId, "10", "25.00").expectStatus().isCreated()

        val lot = dsl.selectFrom(CRYPTO_LOTS).orderBy(CRYPTO_LOTS.ID.desc()).limit(1).fetchSingle()
        assertAmount("2.5", lot.quantity)
        assertAmount("100", lot.price)
        assertAmount("250", latestReinvestment().amount)
    }

    @Test
    @Order(4)
    fun `a stock or crypto destination without a current price is rejected and writes nothing`() {

        val sourceId = createStockHolding(stocksWalletId, "ABEV3", "10", "10.00", "12.00")
        val unpricedId = createCryptoHolding("ETH", "1", "50", null)

        assertNothingWritten {
            stockReinvestment(sourceId, "CRYPTO", unpricedId, "5", "12.00").expectStatus().isBadRequest()
        }
    }

    @Test
    @Order(5)
    fun `a reinvestment between wallets of different currencies is rejected and writes nothing`() {

        val sourceId = createStockHolding(stocksWalletId, "BBDC4", "10", "10.00", "12.00")
        val dollarId = createStockHolding(dollarStocksWalletId, "AAPL", "1", "150.00", "180.00")

        assertNothingWritten {
            stockReinvestment(sourceId, "STOCKS", dollarId, "5", "12.00").expectStatus().isBadRequest()
        }
    }

    @Test
    @Order(6)
    fun `reinvesting a holding into itself is rejected`() {

        val holdingId = createStockHolding(stocksWalletId, "SUZB3", "10", "10.00", "12.00")

        assertNothingWritten {
            stockReinvestment(holdingId, "STOCKS", holdingId, "5", "12.00").expectStatus().isBadRequest()
        }
    }

    @Test
    @Order(7)
    fun `reinvesting more than the remaining position is rejected`() {

        val holdingId = createStockHolding(stocksWalletId, "GGBR4", "10", "10.00", "12.00")
        val fundId = createFundHolding("Fundo DI", "100", "100")

        assertNothingWritten {
            stockReinvestment(holdingId, "FUNDS", fundId, "11", "12.00").expectStatus().isBadRequest()
            reinvest(
                """
                {
                  "sourceKind":"FUNDS","sourceHoldingId":"$fundId",
                  "destinationKind":"FUNDS","destinationHoldingId":"$treasuryFundId",
                  "reinvestmentDate":"2026-09-30","amount":101
                }
                """.trimIndent()
            ).expectStatus().isBadRequest()
        }
    }

    @Test
    @Order(8)
    fun `fees and taxes consuming the whole gross amount are rejected`() {

        val holdingId = createStockHolding(stocksWalletId, "EMBR3", "10", "10.00", "12.00")

        assertNothingWritten {
            stockReinvestment(
                holdingId, "FUNDS", treasuryFundId, "1", "12.00",
                extra = ""","fees":10,"taxes":2""",
            ).expectStatus().isBadRequest()
        }
    }

    @Test
    @Order(9)
    fun `a stock source without a unit price is rejected`() {

        val holdingId = createStockHolding(stocksWalletId, "RADL3", "10", "10.00", "12.00")

        assertNothingWritten {
            reinvest(
                """
                {
                  "sourceKind":"STOCKS","sourceHoldingId":"$holdingId",
                  "destinationKind":"FUNDS","destinationHoldingId":"$treasuryFundId",
                  "reinvestmentDate":"2026-09-30","quantity":1
                }
                """.trimIndent()
            ).expectStatus().isBadRequest()
        }
    }

    @Test
    @Order(10)
    fun `a fund source reinvests an amount and reduces its current value`() {

        val fundId = createFundHolding("Fundo Multimercado", "1000", "1200")
        val itauId = createStockHolding(stocksWalletId, "ITSA4", "10", "10.00", "10.00")

        reinvest(
            """
            {
              "sourceKind":"FUNDS","sourceHoldingId":"$fundId",
              "destinationKind":"STOCKS","destinationHoldingId":"$itauId",
              "reinvestmentDate":"2026-09-30","amount":600,"taxes":100
            }
            """.trimIndent()
        ).expectStatus().isCreated()

        val fund = dsl.selectFrom(FUND_HOLDINGS).where(FUND_HOLDINGS.EXTERNAL_ID.eq(fundId)).fetchSingle()
        assertAmount("600", fund.currentValue)

        val lot = dsl.selectFrom(STOCK_LOTS).orderBy(STOCK_LOTS.ID.desc()).limit(1).fetchSingle()
        assertEquals(stockHoldingInternalId(itauId), lot.stockHoldingId)
        assertAmount("50", lot.quantity)
        assertAmount("10", lot.price)
    }

    @Test
    @Order(11)
    fun `the history lists reinvestments newest first with both sides labelled`() {

        val holdingId = createStockHolding(stocksWalletId, "TAEE11", "10", "30.00", "35.00")

        stockReinvestment(holdingId, "FUNDS", treasuryFundId, "2", "35.00", date = "2026-10-01")
            .expectStatus().isCreated()

        restTestClient.get()
            .uri("/private/v1/reinvestments?size=100")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.page.totalElements").isEqualTo(countRows(REINVESTMENTS))
            .jsonPath("$.content[0].reinvestmentDate").isEqualTo("2026-10-01")
            .jsonPath("$.content[0].currency").isEqualTo("BRL")
            .jsonPath("$.content[0].source.kind").isEqualTo("STOCKS")
            .jsonPath("$.content[0].source.ticker").isEqualTo("TAEE11")
            .jsonPath("$.content[0].source.walletName").isEqualTo("Stocks Wallet")
            .jsonPath("$.content[0].destination.kind").isEqualTo("FUNDS")
            .jsonPath("$.content[0].destination.name").isEqualTo("Tesouro Selic")
            .jsonPath("$.content[0].destination.walletName").isEqualTo("Funds Wallet")
            .jsonPath("$.content[0].quantity").isEqualTo(2)
            .jsonPath("$.content[0].grossAmount").isEqualTo(70.0)
            .jsonPath("$.content[0].amount").isEqualTo(70.0)
            .jsonPath("$.content[0].profit").isEqualTo(10.0)
    }

    @Test
    @Order(12)
    fun `a reinvestment shows on the source's ledger and cannot be undone as a withdrawal`() {

        val holdingId = createStockHolding(stocksWalletId, "CMIG4", "10", "10.00", "12.00")

        stockReinvestment(holdingId, "FUNDS", treasuryFundId, "4", "12.00").expectStatus().isCreated()

        val resultId = dsl.selectFrom(RESULTS).orderBy(RESULTS.ID.desc()).limit(1).fetchSingle().externalId!!

        restTestClient.get()
            .uri("/private/v1/wallets/$stocksWalletId/stock-holdings/$holdingId")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.withdrawals[0].resultType").isEqualTo("REINVESTMENT")

        assertNothingWritten {
            restTestClient.delete()
                .uri("/private/v1/wallets/$stocksWalletId/stock-holdings/$holdingId/withdrawals/$resultId")
                .exchange()
                .expectStatus().isEqualTo(409)
        }
    }

    @Test
    @Order(13)
    fun `merging a reinvestment destination into another wallet keeps the reinvestment linked`() {

        val sourceId = createStockHolding(stocksWalletId, "KLBN11", "10", "20.00", "20.00")
        val destinationId = createStockHolding(stocksWalletId, "SANB11", "10", "30.00", "30.00")
        val matchId = createStockHolding(otherStocksWalletId, "SANB11", "5", "28.00", "30.00")

        stockReinvestment(sourceId, "STOCKS", destinationId, "3", "20.00").expectStatus().isCreated()

        val reinvestmentId = latestReinvestment().id

        restTestClient.post()
            .uri("/private/v1/wallets/$stocksWalletId/moves")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"destinationWalletId":"$otherStocksWalletId","items":[{"holdingId":"$destinationId"}]}""")
            .exchange()
            .expectStatus().is2xxSuccessful()

        val destinationStillExists = dsl.fetchExists(
            dsl.selectFrom(STOCK_HOLDINGS).where(STOCK_HOLDINGS.EXTERNAL_ID.eq(destinationId))
        )
        assertEquals(false, destinationStillExists)

        val reinvestment = dsl.selectFrom(REINVESTMENTS).where(REINVESTMENTS.ID.eq(reinvestmentId)).fetchSingle()
        assertEquals(stockHoldingInternalId(matchId), reinvestment.destinationStockHoldingId)
    }

    private fun freeze(uri: String) {
        restTestClient.patch()
            .uri(uri)
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"frozen":true}""")
            .exchange()
            .expectStatus().isOk()
    }

    @Test
    @Order(14)
    fun `reinvesting into a frozen stock is rejected with 409 and writes nothing`() {

        val sourceId = createStockHolding(stocksWalletId, "CMIG4", "10", "10.00", "12.00")
        val frozenId = createStockHolding(stocksWalletId, "CPLE6", "10", "10.00", "12.00")
        freeze("/private/v1/wallets/$stocksWalletId/stock-holdings/$frozenId")

        assertNothingWritten {
            stockReinvestment(sourceId, "STOCKS", frozenId, "5", "12.00").expectStatus().isEqualTo(409)
        }

        restTestClient.get()
            .uri("/private/v1/holdings?walletId=$stocksWalletId&search=CMIG4")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content[0].quantity").isEqualTo(10)
    }

    @Test
    @Order(15)
    fun `reinvesting into a frozen fund is rejected with 409 and writes nothing`() {

        val sourceId = createStockHolding(stocksWalletId, "TAEE11", "10", "10.00", "12.00")
        val frozenFundId = createFundHolding("Fundo Congelado", "100", "100")
        freeze("/private/v1/wallets/$fundsWalletId/fund-holdings/$frozenFundId")

        assertNothingWritten {
            stockReinvestment(sourceId, "FUNDS", frozenFundId, "5", "12.00").expectStatus().isEqualTo(409)
        }
    }

    @Test
    @Order(16)
    fun `a frozen holding can still be the source of a reinvestment`() {

        val frozenSourceId = createStockHolding(stocksWalletId, "EGIE3", "10", "10.00", "12.00")
        val destinationId = createStockHolding(stocksWalletId, "CSMG3", "10", "10.00", "12.00")
        freeze("/private/v1/wallets/$stocksWalletId/stock-holdings/$frozenSourceId")

        stockReinvestment(frozenSourceId, "STOCKS", destinationId, "5", "12.00").expectStatus().isCreated()
    }
}
