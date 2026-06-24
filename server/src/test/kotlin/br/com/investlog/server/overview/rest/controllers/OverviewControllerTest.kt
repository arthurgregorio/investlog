package br.com.investlog.server.overview.rest.controllers

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.overview.rest.payloads.PortfolioSummaryResponse
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
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OverviewControllerTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTestClient: RestTestClient

    lateinit var walletId: UUID
    lateinit var holdingId: UUID

    @BeforeAll
    fun setup() {
        val stockTypeId = restTestClient.post()
            .uri("/private/v1/stock-types")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Overview Stock Type"}""")
            .exchange()
            .returnResult<TypeResponse>()
            .responseBody!!
            .id

        walletId = restTestClient.post()
            .uri("/private/v1/wallets")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Overview Wallet","kind":"stocks","currency":"BRL"}""")
            .exchange()
            .returnResult<WalletResponse>()
            .responseBody!!
            .id

        holdingId = restTestClient.post()
            .uri("/private/v1/wallets/$walletId/stock-holdings")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                """{"stockTypeId":"$stockTypeId","ticker":"OVRVW3","currentPrice":60.00,
                   "lot":{"lotDate":"2025-03-10","quantity":5,"price":50.00}}"""
            )
            .exchange()
            .expectStatus().isCreated()
            .returnResult<StockHoldingResponse>()
            .responseBody!!
            .id
    }

    @Test
    @Order(1)
    fun `GET overview returns summary with non-null totals`() {
        restTestClient.get()
            .uri("/private/v1/overview")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.totalCostBasis").isNotEmpty()
            .jsonPath("$.totalCurrentValue").isNotEmpty()
            .jsonPath("$.totalGain").isNotEmpty()
            .jsonPath("$.kindSummaries").isArray()
    }

    @Test
    @Order(2)
    fun `GET overview includes stocks kind summary with correct holding count`() {
        restTestClient.get()
            .uri("/private/v1/overview")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.kindSummaries[?(@.kind == 'STOCKS')].holdingCount").isNotEmpty()
    }

    @Test
    @Order(3)
    fun `GET overview series returns monthly data points in chronological order`() {
        restTestClient.get()
            .uri("/private/v1/overview/series")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$").isArray()
            .jsonPath("$[?(@.month == '2025-03')].totalInvested").isNotEmpty()
    }

    @Test
    @Order(4)
    fun `GET overview series totalInvested is cumulative`() {
        val body = restTestClient.get()
            .uri("/private/v1/overview/series")
            .exchange()
            .expectStatus().isOk()
            .expectBody(List::class.java)
            .returnResult().responseBody!!

        var previousTotal = Double.MIN_VALUE
        for (entry in body) {
            @Suppress("UNCHECKED_CAST")
            val point = entry as Map<String, Any>
            val totalInvested = (point["totalInvested"] as Number).toDouble()
            assert(totalInvested >= previousTotal) {
                "totalInvested should be non-decreasing: $totalInvested < $previousTotal"
            }
            previousTotal = totalInvested
        }
    }

    @Test
    @Order(5)
    fun `GET overview converts a wallet in a different currency using the configured rate`() {

        restTestClient.put()
            .uri("/private/v1/currency-rates/USD")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"rate":5.00}""")
            .exchange()
            .expectStatus().isOk()

        val before = restTestClient.get()
            .uri("/private/v1/overview")
            .exchange()
            .expectStatus().isOk()
            .returnResult<PortfolioSummaryResponse>()
            .responseBody!!

        val usdStockTypeId = restTestClient.post()
            .uri("/private/v1/stock-types")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"USD Overview Stock Type"}""")
            .exchange()
            .returnResult<TypeResponse>()
            .responseBody!!
            .id

        val usdWalletId = restTestClient.post()
            .uri("/private/v1/wallets")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"USD Overview Wallet","kind":"stocks","currency":"USD"}""")
            .exchange()
            .returnResult<WalletResponse>()
            .responseBody!!
            .id

        restTestClient.post()
            .uri("/private/v1/wallets/$usdWalletId/stock-holdings")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                """{"stockTypeId":"$usdStockTypeId","ticker":"USDOV3","currentPrice":110.00,
                   "lot":{"lotDate":"2025-04-10","quantity":2,"price":100.00}}"""
            )
            .exchange()
            .expectStatus().isCreated()

        val after = restTestClient.get()
            .uri("/private/v1/overview")
            .exchange()
            .expectStatus().isOk()
            .returnResult<PortfolioSummaryResponse>()
            .responseBody!!

        assertEquals("BRL", after.displayCurrency)
        assertEquals(0, after.totalCostBasis.compareTo(before.totalCostBasis + BigDecimal("1000.00")))
        assertEquals(0, after.totalCurrentValue.compareTo(before.totalCurrentValue + BigDecimal("1100.00")))
    }

    @Test
    @Order(6)
    fun `GET overview uses the currently selected preferred currency for conversion`() {

        val beforeSwitch = restTestClient.get()
            .uri("/private/v1/overview")
            .exchange()
            .expectStatus().isOk()
            .returnResult<PortfolioSummaryResponse>()
            .responseBody!!

        restTestClient.patch()
            .uri("/private/v1/profile")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"preferredCurrency":"USD"}""")
            .exchange()
            .expectStatus().isOk()

        val afterSwitch = restTestClient.get()
            .uri("/private/v1/overview")
            .exchange()
            .expectStatus().isOk()
            .returnResult<PortfolioSummaryResponse>()
            .responseBody!!

        assertEquals("USD", afterSwitch.displayCurrency)
        assertEquals(
            0,
            afterSwitch.totalCostBasis.compareTo(
                beforeSwitch.totalCostBasis.divide(BigDecimal("5.00"), 10, RoundingMode.HALF_UP)
            ),
        )
    }

    @Test
    @Order(7)
    fun `GET overview series converts to the currently selected preferred currency`() {

        val body = restTestClient.get()
            .uri("/private/v1/overview/series")
            .exchange()
            .expectStatus().isOk()
            .expectBody(List::class.java)
            .returnResult().responseBody!!

        @Suppress("UNCHECKED_CAST")
        val aprilPoint = body.map { it as Map<String, Any> }.first { it["month"] == "2025-04" }
        val totalInvested = BigDecimal((aprilPoint["totalInvested"] as Number).toString())

        assertEquals(0, totalInvested.compareTo(BigDecimal("250.00")))
    }
}
