package br.com.investlog.server.holdingsoverview

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.cryptoholdings.rest.payloads.CryptoHoldingResponse
import br.com.investlog.server.stockholdings.rest.payloads.StockHoldingResponse
import br.com.investlog.server.typelists.rest.payloads.TypeResponse
import br.com.investlog.server.wallets.rest.payloads.WalletResponse
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.returnResult
import java.util.UUID
import kotlin.test.Test

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HoldingsReportControllerTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTestClient: RestTestClient

    lateinit var cryptoWalletId: UUID
    lateinit var otherCryptoWalletId: UUID
    lateinit var stockWalletId: UUID
    lateinit var stockTypeId: UUID
    lateinit var stockHoldingId: UUID
    lateinit var mergedHoldingTicker: String

    @BeforeAll
    fun setup() {
        cryptoWalletId = createWallet("Report Test Crypto Wallet", "CRYPTO")
        otherCryptoWalletId = createWallet("Report Test Other Crypto Wallet", "CRYPTO")
        stockWalletId = createWallet("Report Test Stock Wallet", "STOCKS")

        stockTypeId = restTestClient.post()
            .uri("/private/v1/stock-types")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Report Test Type"}""")
            .exchange()
            .returnResult<TypeResponse>()
            .responseBody!!
            .id

        stockHoldingId = restTestClient.post()
            .uri("/private/v1/wallets/$stockWalletId/stock-holdings")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                """{"stockTypeId":"$stockTypeId","ticker":"RPTST3",
                   "currentPrice":50.00,
                   "lot":{"lotDate":"2025-01-15","quantity":10,"price":45.00}}"""
            )
            .exchange()
            .expectStatus().isCreated()
            .returnResult<StockHoldingResponse>()
            .responseBody!!
            .id

        mergedHoldingTicker = "RPBTC"

        // Two separate holdings, same wallet + ticker: findAllForReport must merge them into one row.
        createCryptoHolding(cryptoWalletId, mergedHoldingTicker, currentPrice = "100.00", quantity = "1", price = "80.00")
        createCryptoHolding(cryptoWalletId, mergedHoldingTicker, currentPrice = "100.00", quantity = "2", price = "90.00")

        // Same ticker, different wallet: must stay a separate row, not merged into the above.
        // Quantity deliberately differs from the merged cryptoWalletId row's sum (3) so the two
        // rows are distinguishable by value, not just by walletId.
        createCryptoHolding(otherCryptoWalletId, mergedHoldingTicker, currentPrice = "100.00", quantity = "5", price = "70.00")
    }

    private fun createWallet(name: String, kind: String): UUID =
        restTestClient.post()
            .uri("/private/v1/wallets")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"$name","kind":"$kind","currency":"BRL"}""")
            .exchange()
            .returnResult<WalletResponse>()
            .responseBody!!
            .id

    private fun createCryptoHolding(walletId: UUID, ticker: String, currentPrice: String, quantity: String, price: String) {
        restTestClient.post()
            .uri("/private/v1/wallets/$walletId/crypto-holdings")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                """{"ticker":"$ticker","name":null,"currentPrice":$currentPrice,
                   "lot":{"lotDate":"2025-01-15","quantity":$quantity,"price":$price}}"""
            )
            .exchange()
            .expectStatus().isCreated()
            .returnResult<CryptoHoldingResponse>()
    }

    @Test
    @Order(1)
    fun `GET holdings report merges same-wallet same-ticker crypto holdings into one row`() {
        // A combined ticker+walletId filter that still matched more than one row would fail this
        // scalar assertion (JsonPath rejects a multi-match here), so a single value proves merge.
        restTestClient.get()
            .uri("/private/v1/holdings/report?walletId={walletId}", cryptoWalletId)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$[?(@.ticker == '$mergedHoldingTicker' && @.walletId == '$cryptoWalletId')].quantity")
            .isEqualTo(3)
            .jsonPath("$[?(@.ticker == '$mergedHoldingTicker' && @.walletId == '$cryptoWalletId')].costBasis")
            .isEqualTo(260.0)
            .jsonPath("$[?(@.ticker == '$mergedHoldingTicker' && @.walletId == '$cryptoWalletId')].currentValue")
            .isEqualTo(300.0)
    }

    @Test
    @Order(2)
    fun `GET holdings report does not merge the same ticker across different wallets`() {
        restTestClient.get()
            .uri("/private/v1/holdings/report?search={ticker}", mergedHoldingTicker)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$[?(@.ticker == '$mergedHoldingTicker' && @.walletId == '$cryptoWalletId')].quantity")
            .isEqualTo(3)
            .jsonPath("$[?(@.ticker == '$mergedHoldingTicker' && @.walletId == '$otherCryptoWalletId')].quantity")
            .isEqualTo(5)
    }

    @Test
    @Order(3)
    fun `GET holdings report passes stock holdings through unaffected`() {
        restTestClient.get()
            .uri("/private/v1/holdings/report?walletId={walletId}", stockWalletId)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$[?(@.id == '$stockHoldingId')].ticker").isEqualTo("RPTST3")
            .jsonPath("$[?(@.id == '$stockHoldingId')].costBasis").isEqualTo(450.0)
            .jsonPath("$[?(@.id == '$stockHoldingId')].currentValue").isEqualTo(500.0)
    }

    @Test
    @Order(4)
    fun `GET holdings report respects the kind filter`() {
        restTestClient.get()
            .uri("/private/v1/holdings/report?kind=FUNDS&walletId={walletId}", stockWalletId)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$[?(@.id == '$stockHoldingId')]").isEmpty()
    }

    @Test
    @Order(5)
    fun `GET holdings report respects the typeLabel filter`() {
        restTestClient.get()
            .uri("/private/v1/holdings/report?typeLabel={typeLabel}", "Report Test Type")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$[?(@.id == '$stockHoldingId')]").isNotEmpty()
    }

    @Test
    @Order(6)
    fun `GET holdings report returns a plain array with no pagination envelope`() {
        restTestClient.get()
            .uri("/private/v1/holdings/report?walletId={walletId}", stockWalletId)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content").doesNotExist()
            .jsonPath("$.page").doesNotExist()
    }
}
