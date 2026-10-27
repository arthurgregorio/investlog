package br.com.investlog.server.investmentreport

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.investmentreport.services.InvestmentReportService
import br.com.investlog.server.stockholdings.rest.payloads.StockHoldingResponse
import br.com.investlog.server.typelists.rest.payloads.TypeResponse
import br.com.investlog.server.wallets.rest.payloads.WalletResponse
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.returnResult
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InvestmentReportControllerTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTestClient: RestTestClient

    lateinit var stockWalletId: UUID
    lateinit var usdCryptoWalletId: UUID

    private val commaTypeLabel = "Ações, com vírgula"
    private val otherTypeLabel = "Pdf Outro Tipo"

    @BeforeAll
    fun setup() {
        restTestClient.put()
            .uri("/private/v1/currency-rates/USD")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"rate":5.00}""")
            .exchange()
            .expectStatus().isOk()

        val commaTypeId = createType(commaTypeLabel, "STOCKS")
        val otherTypeId = createType(otherTypeLabel, "STOCKS")
        val fundTypeId = createType("Pdf Renda Fixa", "FUNDS")

        stockWalletId = createWallet("Pdf Stocks", "STOCKS", "BRL")
        val brlCryptoWalletId = createWallet("Pdf Crypto BRL", "CRYPTO", "BRL")
        usdCryptoWalletId = createWallet("Pdf Crypto USD", "CRYPTO", "USD")
        val fundWalletId = createWallet("Pdf Funds", "FUNDS", "BRL")

        createStockHolding(commaTypeId, "PDFA3", quantity = "10", price = "45.00", currentPrice = "50.00")
        createStockHolding(otherTypeId, "PDFB3", quantity = "5", price = "18.00", currentPrice = "20.00")
        val frozenHolding = createStockHolding(otherTypeId, "PDFFRZ3", quantity = "1", price = "10.00", currentPrice = "10.00")

        restTestClient.patch()
            .uri("/private/v1/wallets/$stockWalletId/stock-holdings/${frozenHolding.id}")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"frozen":true}""")
            .exchange()
            .expectStatus().isOk()

        createCryptoHolding(brlCryptoWalletId, quantity = "1", price = "80.00", currentPrice = "100.00")
        createCryptoHolding(usdCryptoWalletId, quantity = "2", price = "10.00", currentPrice = "12.00")

        restTestClient.post()
            .uri("/private/v1/wallets/$fundWalletId/fund-holdings")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                """{"typeId":"$fundTypeId","name":"Fundo Pdf Renda","currentValue":1100.00,
                   "contribution":{"contributionDate":"2025-01-10","amount":1000.00}}"""
            )
            .exchange()
            .expectStatus().isCreated()
    }

    private fun createType(name: String, kind: String): UUID = restTestClient.post()
        .uri("/private/v1/holding-types")
        .contentType(MediaType.APPLICATION_JSON)
        .body("""{"name":"$name","kinds":["$kind"]}""")
        .exchange()
        .expectStatus().isCreated()
        .returnResult<TypeResponse>()
        .responseBody!!
        .id

    private fun createWallet(name: String, kind: String, currency: String): UUID = restTestClient.post()
        .uri("/private/v1/wallets")
        .contentType(MediaType.APPLICATION_JSON)
        .body("""{"name":"$name","kind":"$kind","currency":"$currency"}""")
        .exchange()
        .returnResult<WalletResponse>()
        .responseBody!!
        .id

    private fun createStockHolding(typeId: UUID, ticker: String, quantity: String, price: String, currentPrice: String): StockHoldingResponse =
        restTestClient.post()
            .uri("/private/v1/wallets/$stockWalletId/stock-holdings")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                """{"typeId":"$typeId","ticker":"$ticker","name":"Empresa ${ticker.first()}","currentPrice":$currentPrice,
                   "lot":{"lotDate":"2025-01-15","quantity":$quantity,"price":$price}}"""
            )
            .exchange()
            .expectStatus().isCreated()
            .returnResult<StockHoldingResponse>()
            .responseBody!!

    private fun createCryptoHolding(walletId: UUID, quantity: String, price: String, currentPrice: String) {
        restTestClient.post()
            .uri("/private/v1/wallets/$walletId/crypto-holdings")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                """{"ticker":"PDFBTC","name":null,"currentPrice":$currentPrice,
                   "lot":{"lotDate":"2025-01-15","quantity":$quantity,"price":$price}}"""
            )
            .exchange()
            .expectStatus().isCreated()
    }

    private fun pdfText(uri: String, vararg uriVariables: Any): String {
        val pdf = restTestClient.get()
            .uri(uri, *uriVariables)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_PDF)
            .returnResult<ByteArray>()
            .responseBody!!

        return Loader.loadPDF(pdf).use { document -> PDFTextStripper().apply { sortByPosition = true }.getText(document) }
    }

    private fun occurrences(text: String, token: String): Int = Regex("\\b${Regex.escape(token)}\\b").findAll(text).count()

    @Test
    @Order(1)
    fun `GET report pdf downloads an A4 landscape attachment named after the generation date`() {
        val today = LocalDate.now(InvestmentReportService.REPORT_ZONE)

        val pdf = restTestClient.get()
            .uri("/private/v1/holdings/report/pdf")
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_PDF)
            .expectHeader().valueEquals(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"relatorio-investimentos-$today.pdf\"")
            .returnResult<ByteArray>()
            .responseBody!!

        Loader.loadPDF(pdf).use { document ->
            val mediaBox = document.getPage(0).mediaBox
            val text = PDFTextStripper().apply { sortByPosition = true }.getText(document)

            assertEquals(841.89f, mediaBox.width, 1f)
            assertEquals(595.28f, mediaBox.height, 1f)
            assertContains(text, "Resumo da posição consolidada")
            assertContains(text, "Página 1 de ${document.numberOfPages}")
            assertContains(text, "Custódia em Ações")
            assertContains(text, "Custódia em Cripto")
            assertContains(text, "Custódia em Fundos")
            assertContains(text, "Quantidade Preço médio Preço atual")
            assertContains(text, "Total geral 5 ")
            assertFalse(text.contains("Pdf Stocks"))
            assertFalse(text.contains("Pdf Crypto USD"))
        }
    }

    @Test
    @Order(2)
    fun `GET report pdf merges a ticker held in a BRL and a USD wallet into one converted row`() {
        val text = pdfText("/private/v1/holdings/report/pdf")

        assertEquals(1, occurrences(text, "PDFBTC"))
        assertContains(text, "PDFBTC 3 60,00 73,33 180,00 220,00 +40,00 +22,2%")
        assertContains(text, "1 USD = R$ 5,00")
        assertContains(text, "Total Cripto · 1 posição 180,00 220,00 +40,00 +22,2%")
    }

    @Test
    @Order(3)
    fun `GET report pdf groups positions by type with type totals`() {
        val text = pdfText("/private/v1/holdings/report/pdf")

        assertContains(text, "$commaTypeLabel · 1 posição 450,00 500,00 +50,00 +11,1%")
        assertContains(text, "$otherTypeLabel · 2 posições 100,00 110,00 +10,00 +10,0%")
        assertContains(text, "PDFA3 Empresa P — 10 45,00 50,00 450,00 500,00 +50,00 +11,1%")
        assertContains(text, "Pdf Renda Fixa · 1 posição 1.000,00 1.100,00 +100,00 +10,0%")
    }

    @Test
    @Order(4)
    fun `GET report pdf honours the kind filter`() {
        val text = pdfText("/private/v1/holdings/report/pdf?kind=FUNDS")

        assertContains(text, "Fundo Pdf Renda")
        assertEquals(0, occurrences(text, "PDFA3"))
        assertEquals(0, occurrences(text, "PDFBTC"))
        assertFalse(text.contains("Custódia em Ações"))
    }

    @Test
    @Order(5)
    fun `GET report pdf honours a typeLabel that contains a comma, alone or repeated`() {
        val single = pdfText("/private/v1/holdings/report/pdf?typeLabel={typeLabel}", commaTypeLabel)

        assertEquals(1, occurrences(single, "PDFA3"))
        assertEquals(0, occurrences(single, "PDFB3"))

        val repeated = pdfText("/private/v1/holdings/report/pdf?typeLabel={first}&typeLabel={second}", commaTypeLabel, otherTypeLabel)

        assertEquals(1, occurrences(repeated, "PDFA3"))
        assertEquals(1, occurrences(repeated, "PDFB3"))
        assertEquals(0, occurrences(repeated, "PDFBTC"))
    }

    @Test
    @Order(6)
    fun `GET report pdf honours the walletId filter`() {
        val text = pdfText("/private/v1/holdings/report/pdf?walletId={walletId}", usdCryptoWalletId)

        assertContains(text, "PDFBTC 2 50,00 60,00 100,00 120,00 +20,00 +20,0%")
        assertEquals(0, occurrences(text, "PDFA3"))
    }

    @Test
    @Order(7)
    fun `GET report pdf honours the search filter`() {
        val text = pdfText("/private/v1/holdings/report/pdf?search={search}", "pdfb3")

        assertEquals(1, occurrences(text, "PDFB3"))
        assertEquals(0, occurrences(text, "PDFA3"))
        assertEquals(0, occurrences(text, "PDFBTC"))
    }

    @Test
    @Order(8)
    fun `GET report pdf drops frozen positions only when hideFrozen is set`() {
        assertEquals(1, occurrences(pdfText("/private/v1/holdings/report/pdf"), "PDFFRZ3"))

        val text = pdfText("/private/v1/holdings/report/pdf?hideFrozen=true")

        assertEquals(0, occurrences(text, "PDFFRZ3"))
        assertEquals(1, occurrences(text, "PDFB3"))
    }

    @Test
    @Order(9)
    fun `GET report pdf answers 204 when the filters select nothing`() {
        restTestClient.get()
            .uri("/private/v1/holdings/report/pdf?search={search}", "nothing-matches-this")
            .exchange()
            .expectStatus().isNoContent()
            .expectBody().isEmpty()
    }
}
