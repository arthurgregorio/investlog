package br.com.investlog.server.fundholdings

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.fundholdings.rest.payloads.ContributionResponse
import br.com.investlog.server.fundholdings.rest.payloads.FundHoldingResponse
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
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FundHoldingControllerTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTestClient: RestTestClient

    lateinit var walletId: UUID
    lateinit var fundTypeId: UUID

    @BeforeAll
    fun setup() {
        walletId = restTestClient.post()
            .uri("/private/v1/wallets")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Funds Wallet","kind":"funds","currency":"BRL"}""")
            .exchange()
            .returnResult<WalletResponse>()
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
    }

    private fun createHolding(name: String = "Tesouro IPCA+"): FundHoldingResponse =
        restTestClient.post()
            .uri("/private/v1/wallets/$walletId/fund-holdings")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""
                {
                  "fundTypeId":"$fundTypeId",
                  "name":"$name",
                  "currentValue":5500.00,
                  "contribution":{"contributionDate":"2024-01-10","amount":5000.00}
                }
            """.trimIndent())
            .exchange()
            .expectStatus().isCreated()
            .returnResult<FundHoldingResponse>()
            .responseBody!!

    @Test
    @Order(1)
    fun `creates a fund holding with initial contribution`() {
        val h = createHolding()
        assertNotNull(h.id)
        assertEquals("Tesouro IPCA+", h.name)
        assertEquals(1, h.contributions.size)
        assertEquals("2024-01-10", h.contributions[0].contributionDate.toString())
    }

    @Test
    @Order(2)
    fun `lists fund holdings`() {
        restTestClient.get()
            .uri("/private/v1/wallets/$walletId/fund-holdings")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.page.totalElements").isEqualTo(1)
            .jsonPath("$.content[0].name").isEqualTo("Tesouro IPCA+")
            .jsonPath("$.content[0].contributions").isArray()
    }

    @Test
    @Order(3)
    fun `adds a contribution`() {
        val h = createHolding("CDB XP")
        restTestClient.post()
            .uri("/private/v1/wallets/$walletId/fund-holdings/${h.id}/contributions")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"contributionDate":"2024-04-01","amount":2000.00}""")
            .exchange()
            .expectStatus().isCreated()
            .expectBody()
            .jsonPath("$.amount").isEqualTo(2000.00)
    }

    @Test
    @Order(4)
    fun `updates current value`() {
        val h = createHolding("LCI Banco Inter")
        restTestClient.patch()
            .uri("/private/v1/wallets/$walletId/fund-holdings/${h.id}")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"currentValue":6200.00}""")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.currentValue").isEqualTo(6200.00)
    }

    @Test
    @Order(5)
    fun `deletes a holding`() {
        val h = createHolding("FII KNRI11")
        restTestClient.delete()
            .uri("/private/v1/wallets/$walletId/fund-holdings/${h.id}")
            .exchange()
            .expectStatus().isNoContent()
    }

    @Test
    @Order(6)
    fun `deletes a contribution`() {
        val h = createHolding("Debênture Petrobras")
        val contribution = restTestClient.post()
            .uri("/private/v1/wallets/$walletId/fund-holdings/${h.id}/contributions")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"contributionDate":"2024-07-01","amount":1000.00}""")
            .exchange()
            .returnResult<ContributionResponse>()
            .responseBody!!

        restTestClient.delete()
            .uri("/private/v1/wallets/$walletId/fund-holdings/${h.id}/contributions/${contribution.id}")
            .exchange()
            .expectStatus().isNoContent()
    }

    @Test
    @Order(7)
    fun `returns 404 for unknown wallet`() {
        restTestClient.get()
            .uri("/private/v1/wallets/${UUID.randomUUID()}/fund-holdings")
            .exchange()
            .expectStatus().isNotFound()
    }

    @Test
    @Order(8)
    fun `updates a contribution's date`() {
        val h = createHolding("Tesouro Selic 2027")
        val contribution = restTestClient.post()
            .uri("/private/v1/wallets/$walletId/fund-holdings/${h.id}/contributions")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"contributionDate":"2024-05-01","amount":3000.00}""")
            .exchange()
            .returnResult<ContributionResponse>()
            .responseBody!!

        restTestClient.patch()
            .uri("/private/v1/wallets/$walletId/fund-holdings/${h.id}/contributions/${contribution.id}")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"contributionDate":"2024-05-20"}""")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.contributionDate").isEqualTo("2024-05-20")
    }

    @Test
    @Order(9)
    fun `returns 404 when updating an unknown contribution's date`() {
        val h = createHolding("Tesouro Prefixado 2030")
        restTestClient.patch()
            .uri("/private/v1/wallets/$walletId/fund-holdings/${h.id}/contributions/${UUID.randomUUID()}")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"contributionDate":"2024-05-20"}""")
            .exchange()
            .expectStatus().isNotFound()
    }
}
