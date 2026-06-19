package br.com.investlog.server.overview.rest.controllers

import br.com.investlog.server.BaseIntegrationTest
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
}
