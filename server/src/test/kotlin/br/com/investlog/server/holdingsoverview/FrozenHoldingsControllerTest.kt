package br.com.investlog.server.holdingsoverview

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.cryptoholdings.rest.payloads.CryptoHoldingResponse
import br.com.investlog.server.fundholdings.rest.payloads.FundHoldingResponse
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
import kotlin.test.assertFalse

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FrozenHoldingsControllerTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTestClient: RestTestClient

    lateinit var stocksWalletId: UUID
    lateinit var cryptoWalletId: UUID
    lateinit var fundsWalletId: UUID
    lateinit var stockTypeId: UUID
    lateinit var fundTypeId: UUID

    @BeforeAll
    fun setup() {
        stocksWalletId = createWallet("Frozen Stocks Wallet", "stocks")
        cryptoWalletId = createWallet("Frozen Crypto Wallet", "crypto")
        fundsWalletId = createWallet("Frozen Funds Wallet", "funds")
        stockTypeId = createType("/private/v1/stock-types", "Ação Congelada")
        fundTypeId = createType("/private/v1/fund-types", "Fundo Congelado")
    }

    private fun createWallet(name: String, kind: String): UUID = restTestClient.post()
        .uri("/private/v1/wallets")
        .contentType(MediaType.APPLICATION_JSON)
        .body("""{"name":"$name","kind":"$kind","currency":"BRL"}""")
        .exchange()
        .returnResult<WalletResponse>()
        .responseBody!!
        .id

    private fun createType(uri: String, name: String): UUID = restTestClient.post()
        .uri(uri)
        .contentType(MediaType.APPLICATION_JSON)
        .body("""{"name":"$name"}""")
        .exchange()
        .returnResult<TypeResponse>()
        .responseBody!!
        .id

    private fun createStockHolding(ticker: String): StockHoldingResponse = restTestClient.post()
        .uri("/private/v1/wallets/$stocksWalletId/stock-holdings")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            """
            {
              "stockTypeId":"$stockTypeId",
              "ticker":"$ticker",
              "name":"Holding $ticker",
              "currentPrice":10.00,
              "lot":{"lotDate":"2024-01-15","quantity":10,"price":8.00}
            }
            """.trimIndent()
        )
        .exchange()
        .expectStatus().isCreated()
        .returnResult<StockHoldingResponse>()
        .responseBody!!

    private fun createCryptoHolding(ticker: String): CryptoHoldingResponse = restTestClient.post()
        .uri("/private/v1/wallets/$cryptoWalletId/crypto-holdings")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            """
            {
              "ticker":"$ticker",
              "name":"Holding $ticker",
              "currentPrice":100.00,
              "lot":{"lotDate":"2024-01-15","quantity":2,"price":80.00}
            }
            """.trimIndent()
        )
        .exchange()
        .expectStatus().isCreated()
        .returnResult<CryptoHoldingResponse>()
        .responseBody!!

    private fun createFundHolding(name: String): FundHoldingResponse = restTestClient.post()
        .uri("/private/v1/wallets/$fundsWalletId/fund-holdings")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            """
            {
              "fundTypeId":"$fundTypeId",
              "name":"$name",
              "currentValue":1100.00,
              "contribution":{"contributionDate":"2024-01-10","amount":1000.00}
            }
            """.trimIndent()
        )
        .exchange()
        .expectStatus().isCreated()
        .returnResult<FundHoldingResponse>()
        .responseBody!!

    private fun patch(uri: String, body: String) = restTestClient.patch()
        .uri(uri)
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .exchange()

    private fun postLot(holdingId: UUID, kind: String) = restTestClient.post()
        .uri("/private/v1/wallets/${if (kind == "stock") stocksWalletId else cryptoWalletId}/$kind-holdings/$holdingId/lots")
        .contentType(MediaType.APPLICATION_JSON)
        .body("""{"lotDate":"2024-03-10","quantity":5,"price":9.00}""")
        .exchange()

    private fun postContribution(holdingId: UUID) = restTestClient.post()
        .uri("/private/v1/wallets/$fundsWalletId/fund-holdings/$holdingId/contributions")
        .contentType(MediaType.APPLICATION_JSON)
        .body("""{"contributionDate":"2024-03-10","amount":250.00}""")
        .exchange()

    @Test
    @Order(1)
    fun `every kind of holding is created unfrozen`() {

        assertFalse(createStockHolding("FRZS1").frozen)
        assertFalse(createCryptoHolding("FRZC1").frozen)
        assertFalse(createFundHolding("Fundo Frz 1").frozen)
    }

    @Test
    @Order(2)
    fun `patching a stock holding flips the flag and leaves every other field untouched`() {

        val holding = createStockHolding("FRZS2")

        patch("/private/v1/wallets/$stocksWalletId/stock-holdings/${holding.id}", """{"frozen":true}""")
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.frozen").isEqualTo(true)
            .jsonPath("$.ticker").isEqualTo("FRZS2")
            .jsonPath("$.name").isEqualTo("Holding FRZS2")
            .jsonPath("$.currentPrice").isEqualTo(10.0)
            .jsonPath("$.lots.length()").isEqualTo(1)

        patch("/private/v1/wallets/$stocksWalletId/stock-holdings/${holding.id}", """{"currentPrice":11.00}""")
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.frozen").isEqualTo(true)
            .jsonPath("$.currentPrice").isEqualTo(11.0)

        patch("/private/v1/wallets/$stocksWalletId/stock-holdings/${holding.id}", """{"frozen":false}""")
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.frozen").isEqualTo(false)
            .jsonPath("$.currentPrice").isEqualTo(11.0)
    }

    @Test
    @Order(3)
    fun `patching a crypto holding flips the flag and leaves every other field untouched`() {

        val holding = createCryptoHolding("FRZC2")

        patch("/private/v1/wallets/$cryptoWalletId/crypto-holdings/${holding.id}", """{"frozen":true}""")
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.frozen").isEqualTo(true)
            .jsonPath("$.ticker").isEqualTo("FRZC2")
            .jsonPath("$.name").isEqualTo("Holding FRZC2")
            .jsonPath("$.currentPrice").isEqualTo(100.0)
            .jsonPath("$.lots.length()").isEqualTo(1)

        patch("/private/v1/wallets/$cryptoWalletId/crypto-holdings/${holding.id}", """{"name":"Renamed"}""")
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.frozen").isEqualTo(true)
            .jsonPath("$.name").isEqualTo("Renamed")
    }

    @Test
    @Order(4)
    fun `patching a fund holding flips the flag and leaves every other field untouched`() {

        val holding = createFundHolding("Fundo Frz 2")

        patch("/private/v1/wallets/$fundsWalletId/fund-holdings/${holding.id}", """{"frozen":true}""")
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.frozen").isEqualTo(true)
            .jsonPath("$.name").isEqualTo("Fundo Frz 2")
            .jsonPath("$.currentValue").isEqualTo(1100.0)
            .jsonPath("$.contributions.length()").isEqualTo(1)

        patch("/private/v1/wallets/$fundsWalletId/fund-holdings/${holding.id}", """{"currentValue":1200.00}""")
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.frozen").isEqualTo(true)
            .jsonPath("$.currentValue").isEqualTo(1200.0)
    }

    @Test
    @Order(5)
    fun `a lot posted to a frozen stock holding returns 409 and persists nothing`() {

        val holding = createStockHolding("FRZS3")
        patch("/private/v1/wallets/$stocksWalletId/stock-holdings/${holding.id}", """{"frozen":true}""")
            .expectStatus().isOk()

        postLot(holding.id, "stock").expectStatus().isEqualTo(409)

        restTestClient.get()
            .uri("/private/v1/wallets/$stocksWalletId/stock-holdings")
            .exchange()
            .expectBody()
            .jsonPath("$.content[?(@.ticker == 'FRZS3')].lots.length()").isEqualTo(1)
    }

    @Test
    @Order(6)
    fun `a lot posted to a frozen crypto holding returns 409 and persists nothing`() {

        val holding = createCryptoHolding("FRZC3")
        patch("/private/v1/wallets/$cryptoWalletId/crypto-holdings/${holding.id}", """{"frozen":true}""")
            .expectStatus().isOk()

        postLot(holding.id, "crypto").expectStatus().isEqualTo(409)

        restTestClient.get()
            .uri("/private/v1/wallets/$cryptoWalletId/crypto-holdings")
            .exchange()
            .expectBody()
            .jsonPath("$.content[?(@.ticker == 'FRZC3')].lots.length()").isEqualTo(1)
    }

    @Test
    @Order(7)
    fun `a contribution posted to a frozen fund holding returns 409 and persists nothing`() {

        val holding = createFundHolding("Fundo Frz 3")
        patch("/private/v1/wallets/$fundsWalletId/fund-holdings/${holding.id}", """{"frozen":true}""")
            .expectStatus().isOk()

        postContribution(holding.id).expectStatus().isEqualTo(409)

        restTestClient.get()
            .uri("/private/v1/wallets/$fundsWalletId/fund-holdings")
            .exchange()
            .expectBody()
            .jsonPath("$.content[?(@.name == 'Fundo Frz 3')].contributions.length()").isEqualTo(1)
    }

    @Test
    @Order(8)
    fun `the 409 carries the standard problem payload`() {

        val holding = createStockHolding("FRZS4")
        patch("/private/v1/wallets/$stocksWalletId/stock-holdings/${holding.id}", """{"frozen":true}""")
            .expectStatus().isOk()

        postLot(holding.id, "stock")
            .expectStatus().isEqualTo(409)
            .expectBody()
            .jsonPath("$.status").isEqualTo(409)
            .jsonPath("$.detail").isNotEmpty()
            .jsonPath("$.timestamp").isNotEmpty()
    }

    @Test
    @Order(9)
    fun `an unfrozen holding still accepts a new position`() {

        val stock = createStockHolding("FRZS5")
        val crypto = createCryptoHolding("FRZC5")
        val fund = createFundHolding("Fundo Frz 5")

        postLot(stock.id, "stock").expectStatus().isCreated()
        postLot(crypto.id, "crypto").expectStatus().isCreated()
        postContribution(fund.id).expectStatus().isCreated()
    }

    @Test
    @Order(10)
    fun `unfreezing a holding lets it accept a new position again`() {

        val holding = createStockHolding("FRZS6")
        val uri = "/private/v1/wallets/$stocksWalletId/stock-holdings/${holding.id}"

        patch(uri, """{"frozen":true}""").expectStatus().isOk()
        postLot(holding.id, "stock").expectStatus().isEqualTo(409)

        patch(uri, """{"frozen":false}""").expectStatus().isOk()
        postLot(holding.id, "stock").expectStatus().isCreated()
    }

    @Test
    @Order(11)
    fun `everything except a new position stays allowed on a frozen holding`() {

        val holding = createStockHolding("FRZS7")
        val uri = "/private/v1/wallets/$stocksWalletId/stock-holdings/${holding.id}"
        patch(uri, """{"frozen":true}""").expectStatus().isOk()

        patch(uri, """{"name":"Edited","currentPrice":12.00}""").expectStatus().isOk()

        restTestClient.delete()
            .uri("$uri/lots/${holding.lots.single().id}")
            .exchange()
            .expectStatus().isNoContent()

        restTestClient.delete().uri(uri).exchange().expectStatus().isNoContent()
    }

    @Test
    @Order(12)
    fun `holdings overview returns the frozen flag on every row`() {

        val frozenStock = createStockHolding("FRZS8")
        createStockHolding("FRZS9")
        val frozenCrypto = createCryptoHolding("FRZC8")
        val frozenFund = createFundHolding("Fundo Frz 8")
        patch("/private/v1/wallets/$stocksWalletId/stock-holdings/${frozenStock.id}", """{"frozen":true}""")
            .expectStatus().isOk()
        patch("/private/v1/wallets/$cryptoWalletId/crypto-holdings/${frozenCrypto.id}", """{"frozen":true}""")
            .expectStatus().isOk()
        patch("/private/v1/wallets/$fundsWalletId/fund-holdings/${frozenFund.id}", """{"frozen":true}""")
            .expectStatus().isOk()

        restTestClient.get()
            .uri("/private/v1/holdings?size=100")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content[?(@.ticker == 'FRZS8')].frozen").isEqualTo(true)
            .jsonPath("$.content[?(@.ticker == 'FRZS9')].frozen").isEqualTo(false)
            .jsonPath("$.content[?(@.ticker == 'FRZC8')].frozen").isEqualTo(true)
            .jsonPath("$.content[?(@.name == 'Fundo Frz 8')].frozen").isEqualTo(true)
    }

    @Test
    @Order(13)
    fun `holdings report returns the frozen flag on every row`() {

        restTestClient.get()
            .uri("/private/v1/holdings/report")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$[?(@.ticker == 'FRZS8')].frozen").isEqualTo(true)
            .jsonPath("$[?(@.ticker == 'FRZS9')].frozen").isEqualTo(false)
            .jsonPath("$[?(@.ticker == 'FRZC8')].frozen").isEqualTo(true)
            .jsonPath("$[?(@.name == 'Fundo Frz 8')].frozen").isEqualTo(true)
    }

    @Test
    @Order(14)
    fun `a report row merging a frozen holding with an unfrozen one reads as frozen`() {

        createCryptoHolding("FRZMRG")
        val frozen = createCryptoHolding("FRZMRG")
        val frozenUri = "/private/v1/wallets/$cryptoWalletId/crypto-holdings/${frozen.id}"
        patch(frozenUri, """{"frozen":true}""").expectStatus().isOk()

        restTestClient.get()
            .uri("/private/v1/holdings/report?walletId=$cryptoWalletId")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$[?(@.ticker == 'FRZMRG')].quantity").isEqualTo(4)
            .jsonPath("$[?(@.ticker == 'FRZMRG')].frozen").isEqualTo(true)

        patch(frozenUri, """{"frozen":false}""").expectStatus().isOk()

        restTestClient.get()
            .uri("/private/v1/holdings/report?walletId=$cryptoWalletId")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$[?(@.ticker == 'FRZMRG')].frozen").isEqualTo(false)
    }
}
