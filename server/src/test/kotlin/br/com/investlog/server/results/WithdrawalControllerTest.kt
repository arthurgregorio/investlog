package br.com.investlog.server.results

import br.com.investlog.server.BaseIntegrationTest
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

    private fun withdrawFromFund(holdingId: UUID, body: String) = restTestClient.post()
        .uri("/private/v1/wallets/$fundsWalletId/fund-holdings/$holdingId/withdrawals")
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
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
}
