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
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    private fun createHoldingWithRates(administrationFeeRate: String, performanceFeeRate: String): FundHoldingResponse =
        restTestClient.post()
            .uri("/private/v1/wallets/$walletId/fund-holdings")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""
                {
                  "fundTypeId":"$fundTypeId",
                  "name":"Fundo Multimercado",
                  "currentValue":5500.00,
                  "administrationFeeRate":$administrationFeeRate,
                  "performanceFeeRate":$performanceFeeRate,
                  "contribution":{"contributionDate":"2024-01-10","amount":5000.00}
                }
            """.trimIndent())
            .exchange()
            .expectStatus().isCreated()
            .returnResult<FundHoldingResponse>()
            .responseBody!!

    @Test
    @Order(10)
    fun `creates a fund holding with both fee rates and returns them on the next get`() {
        val holding = createHoldingWithRates("1.5", "20")

        restTestClient.get()
            .uri("/private/v1/wallets/$walletId/fund-holdings/${holding.id}")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.administrationFeeRate").isEqualTo(1.5)
            .jsonPath("$.performanceFeeRate").isEqualTo(20)
    }

    @Test
    @Order(11)
    fun `creates a fund holding without fee rates and stores null for both`() {
        val holding = createHolding("Fundo Sem Taxas")
        assertNull(holding.administrationFeeRate)
        assertNull(holding.performanceFeeRate)

        restTestClient.get()
            .uri("/private/v1/wallets/$walletId/fund-holdings/${holding.id}")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.administrationFeeRate").isEmpty()
            .jsonPath("$.performanceFeeRate").isEmpty()
    }

    @Test
    @Order(12)
    fun `creates a fund holding with only one fee rate`() {
        val holding = restTestClient.post()
            .uri("/private/v1/wallets/$walletId/fund-holdings")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""
                {
                  "fundTypeId":"$fundTypeId",
                  "name":"Fundo Taxa Unica",
                  "administrationFeeRate":0.75,
                  "contribution":{"contributionDate":"2024-01-10","amount":5000.00}
                }
            """.trimIndent())
            .exchange()
            .expectStatus().isCreated()
            .returnResult<FundHoldingResponse>()
            .responseBody!!

        assertEquals(0.75, holding.administrationFeeRate!!.toDouble())
        assertNull(holding.performanceFeeRate)
    }

    @Test
    @Order(13)
    fun `lists fee rates on fund holdings`() {
        val holding = createHoldingWithRates("2", "10")

        val listed = restTestClient.get()
            .uri("/private/v1/wallets/$walletId/fund-holdings?size=100")
            .exchange()
            .expectStatus().isOk()
            .returnResult<String>()
            .responseBody!!

        assertTrue(listed.contains(holding.id.toString()))
        assertTrue(listed.contains("\"administrationFeeRate\":2,"))
        assertTrue(listed.contains("\"performanceFeeRate\":10,"))
    }

    @Test
    @Order(14)
    fun `patching only the administration fee rate leaves everything else alone`() {
        val holding = createHoldingWithRates("1.5", "20")

        restTestClient.patch()
            .uri("/private/v1/wallets/$walletId/fund-holdings/${holding.id}")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"administrationFeeRate":2.25}""")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.administrationFeeRate").isEqualTo(2.25)
            .jsonPath("$.performanceFeeRate").isEqualTo(20)
            .jsonPath("$.name").isEqualTo("Fundo Multimercado")
            .jsonPath("$.fundTypeId").isEqualTo(fundTypeId.toString())
            .jsonPath("$.currentValue").isEqualTo(5500.00)
    }

    @Test
    @Order(15)
    fun `patching only the performance fee rate leaves the administration fee rate alone`() {
        val holding = createHoldingWithRates("1.5", "20")

        restTestClient.patch()
            .uri("/private/v1/wallets/$walletId/fund-holdings/${holding.id}")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"performanceFeeRate":15}""")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.administrationFeeRate").isEqualTo(1.5)
            .jsonPath("$.performanceFeeRate").isEqualTo(15)
    }

    @Test
    @Order(16)
    fun `patching the current value keeps the stored fee rates`() {
        val holding = createHoldingWithRates("1.5", "20")

        restTestClient.patch()
            .uri("/private/v1/wallets/$walletId/fund-holdings/${holding.id}")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"currentValue":6000.00}""")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.currentValue").isEqualTo(6000.00)
            .jsonPath("$.administrationFeeRate").isEqualTo(1.5)
            .jsonPath("$.performanceFeeRate").isEqualTo(20)
    }

    @Test
    @Order(17)
    fun `sets a fee rate on a fund holding that had none`() {
        val holding = createHolding("Fundo Legado")

        restTestClient.patch()
            .uri("/private/v1/wallets/$walletId/fund-holdings/${holding.id}")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"administrationFeeRate":0}""")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.administrationFeeRate").isEqualTo(0)
            .jsonPath("$.performanceFeeRate").isEmpty()
    }

    @Test
    @Order(18)
    fun `rejects a negative administration fee rate on create`() {
        restTestClient.post()
            .uri("/private/v1/wallets/$walletId/fund-holdings")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""
                {
                  "fundTypeId":"$fundTypeId",
                  "name":"Fundo Invalido",
                  "administrationFeeRate":-0.5,
                  "contribution":{"contributionDate":"2024-01-10","amount":5000.00}
                }
            """.trimIndent())
            .exchange()
            .expectStatus().isBadRequest()
    }

    @Test
    @Order(19)
    fun `rejects a negative performance fee rate on create`() {
        restTestClient.post()
            .uri("/private/v1/wallets/$walletId/fund-holdings")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""
                {
                  "fundTypeId":"$fundTypeId",
                  "name":"Fundo Invalido",
                  "performanceFeeRate":-1,
                  "contribution":{"contributionDate":"2024-01-10","amount":5000.00}
                }
            """.trimIndent())
            .exchange()
            .expectStatus().isBadRequest()
    }

    @Test
    @Order(20)
    fun `rejects a negative fee rate on update`() {
        val holding = createHoldingWithRates("1.5", "20")

        restTestClient.patch()
            .uri("/private/v1/wallets/$walletId/fund-holdings/${holding.id}")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"administrationFeeRate":-1}""")
            .exchange()
            .expectStatus().isBadRequest()

        restTestClient.patch()
            .uri("/private/v1/wallets/$walletId/fund-holdings/${holding.id}")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"performanceFeeRate":-1}""")
            .exchange()
            .expectStatus().isBadRequest()

        restTestClient.get()
            .uri("/private/v1/wallets/$walletId/fund-holdings/${holding.id}")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.administrationFeeRate").isEqualTo(1.5)
            .jsonPath("$.performanceFeeRate").isEqualTo(20)
    }
    @Test
    @Order(21)
    fun `returns 404 with the holding id when fetching an unknown holding`() {
        val unknownHoldingId = UUID.randomUUID()

        restTestClient.get()
            .uri("/private/v1/wallets/$walletId/fund-holdings/$unknownHoldingId")
            .exchange()
            .expectStatus().isNotFound()
            .expectBody()
            .jsonPath("$.detail").isEqualTo("Posição de fundo não encontrada: $unknownHoldingId")
    }

    @Test
    @Order(22)
    fun `returns 404 with the type id when creating a holding with an unknown fund type`() {
        val unknownTypeId = UUID.randomUUID()

        restTestClient.post()
            .uri("/private/v1/wallets/$walletId/fund-holdings")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                """
                {
                  "fundTypeId":"$unknownTypeId",
                  "name":"Fundo Fantasma",
                  "currentValue":5500.00,
                  "contribution":{"contributionDate":"2024-01-10","amount":5000.00}
                }
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isNotFound()
            .expectBody()
            .jsonPath("$.detail").isEqualTo("Tipo de fundo não encontrado: $unknownTypeId")
    }

    @Test
    @Order(23)
    fun `returns 404 with the type id when updating a holding to an unknown fund type`() {
        val holding = createHolding("Fundo Tipo Inexistente")
        val unknownTypeId = UUID.randomUUID()

        restTestClient.patch()
            .uri("/private/v1/wallets/$walletId/fund-holdings/${holding.id}")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"fundTypeId":"$unknownTypeId"}""")
            .exchange()
            .expectStatus().isNotFound()
            .expectBody()
            .jsonPath("$.detail").isEqualTo("Tipo de fundo não encontrado: $unknownTypeId")
    }

    @Test
    @Order(24)
    fun `returns 404 with the holding id when updating an unknown holding`() {
        val unknownHoldingId = UUID.randomUUID()

        restTestClient.patch()
            .uri("/private/v1/wallets/$walletId/fund-holdings/$unknownHoldingId")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"currentValue":6000.00}""")
            .exchange()
            .expectStatus().isNotFound()
            .expectBody()
            .jsonPath("$.detail").isEqualTo("Posição de fundo não encontrada: $unknownHoldingId")
    }

    @Test
    @Order(25)
    fun `returns 404 with the holding id when deleting an unknown holding`() {
        val unknownHoldingId = UUID.randomUUID()

        restTestClient.delete()
            .uri("/private/v1/wallets/$walletId/fund-holdings/$unknownHoldingId")
            .exchange()
            .expectStatus().isNotFound()
            .expectBody()
            .jsonPath("$.detail").isEqualTo("Posição de fundo não encontrada: $unknownHoldingId")
    }

    @Test
    @Order(26)
    fun `returns 404 with the holding id when adding a contribution to an unknown holding`() {
        val unknownHoldingId = UUID.randomUUID()

        restTestClient.post()
            .uri("/private/v1/wallets/$walletId/fund-holdings/$unknownHoldingId/contributions")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"contributionDate":"2024-03-10","amount":1000.00}""")
            .exchange()
            .expectStatus().isNotFound()
            .expectBody()
            .jsonPath("$.detail").isEqualTo("Posição de fundo não encontrada: $unknownHoldingId")
    }

    @Test
    @Order(27)
    fun `returns 404 with the holding id when deleting a contribution of an unknown holding`() {
        val unknownHoldingId = UUID.randomUUID()

        restTestClient.delete()
            .uri("/private/v1/wallets/$walletId/fund-holdings/$unknownHoldingId/contributions/${UUID.randomUUID()}")
            .exchange()
            .expectStatus().isNotFound()
            .expectBody()
            .jsonPath("$.detail").isEqualTo("Posição de fundo não encontrada: $unknownHoldingId")
    }

    @Test
    @Order(28)
    fun `returns 404 with the contribution id when deleting an unknown contribution`() {
        val holding = createHolding("Fundo Aporte Inexistente")
        val unknownContributionId = UUID.randomUUID()

        restTestClient.delete()
            .uri("/private/v1/wallets/$walletId/fund-holdings/${holding.id}/contributions/$unknownContributionId")
            .exchange()
            .expectStatus().isNotFound()
            .expectBody()
            .jsonPath("$.detail").isEqualTo("Aporte não encontrado: $unknownContributionId")
    }

    @Test
    @Order(29)
    fun `returns 404 with the holding id when updating the contribution date of an unknown holding`() {
        val unknownHoldingId = UUID.randomUUID()

        restTestClient.patch()
            .uri("/private/v1/wallets/$walletId/fund-holdings/$unknownHoldingId/contributions/${UUID.randomUUID()}")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"contributionDate":"2024-05-20"}""")
            .exchange()
            .expectStatus().isNotFound()
            .expectBody()
            .jsonPath("$.detail").isEqualTo("Posição de fundo não encontrada: $unknownHoldingId")
    }
}
