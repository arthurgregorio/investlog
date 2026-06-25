package br.com.investlog.server.holdingsoverview.rest.controllers

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
class HoldingsOverviewControllerTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTestClient: RestTestClient

    lateinit var walletId: UUID
    lateinit var stockTypeId: UUID
    lateinit var holdingId: UUID

    @BeforeAll
    fun setup() {
        walletId = restTestClient.post()
            .uri("/private/v1/wallets")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Overview Test Wallet","kind":"STOCKS","currency":"BRL"}""")
            .exchange()
            .returnResult<WalletResponse>()
            .responseBody!!
            .id

        stockTypeId = restTestClient.post()
            .uri("/private/v1/stock-types")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Overview Test Type"}""")
            .exchange()
            .returnResult<TypeResponse>()
            .responseBody!!
            .id

        holdingId = restTestClient.post()
            .uri("/private/v1/wallets/$walletId/stock-holdings")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                """{"stockTypeId":"$stockTypeId","ticker":"OVTST3",
                   "currentPrice":50.00,
                   "lot":{"lotDate":"2025-01-15","quantity":10,"price":45.00}}"""
            )
            .exchange()
            .expectStatus().isCreated()
            .returnResult<StockHoldingResponse>()
            .responseBody!!
            .id
    }

    @Test
    @Order(1)
    fun `GET holdings returns the created holding with computed gain`() {
        restTestClient.get()
            .uri("/private/v1/holdings")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content[?(@.id == '$holdingId')].ticker").isEqualTo("OVTST3")
            .jsonPath("$.content[?(@.id == '$holdingId')].kind").isEqualTo("STOCKS")
            .jsonPath("$.content[?(@.id == '$holdingId')].costBasis").isEqualTo(450.0)
            .jsonPath("$.content[?(@.id == '$holdingId')].currentValue").isEqualTo(500.0)
            .jsonPath("$.content[?(@.id == '$holdingId')].gain").isEqualTo(50.0)
            .jsonPath("$.content[?(@.id == '$holdingId')].walletName").isEqualTo("Overview Test Wallet")
    }

    @Test
    @Order(2)
    fun `GET holdings with kind=stocks includes the created holding`() {
        restTestClient.get()
            .uri("/private/v1/holdings?kind=STOCKS")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content[?(@.id == '$holdingId')]").isNotEmpty()
    }

    @Test
    @Order(3)
    fun `GET holdings with kind=funds excludes the stocks holding`() {
        restTestClient.get()
            .uri("/private/v1/holdings?kind=FUNDS")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content[?(@.id == '$holdingId')]").isEmpty()
    }

    @Test
    @Order(4)
    fun `GET holdings with invalid kind returns 400`() {
        restTestClient.get()
            .uri("/private/v1/holdings?kind=invalid")
            .exchange()
            .expectStatus().isBadRequest()
    }

    @Test
    @Order(5)
    fun `GET holdings with matching typeLabel includes the holding`() {
        restTestClient.get()
            .uri("/private/v1/holdings?typeLabel={typeLabel}", "Overview Test Type")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content[?(@.id == '$holdingId')]").isNotEmpty()
    }

    @Test
    @Order(6)
    fun `GET holdings with non-matching typeLabel excludes the holding`() {
        restTestClient.get()
            .uri("/private/v1/holdings?typeLabel={typeLabel}", "Nonexistent Type")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content[?(@.id == '$holdingId')]").isEmpty()
    }

    @Test
    @Order(7)
    fun `GET holdings with matching walletId includes the holding`() {
        restTestClient.get()
            .uri("/private/v1/holdings?walletId={walletId}", walletId)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content[?(@.id == '$holdingId')]").isNotEmpty()
    }

    @Test
    @Order(8)
    fun `GET holdings with non-matching walletId excludes the holding`() {
        restTestClient.get()
            .uri("/private/v1/holdings?walletId={walletId}", UUID.randomUUID())
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content[?(@.id == '$holdingId')]").isEmpty()
    }

    @Test
    @Order(9)
    fun `GET holdings with matching search includes the holding`() {
        restTestClient.get()
            .uri("/private/v1/holdings?search=ovtst")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content[?(@.id == '$holdingId')]").isNotEmpty()
    }

    @Test
    @Order(10)
    fun `GET holdings with non-matching search excludes the holding`() {
        restTestClient.get()
            .uri("/private/v1/holdings?search=nomatch")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content[?(@.id == '$holdingId')]").isEmpty()
    }

    @Test
    @Order(11)
    fun `GET holdings sorted by invested ascending orders the smaller position first`() {
        // costBasis = 1 * 10.00 = 10.00, versus the @BeforeAll fixture's OVTST3 at 450.00 —
        // scoping by typeLabel to just these two stockTypeId siblings keeps content[0]
        // deterministic regardless of any other holdings the dev user already has.
        restTestClient.post()
            .uri("/private/v1/wallets/$walletId/stock-holdings")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                """{"stockTypeId":"$stockTypeId","ticker":"OVSML3",
                   "currentPrice":12.00,
                   "lot":{"lotDate":"2025-02-01","quantity":1,"price":10.00}}"""
            )
            .exchange()
            .expectStatus().isCreated()

        restTestClient.get()
            .uri("/private/v1/holdings?typeLabel={typeLabel}&sort=invested,asc", "Overview Test Type")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content[0].ticker").isEqualTo("OVSML3")
    }
}
