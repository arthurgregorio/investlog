package br.com.investlog.server.investmentreport

import br.com.investlog.server.holdingsoverview.rest.payloads.HoldingRowResponse
import br.com.investlog.server.investmentreport.services.InvestmentReport
import br.com.investlog.server.investmentreport.services.InvestmentReportService
import br.com.investlog.server.investmentreport.services.InvestmentReportTypeGroup
import br.com.investlog.server.wallets.rest.payloads.WalletKind
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.ZonedDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InvestmentReportServiceTest {

    private val generatedAt = ZonedDateTime.parse("2026-10-22T23:12:00-03:00[America/Sao_Paulo]")

    private val brlOnly = mapOf("BRL" to BigDecimal.ONE)

    private fun row(
        kind: WalletKind = WalletKind.STOCKS,
        ticker: String? = "PETR4",
        name: String = "Petrobras PN",
        typeLabel: String? = "Ações",
        segmentLabel: String? = null,
        walletCurrency: String = "BRL",
        quantity: String? = "10",
        costBasis: String = "100",
        currentPrice: String? = "12",
        currentValue: String? = "120",
        frozen: Boolean = false,
    ) = HoldingRowResponse(
        id = UUID.randomUUID(),
        kind = kind.text,
        name = name,
        ticker = ticker,
        typeLabel = typeLabel,
        segmentLabel = segmentLabel,
        walletId = UUID.randomUUID(),
        walletName = "Carteira",
        walletCurrency = walletCurrency,
        quantity = quantity?.let(::BigDecimal),
        costBasis = BigDecimal(costBasis),
        currentPrice = currentPrice?.let(::BigDecimal),
        currentValue = currentValue?.let(::BigDecimal),
        frozen = frozen,
        gain = null,
        gainPct = null,
    )

    private fun consolidate(
        rows: List<HoldingRowResponse>,
        ratesByCurrency: Map<String, BigDecimal> = brlOnly,
        displayCurrency: String = "BRL",
    ): InvestmentReport = InvestmentReportService.consolidate(rows, ratesByCurrency, displayCurrency, "Marina Duarte", generatedAt)

    private fun assertAmount(expected: String, actual: BigDecimal?) {
        assertEquals(0, BigDecimal(expected).compareTo(actual), "expected $expected but was $actual")
    }

    private fun assertRounded(expected: String, actual: BigDecimal?) {
        assertAmount(expected, actual?.setScale(4, RoundingMode.HALF_UP))
    }

    @Test
    fun `merges the same ticker across wallets ignoring its case`() {
        val report = consolidate(
            listOf(
                row(kind = WalletKind.CRYPTO, ticker = "BTC", name = "Bitcoin", typeLabel = null, quantity = "0.1", costBasis = "50000", currentValue = "45000"),
                row(kind = WalletKind.CRYPTO, ticker = "btc", name = "Bitcoin", typeLabel = null, quantity = "0.073", costBasis = "35000", currentValue = "31000"),
            )
        )

        val position = report.sections.single().typeGroups.single().positions.single()

        assertEquals("BTC", position.ticker)
        assertAmount("0.173", position.quantity)
        assertAmount("85000", position.costBasis)
        assertAmount("76000", position.currentValue)
        assertAmount("-9000", position.result)
        assertRounded("439306.3584", position.currentPrice)
        assertRounded("491329.4798", position.averagePrice)
        assertRounded("-10.5882", position.resultPercentage)
        assertEquals(1, report.totals.positionCount)
    }

    @Test
    fun `keeps the same ticker apart when its type differs`() {
        val report = consolidate(
            listOf(
                row(ticker = "AAPL", typeLabel = "Ações"),
                row(ticker = "AAPL", typeLabel = "BDR"),
            )
        )

        val typeGroups = report.sections.single().typeGroups

        assertEquals(listOf("Ações", "BDR"), typeGroups.map { typeGroup -> typeGroup.label })
        assertEquals(2, report.totals.positionCount)
    }

    @Test
    fun `merges funds by name and ignores their ticker`() {
        val report = consolidate(
            listOf(
                row(kind = WalletKind.FUNDS, ticker = null, name = "Oak FI RF CP", typeLabel = "Renda Fixa", quantity = null, currentPrice = null, costBasis = "1000", currentValue = "1100"),
                row(kind = WalletKind.FUNDS, ticker = "OAK", name = "Oak FI RF CP", typeLabel = "Renda Fixa", quantity = null, currentPrice = null, costBasis = "500", currentValue = "550"),
                row(kind = WalletKind.FUNDS, ticker = null, name = "Lagoa FIF Multimercado", typeLabel = "Renda Fixa", quantity = null, currentPrice = null),
            )
        )

        val positions = report.sections.single().typeGroups.single().positions

        assertEquals(listOf("Lagoa FIF Multimercado", "Oak FI RF CP"), positions.map { position -> position.name })
        assertAmount("1500", positions[1].costBasis)
        assertAmount("1650", positions[1].currentValue)
        assertNull(positions[1].quantity)
        assertNull(positions[1].currentPrice)
        assertNull(positions[1].averagePrice)
        assertEquals("OAK", positions[1].ticker)
    }

    @Test
    fun `takes the name and segment from the first row that has one and freezes when any part is frozen`() {
        val report = consolidate(
            listOf(
                row(name = " ", segmentLabel = null),
                row(name = "Petrobras PN", segmentLabel = "Energia", frozen = true),
                row(name = "Petróleo Brasileiro", segmentLabel = "Petróleo"),
            )
        )

        val position = report.sections.single().typeGroups.single().positions.single()

        assertEquals("Petrobras PN", position.name)
        assertEquals("Energia", position.segment)
        assertTrue(position.frozen)
        assertAmount("30", position.quantity)
    }

    @Test
    fun `keeps a blank name when no part has one`() {
        val position = consolidate(listOf(row(name = ""))).sections.single().typeGroups.single().positions.single()

        assertEquals("", position.name)
    }

    @Test
    fun `converts every amount by the wallet rate over the display currency rate`() {
        val rates = mapOf("BRL" to BigDecimal.ONE, "USD" to BigDecimal("5.18"))

        val report = consolidate(
            listOf(
                row(ticker = "AAPL", walletCurrency = "USD", quantity = "2", costBasis = "100", currentPrice = "60", currentValue = "120"),
                row(ticker = "AAPL", walletCurrency = "BRL", quantity = "1", costBasis = "300", currentPrice = "320", currentValue = "320"),
            ),
            rates,
        )

        val position = report.sections.single().typeGroups.single().positions.single()

        assertAmount("818", position.costBasis)
        assertAmount("941.6", position.currentValue)
        assertRounded("313.8667", position.currentPrice)
        assertRounded("272.6667", position.averagePrice)
        assertEquals(listOf("USD"), report.exchangeRates.map { exchangeRate -> exchangeRate.currency })
        assertAmount("5.18", report.exchangeRates.single().rate)
    }

    @Test
    fun `converts into a display currency that is not the anchor`() {
        val rates = mapOf("BRL" to BigDecimal.ONE, "USD" to BigDecimal("5"))

        val report = consolidate(
            listOf(
                row(ticker = "AAPL", walletCurrency = "USD", costBasis = "100", currentValue = "120"),
                row(ticker = "PETR4", walletCurrency = "BRL", costBasis = "500", currentValue = "600"),
            ),
            rates,
            displayCurrency = "USD",
        )

        assertAmount("200", report.totals.costBasis)
        assertAmount("240", report.totals.currentValue)
        assertEquals("USD", report.displayCurrency)
        assertEquals(listOf("BRL"), report.exchangeRates.map { exchangeRate -> exchangeRate.currency })
        assertAmount("0.2", report.exchangeRates.single().rate)
    }

    @Test
    fun `treats a currency without a stored rate as the anchor`() {
        val report = consolidate(listOf(row(walletCurrency = "USD", costBasis = "100", currentValue = "100")), ratesByCurrency = emptyMap())

        assertAmount("100", report.totals.costBasis)
        assertAmount("1", report.exchangeRates.single().rate)
    }

    @Test
    fun `an unpriced row has no value or result while the totals count it as zero`() {
        val report = consolidate(
            listOf(
                row(ticker = "UNPR3", costBasis = "100", currentPrice = null, currentValue = null),
                row(ticker = "PRIC3", costBasis = "100", currentValue = "150"),
            )
        )

        val positions = report.sections.single().typeGroups.single().positions
        val unpriced = positions.single { position -> position.ticker == "UNPR3" }

        assertNull(unpriced.currentValue)
        assertNull(unpriced.currentPrice)
        assertNull(unpriced.result)
        assertNull(unpriced.resultPercentage)
        assertAmount("200", report.totals.costBasis)
        assertAmount("150", report.totals.currentValue)
        assertAmount("-50", report.totals.result)
        assertAmount("-25", report.totals.resultPercentage)
    }

    @Test
    fun `a merged row with one priced part sums the priced value only`() {
        val position = consolidate(
            listOf(
                row(quantity = "10", costBasis = "100", currentValue = null),
                row(quantity = "10", costBasis = "100", currentValue = "150"),
            )
        ).sections.single().typeGroups.single().positions.single()

        assertAmount("150", position.currentValue)
        assertAmount("7.5", position.currentPrice)
    }

    @Test
    fun `a position with zero quantity has no current or average price`() {
        val position = consolidate(listOf(row(quantity = "0", currentValue = "10"))).sections.single().typeGroups.single().positions.single()

        assertNull(position.currentPrice)
        assertNull(position.averagePrice)
    }

    @Test
    fun `orders classes as stocks, crypto, funds and leaves out a class with no rows`() {
        val report = consolidate(
            listOf(
                row(kind = WalletKind.FUNDS, ticker = null, name = "Fundo", typeLabel = "Renda Fixa", quantity = null),
                row(kind = WalletKind.STOCKS),
            )
        )

        assertEquals(listOf(WalletKind.STOCKS, WalletKind.FUNDS), report.sections.map { section -> section.kind })
    }

    @Test
    fun `orders types and positions in pt-BR alphabetical order with untyped rows as Outros`() {
        val report = consolidate(
            listOf(
                row(ticker = "VALE3", typeLabel = "Renda Variável"),
                row(ticker = "XPTO3", typeLabel = null),
                row(ticker = "ITUB4", typeLabel = "Ações"),
                row(ticker = "ABEV3", typeLabel = "Ações"),
                row(ticker = "RECR11", typeLabel = "Imobiliário"),
                row(kind = WalletKind.FUNDS, ticker = null, name = "Banco FIF", typeLabel = "Ações", quantity = null),
                row(kind = WalletKind.FUNDS, ticker = null, name = "Ágora FIF", typeLabel = "Ações", quantity = null),
            )
        )

        val stocks = report.sections.first()
        val funds = report.sections.last()

        assertEquals(listOf("Ações", "Imobiliário", InvestmentReportTypeGroup.UNTYPED_LABEL, "Renda Variável"), stocks.typeGroups.map { typeGroup -> typeGroup.label })
        assertEquals(listOf("ABEV3", "ITUB4"), stocks.typeGroups.first().positions.map { position -> position.ticker })
        assertEquals(listOf("Ágora FIF", "Banco FIF"), funds.typeGroups.single().positions.map { position -> position.name })
        assertTrue(stocks.showsTypeGroups)
    }

    @Test
    fun `a class whose rows carry no type shows no type groups`() {
        val report = consolidate(
            listOf(
                row(kind = WalletKind.CRYPTO, ticker = "ETH", name = "Ethereum", typeLabel = null),
                row(kind = WalletKind.CRYPTO, ticker = "BTC", name = "Bitcoin", typeLabel = null),
            )
        )

        val crypto = report.sections.single()

        assertFalse(crypto.showsTypeGroups)
        assertEquals(listOf("BTC", "ETH"), crypto.typeGroups.single().positions.map { position -> position.ticker })
    }

    @Test
    fun `carries counts, sums, results and shares for every type, class and the report`() {
        val report = consolidate(
            listOf(
                row(ticker = "ITUB4", typeLabel = "Ações", costBasis = "100", currentValue = "150"),
                row(ticker = "ABEV3", typeLabel = "Ações", costBasis = "100", currentValue = "50"),
                row(ticker = "HGLG11", typeLabel = "FII", costBasis = "200", currentValue = "200"),
                row(kind = WalletKind.CRYPTO, ticker = "BTC", typeLabel = null, costBasis = "400", currentValue = "600"),
            )
        )

        val stocks = report.sections.first()
        val shares = stocks.typeGroups.first().totals

        assertEquals(2, shares.positionCount)
        assertAmount("200", shares.costBasis)
        assertAmount("200", shares.currentValue)
        assertAmount("0", shares.result)
        assertAmount("0", shares.resultPercentage)
        assertAmount("20", shares.share)

        assertEquals(3, stocks.totals.positionCount)
        assertAmount("400", stocks.totals.costBasis)
        assertAmount("400", stocks.totals.currentValue)
        assertAmount("40", stocks.totals.share)

        assertEquals(4, report.totals.positionCount)
        assertAmount("800", report.totals.costBasis)
        assertAmount("1000", report.totals.currentValue)
        assertAmount("200", report.totals.result)
        assertAmount("25", report.totals.resultPercentage)
        assertAmount("100", report.totals.share)
        assertEquals("Marina Duarte", report.holderName)
        assertEquals(generatedAt, report.generatedAt)
    }

    @Test
    fun `leaves the percentage and the share empty when their base is zero`() {
        val report = consolidate(listOf(row(costBasis = "0", currentValue = null)))

        assertNull(report.totals.resultPercentage)
        assertNull(report.totals.share)
        assertAmount("0", report.totals.result)
    }
}
