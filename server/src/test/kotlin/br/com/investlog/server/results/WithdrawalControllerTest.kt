package br.com.investlog.server.results

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.cryptoholdings.rest.payloads.CryptoHoldingResponse
import br.com.investlog.server.fundholdings.rest.payloads.FundHoldingResponse
import br.com.investlog.server.jooq.finances.tables.references.RESULTS
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
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WithdrawalControllerTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTestClient: RestTestClient

    @Autowired
    lateinit var dsl: DSLContext

    lateinit var stocksWalletId: UUID
    lateinit var fundsWalletId: UUID
    lateinit var stockTypeId: UUID
    lateinit var fundTypeId: UUID
    lateinit var petrobrasHoldingId: UUID

    @BeforeAll
    fun setup() {
        stocksWalletId = createWallet("Stocks Wallet", "stocks")
        fundsWalletId = createWallet("Funds Wallet", "funds")

        stockTypeId = restTestClient.post()
            .uri("/private/v1/stock-types")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Ação ON"}""")
            .exchange()
            .returnResult<TypeResponse>()
            .responseBody!!
            .id

        fundTypeId = restTestClient.post()
            .uri("/private/v1/fund-types")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Fundos Imobiliários"}""")
            .exchange()
            .returnResult<TypeResponse>()
            .responseBody!!
            .id

        petrobrasHoldingId = createStockHolding("PETR4", "100", "35.00", "38.50")
    }

    private fun createWallet(name: String, kind: String): UUID = restTestClient.post()
        .uri("/private/v1/wallets")
        .contentType(MediaType.APPLICATION_JSON)
        .body("""{"name":"$name","kind":"$kind","currency":"BRL"}""")
        .exchange()
        .returnResult<WalletResponse>()
        .responseBody!!
        .id

    private fun createStockHolding(
        ticker: String,
        quantity: String,
        price: String,
        currentPrice: String,
    ): UUID = restTestClient.post()
        .uri("/private/v1/wallets/$stocksWalletId/stock-holdings")
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

    private fun withdrawFromStock(holdingId: UUID, body: String) = restTestClient.post()
        .uri("/private/v1/wallets/$stocksWalletId/stock-holdings/$holdingId/withdrawals")
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .exchange()

    private fun createCryptoHolding(walletId: UUID, ticker: String, quantity: String, price: String): UUID =
        restTestClient.post()
            .uri("/private/v1/wallets/$walletId/crypto-holdings")
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

    private fun withdrawFromCrypto(walletId: UUID, holdingId: UUID, body: String) = restTestClient.post()
        .uri("/private/v1/wallets/$walletId/crypto-holdings/$holdingId/withdrawals")
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .exchange()

    private fun deleteCryptoWithdrawal(walletId: UUID, holdingId: UUID, resultId: UUID) = restTestClient.delete()
        .uri("/private/v1/wallets/$walletId/crypto-holdings/$holdingId/withdrawals/$resultId")
        .exchange()

    private fun withdrawFromFund(holdingId: UUID, body: String) = restTestClient.post()
        .uri("/private/v1/wallets/$fundsWalletId/fund-holdings/$holdingId/withdrawals")
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .exchange()

    private fun deleteStockWithdrawal(holdingId: UUID, resultId: UUID) = restTestClient.delete()
        .uri("/private/v1/wallets/$stocksWalletId/stock-holdings/$holdingId/withdrawals/$resultId")
        .exchange()

    private fun deleteFundWithdrawal(holdingId: UUID, resultId: UUID) = restTestClient.delete()
        .uri("/private/v1/wallets/$fundsWalletId/fund-holdings/$holdingId/withdrawals/$resultId")
        .exchange()

    private fun countResults(): Int = dsl.fetchCount(dsl.selectFrom(RESULTS))

    private fun latestResult() = dsl.selectFrom(RESULTS).orderBy(RESULTS.ID.desc()).limit(1).fetchSingle()

    @Test
    @Order(1)
    fun `a partial withdrawal reduces the position, leaves it ACTIVE and stores average cost`() {

        withdrawFromStock(
            petrobrasHoldingId,
            """{"resultDate":"2026-09-19","quantity":40,"unitPrice":50.00,"fees":0,"taxes":0}""",
        ).expectStatus().isCreated()

        restTestClient.get()
            .uri("/private/v1/holdings?walletId=$stocksWalletId&search=PETR4")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.page.totalElements").isEqualTo(1)
            .jsonPath("$.content[0].quantity").isEqualTo(60)
            .jsonPath("$.content[0].costBasis").isEqualTo(2100.0)

        val result = latestResult()
        assertEquals(0, BigDecimal("2000").compareTo(result.grossAmount))
        assertEquals(0, BigDecimal("1400").compareTo(result.costBasis))
    }

    @Test
    @Order(2)
    fun `withdrawing the full remaining position completes it and drops it from every total`() {

        withdrawFromStock(
            petrobrasHoldingId,
            """{"resultDate":"2026-09-19","quantity":60,"unitPrice":50.00}""",
        ).expectStatus().isCreated()

        restTestClient.get()
            .uri("/private/v1/holdings?walletId=$stocksWalletId&search=PETR4")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.page.totalElements").isEqualTo(0)

        restTestClient.get()
            .uri("/private/v1/wallets/$stocksWalletId")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.holdingCount").isEqualTo(0)
            .jsonPath("$.totalInvested").isEqualTo(0)

        restTestClient.get()
            .uri("/private/v1/overview")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.totalCostBasis").isEqualTo(0)
    }

    @Test
    @Order(3)
    fun `withdrawing from an already completed holding is rejected and writes nothing`() {

        val resultsBefore = countResults()

        withdrawFromStock(
            petrobrasHoldingId,
            """{"resultDate":"2026-09-19","quantity":1,"unitPrice":50.00}""",
        ).expectStatus().isBadRequest()

        assertEquals(resultsBefore, countResults())
    }

    @Test
    @Order(4)
    fun `withdrawing more than the remaining quantity is rejected and writes nothing`() {

        val holdingId = createStockHolding("VALE3", "10", "20.00", "25.00")
        val resultsBefore = countResults()

        withdrawFromStock(
            holdingId,
            """{"resultDate":"2026-09-19","quantity":11,"unitPrice":25.00}""",
        ).expectStatus().isBadRequest()

        assertEquals(resultsBefore, countResults())

        restTestClient.get()
            .uri("/private/v1/holdings?walletId=$stocksWalletId&search=VALE3")
            .exchange()
            .expectBody()
            .jsonPath("$.content[0].quantity").isEqualTo(10)
    }

    @Test
    @Order(5)
    fun `a withdrawal with no fees and no taxes stores zero for both`() {

        val holdingId = createStockHolding("ITUB4", "10", "10.00", "12.00")

        withdrawFromStock(
            holdingId,
            """{"resultDate":"2026-09-19","quantity":5,"unitPrice":12.00}""",
        ).expectStatus().isCreated()

        val result = latestResult()
        assertEquals(0, BigDecimal.ZERO.compareTo(result.fees))
        assertEquals(0, BigDecimal.ZERO.compareTo(result.taxes))
    }

    @Test
    @Order(6)
    fun `gross 10 with fees 1 taxes 2 and cost basis 5 stores net 7 and profit 2`() {

        val holdingId = createStockHolding("BBAS3", "10", "0.50", "1.00")

        withdrawFromStock(
            holdingId,
            """{"resultDate":"2026-09-19","quantity":10,"unitPrice":1.00,"fees":1,"taxes":2}""",
        ).expectStatus().isCreated()

        val result = latestResult()
        assertEquals(0, BigDecimal("10").compareTo(result.grossAmount))
        assertEquals(0, BigDecimal("5").compareTo(result.costBasis))
        assertEquals(0, BigDecimal("7").compareTo(result.netAmount))
        assertEquals(0, BigDecimal("2").compareTo(result.profit))
    }

    @Test
    @Order(7)
    fun `a fund withdrawal reduces current value and records a proportional cost basis`() {

        val holdingId = createFundHolding("Tesouro IPCA+", "5000.00", "10000.00")

        withdrawFromFund(holdingId, """{"resultDate":"2026-09-19","amount":2500.00}""")
            .expectStatus().isCreated()

        restTestClient.get()
            .uri("/private/v1/wallets/$fundsWalletId/fund-holdings/$holdingId")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.currentValue").isEqualTo(7500.0)

        assertEquals(0, BigDecimal("1250").compareTo(latestResult().costBasis))
    }

    @Test
    @Order(8)
    fun `a fund withdrawal larger than the current value is rejected and writes nothing`() {

        val holdingId = createFundHolding("Fundo Pequeno", "1000.00", "1200.00")
        val resultsBefore = countResults()

        withdrawFromFund(holdingId, """{"resultDate":"2026-09-19","amount":1500.00}""")
            .expectStatus().isBadRequest()

        assertEquals(resultsBefore, countResults())

        restTestClient.get()
            .uri("/private/v1/wallets/$fundsWalletId/fund-holdings/$holdingId")
            .exchange()
            .expectBody()
            .jsonPath("$.currentValue").isEqualTo(1200.0)
    }

    @Test
    @Order(9)
    fun `the results endpoint returns recorded results with holding and wallet labels`() {

        restTestClient.get()
            .uri("/private/v1/results")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.page.totalElements").isEqualTo(countResults())
            .jsonPath("$.content[0].holdingName").exists()
            .jsonPath("$.content[0].kind").exists()
            .jsonPath("$.content[0].walletName").exists()
            .jsonPath("$.content[0].resultType").isEqualTo("WITHDRAWAL")
    }

    @Test
    @Order(10)
    fun `a stock holding's detail response lists its withdrawals alongside its lots`() {

        val holdingId = createStockHolding("WEGE3", "20", "40.00", "45.00")

        withdrawFromStock(
            holdingId,
            """{"resultDate":"2026-09-19","quantity":8,"unitPrice":45.00,"fees":1,"taxes":2}""",
        ).expectStatus().isCreated()

        restTestClient.get()
            .uri("/private/v1/wallets/$stocksWalletId/stock-holdings/$holdingId")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.lots.length()").isEqualTo(1)
            .jsonPath("$.withdrawals.length()").isEqualTo(1)
            .jsonPath("$.withdrawals[0].quantity").isEqualTo(8)
            .jsonPath("$.withdrawals[0].grossAmount").isEqualTo(360.0)
            .jsonPath("$.withdrawals[0].fees").isEqualTo(1.0)
            .jsonPath("$.withdrawals[0].taxes").isEqualTo(2.0)
            .jsonPath("$.withdrawals[0].netAmount").isEqualTo(357.0)
    }

    @Test
    @Order(11)
    fun `a stock holding with no withdrawals returns an empty withdrawals list`() {

        val holdingId = createStockHolding("TAEE11", "5", "30.00", "32.00")

        restTestClient.get()
            .uri("/private/v1/wallets/$stocksWalletId/stock-holdings/$holdingId")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.withdrawals.length()").isEqualTo(0)
    }

    @Test
    @Order(12)
    fun `a fund holding's detail response lists its withdrawals alongside its contributions`() {

        val holdingId = createFundHolding("Fundo Multimercado", "4000.00", "4000.00")

        withdrawFromFund(holdingId, """{"resultDate":"2026-09-19","amount":1000.00,"fees":5,"taxes":10}""")
            .expectStatus().isCreated()

        restTestClient.get()
            .uri("/private/v1/wallets/$fundsWalletId/fund-holdings/$holdingId")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.contributions.length()").isEqualTo(1)
            .jsonPath("$.withdrawals.length()").isEqualTo(1)
            .jsonPath("$.withdrawals[0].grossAmount").isEqualTo(1000.0)
            .jsonPath("$.withdrawals[0].netAmount").isEqualTo(985.0)
    }

    @Test
    @Order(13)
    fun `deleting a holding's most recent withdrawal reverses its quantity and cost basis`() {

        val holdingId = createStockHolding("RENT3", "50", "20.00", "22.00")

        withdrawFromStock(
            holdingId,
            """{"resultDate":"2026-09-19","quantity":20,"unitPrice":22.00}""",
        ).expectStatus().isCreated()

        val resultId = latestResult().externalId!!
        val resultsBefore = countResults()

        deleteStockWithdrawal(holdingId, resultId).expectStatus().isNoContent()

        assertEquals(resultsBefore - 1, countResults())

        restTestClient.get()
            .uri("/private/v1/holdings?walletId=$stocksWalletId&search=RENT3")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content[0].quantity").isEqualTo(50)
            .jsonPath("$.content[0].costBasis").isEqualTo(1000.0)
    }

    @Test
    @Order(14)
    fun `deleting the withdrawal that fully closed a position reactivates the holding`() {

        val holdingId = createStockHolding("CPLE6", "15", "10.00", "11.00")

        withdrawFromStock(
            holdingId,
            """{"resultDate":"2026-09-19","quantity":15,"unitPrice":11.00}""",
        ).expectStatus().isCreated()

        restTestClient.get()
            .uri("/private/v1/holdings?walletId=$stocksWalletId&search=CPLE6")
            .exchange()
            .expectBody()
            .jsonPath("$.page.totalElements").isEqualTo(0)

        val resultId = latestResult().externalId!!

        deleteStockWithdrawal(holdingId, resultId).expectStatus().isNoContent()

        restTestClient.get()
            .uri("/private/v1/holdings?walletId=$stocksWalletId&search=CPLE6")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.page.totalElements").isEqualTo(1)
            .jsonPath("$.content[0].quantity").isEqualTo(15)
    }

    @Test
    @Order(15)
    fun `deleting a fund withdrawal restores the withdrawn amount to the current value`() {

        val holdingId = createFundHolding("Fundo Cambial", "3000.00", "3000.00")

        withdrawFromFund(holdingId, """{"resultDate":"2026-09-19","amount":800.00}""")
            .expectStatus().isCreated()

        val resultId = latestResult().externalId!!

        deleteFundWithdrawal(holdingId, resultId).expectStatus().isNoContent()

        restTestClient.get()
            .uri("/private/v1/wallets/$fundsWalletId/fund-holdings/$holdingId")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.currentValue").isEqualTo(3000.0)
    }

    @Test
    @Order(16)
    fun `deleting any withdrawal other than the most recent is rejected and deletes nothing`() {

        val holdingId = createStockHolding("EGIE3", "30", "40.00", "42.00")

        withdrawFromStock(
            holdingId,
            """{"resultDate":"2026-09-10","quantity":10,"unitPrice":42.00}""",
        ).expectStatus().isCreated()
        val firstResultId = latestResult().externalId!!

        withdrawFromStock(
            holdingId,
            """{"resultDate":"2026-09-19","quantity":10,"unitPrice":42.00}""",
        ).expectStatus().isCreated()

        val resultsBefore = countResults()

        deleteStockWithdrawal(holdingId, firstResultId).expectStatus().isEqualTo(409)

        assertEquals(resultsBefore, countResults())
    }

    @Test
    @Order(17)
    fun `deleting a withdrawal that doesn't belong to the holding responds 404`() {

        val holdingId = createStockHolding("SBSP3", "5", "60.00", "65.00")

        deleteStockWithdrawal(holdingId, UUID.randomUUID()).expectStatus().isNotFound()
    }

    @Test
    @Order(18)
    fun `a partial crypto withdrawal is listed with the holding in both its detail and its list`() {

        val cryptoWalletId = createWallet("Crypto Wallet 18", "crypto")
        val holdingId = createCryptoHolding(cryptoWalletId, "BTC", "2", "100.00")

        withdrawFromCrypto(
            cryptoWalletId,
            holdingId,
            """{"resultDate":"2026-09-19","quantity":0.5,"unitPrice":150.00,"fees":1,"taxes":2}""",
        ).expectStatus().isCreated()

        restTestClient.get()
            .uri("/private/v1/wallets/$cryptoWalletId/crypto-holdings/$holdingId")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.withdrawals.length()").isEqualTo(1)
            .jsonPath("$.withdrawals[0].quantity").isEqualTo(0.5)
            .jsonPath("$.withdrawals[0].grossAmount").isEqualTo(75.0)
            .jsonPath("$.withdrawals[0].netAmount").isEqualTo(72.0)
            .jsonPath("$.withdrawals[0].profit").isEqualTo(22.0)

        restTestClient.get()
            .uri("/private/v1/wallets/$cryptoWalletId/crypto-holdings")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content[0].withdrawals.length()").isEqualTo(1)
            .jsonPath("$.content[0].withdrawals[0].resultType").isEqualTo("WITHDRAWAL")
    }

    @Test
    @Order(19)
    fun `withdrawing a whole crypto position completes it and deleting that withdrawal reactivates it`() {

        val cryptoWalletId = createWallet("Crypto Wallet 19", "crypto")
        val holdingId = createCryptoHolding(cryptoWalletId, "ETH", "4", "50.00")

        withdrawFromCrypto(
            cryptoWalletId,
            holdingId,
            """{"resultDate":"2026-09-19","quantity":4,"unitPrice":60.00}""",
        ).expectStatus().isCreated()

        restTestClient.get()
            .uri("/private/v1/holdings?walletId=$cryptoWalletId")
            .exchange()
            .expectBody()
            .jsonPath("$.page.totalElements").isEqualTo(0)

        withdrawFromCrypto(
            cryptoWalletId,
            holdingId,
            """{"resultDate":"2026-09-19","quantity":1,"unitPrice":60.00}""",
        ).expectStatus().isBadRequest()

        val resultId = latestResult().externalId!!

        deleteCryptoWithdrawal(cryptoWalletId, holdingId, resultId).expectStatus().isNoContent()

        restTestClient.get()
            .uri("/private/v1/holdings?walletId=$cryptoWalletId")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.page.totalElements").isEqualTo(1)
            .jsonPath("$.content[0].quantity").isEqualTo(4)
    }

    @Test
    @Order(20)
    fun `a stock withdrawal is listed with the holding in the list endpoint`() {

        val listedWalletId = createWallet("Stocks Wallet 20", "stocks")
        val holdingId = restTestClient.post()
            .uri("/private/v1/wallets/$listedWalletId/stock-holdings")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                """
                {
                  "stockTypeId":"$stockTypeId",
                  "ticker":"LREN3",
                  "name":"Holding LREN3",
                  "currentPrice":20.00,
                  "lot":{"lotDate":"2024-01-15","quantity":10,"price":10.00}
                }
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isCreated()
            .returnResult<StockHoldingResponse>()
            .responseBody!!
            .id

        restTestClient.post()
            .uri("/private/v1/wallets/$listedWalletId/stock-holdings/$holdingId/withdrawals")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"resultDate":"2026-09-19","quantity":4,"unitPrice":20.00,"fees":1,"taxes":2}""")
            .exchange()
            .expectStatus().isCreated()

        restTestClient.get()
            .uri("/private/v1/wallets/$listedWalletId/stock-holdings")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content[0].withdrawals.length()").isEqualTo(1)
            .jsonPath("$.content[0].withdrawals[0].quantity").isEqualTo(4)
            .jsonPath("$.content[0].withdrawals[0].netAmount").isEqualTo(77.0)
            .jsonPath("$.content[0].withdrawals[0].profit").isEqualTo(37.0)
    }

    @Test
    @Order(21)
    fun `a fund withdrawal is listed with the holding in the list endpoint`() {

        val listedWalletId = createWallet("Funds Wallet 21", "funds")
        restTestClient.post()
            .uri("/private/v1/wallets/$listedWalletId/fund-holdings")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                """
                {
                  "fundTypeId":"$fundTypeId",
                  "name":"Fundo Listado",
                  "currentValue":2000.00,
                  "contribution":{"contributionDate":"2024-01-10","amount":1000.00}
                }
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isCreated()
        val holdingId = restTestClient.get()
            .uri("/private/v1/wallets/$listedWalletId/fund-holdings")
            .exchange()
            .returnResult<Map<String, Any?>>()
            .responseBody!!
            .let { page -> ((page["content"] as List<*>).single() as Map<*, *>)["id"] as String }

        restTestClient.post()
            .uri("/private/v1/wallets/$listedWalletId/fund-holdings/$holdingId/withdrawals")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"resultDate":"2026-09-19","amount":500.00,"fees":5,"taxes":10}""")
            .exchange()
            .expectStatus().isCreated()

        restTestClient.get()
            .uri("/private/v1/wallets/$listedWalletId/fund-holdings")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content[0].withdrawals.length()").isEqualTo(1)
            .jsonPath("$.content[0].withdrawals[0].grossAmount").isEqualTo(500.0)
            .jsonPath("$.content[0].withdrawals[0].netAmount").isEqualTo(485.0)
    }

    @Test
    @Order(22)
    fun `withdrawing a whole fund completes it and deleting that withdrawal reactivates it`() {

        val holdingId = createFundHolding("Fundo Resgate Total", "1000.00", "1500.00")

        withdrawFromFund(holdingId, """{"resultDate":"2026-09-19","amount":1500.00}""")
            .expectStatus().isCreated()

        withdrawFromFund(holdingId, """{"resultDate":"2026-09-19","amount":1.00}""")
            .expectStatus().isBadRequest()

        deleteFundWithdrawal(holdingId, latestResult().externalId!!).expectStatus().isNoContent()

        restTestClient.get()
            .uri("/private/v1/wallets/$fundsWalletId/fund-holdings/$holdingId")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.currentValue").isEqualTo(1500.0)

        withdrawFromFund(holdingId, """{"resultDate":"2026-09-20","amount":100.00}""")
            .expectStatus().isCreated()
    }

    @Test
    @Order(23)
    fun `withdrawing from or deleting a withdrawal of an unknown holding responds 404`() {

        val unknownHoldingId = UUID.randomUUID()

        withdrawFromStock(
            unknownHoldingId,
            """{"resultDate":"2026-09-19","quantity":1,"unitPrice":10.00}""",
        ).expectStatus().isNotFound()

        deleteStockWithdrawal(unknownHoldingId, UUID.randomUUID()).expectStatus().isNotFound()
    }

    @Test
    @Order(24)
    fun `a holding of another kind is not found through the wrong kind's withdrawal routes`() {

        val stockHoldingId = createStockHolding("CMIG4", "10", "10.00", "11.00")
        val cryptoWalletId = createWallet("Crypto Wallet 24", "crypto")

        withdrawFromCrypto(
            stocksWalletId,
            stockHoldingId,
            """{"resultDate":"2026-09-19","quantity":1,"unitPrice":10.00}""",
        ).expectStatus().isNotFound()

        withdrawFromStock(
            stockHoldingId,
            """{"resultDate":"2026-09-19","quantity":1,"unitPrice":10.00}""",
        ).expectStatus().isCreated()
        val resultId = latestResult().externalId!!

        deleteCryptoWithdrawal(stocksWalletId, stockHoldingId, resultId).expectStatus().isNotFound()
        deleteFundWithdrawal(stockHoldingId, resultId).expectStatus().isNotFound()
        withdrawFromCrypto(cryptoWalletId, stockHoldingId, """{"resultDate":"2026-09-19","quantity":1,"unitPrice":10.00}""")
            .expectStatus().isNotFound()
    }
}
