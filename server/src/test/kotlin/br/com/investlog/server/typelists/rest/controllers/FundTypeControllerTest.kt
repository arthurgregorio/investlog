package br.com.investlog.server.typelists.rest.controllers

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.typelists.rest.payloads.TypeResponse
import org.junit.jupiter.api.Order
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.returnResult
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class FundTypeControllerTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTestClient: RestTestClient

    @Test
    @Order(1)
    fun `returns an empty page initially`() {
        restTestClient.get()
            .uri("/private/v1/fund-types")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.page.totalElements").isEqualTo(0)
            .jsonPath("$.content").isArray()
    }

    @Test
    @Order(2)
    fun `creates a fund type`() {
        val response = restTestClient.post()
            .uri("/private/v1/fund-types")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Renda Fixa"}""")
            .exchange()
            .expectStatus().isCreated()
            .returnResult<TypeResponse>()
            .responseBody

        assertEquals("Renda Fixa", response?.name)
        createdId = response?.id
    }

    @Test
    @Order(3)
    fun `lists the created fund type`() {
        restTestClient.get()
            .uri("/private/v1/fund-types")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.page.totalElements").isEqualTo(1)
            .jsonPath("$.content[0].name").isEqualTo("Renda Fixa")
    }

    @Test
    @Order(4)
    fun `rejects a duplicate name with 409`() {
        restTestClient.post()
            .uri("/private/v1/fund-types")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Renda Fixa"}""")
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.CONFLICT)
    }

    @Test
    @Order(5)
    fun `deletes the created fund type`() {
        restTestClient.delete()
            .uri("/private/v1/fund-types/${createdId}")
            .exchange()
            .expectStatus().isNoContent()
    }

    @Test
    @Order(6)
    fun `returns 404 when deleting an unknown id`() {
        restTestClient.delete()
            .uri("/private/v1/fund-types/${UUID.randomUUID()}")
            .exchange()
            .expectStatus().isNotFound()
    }

    companion object {
        private var createdId: UUID? = null
    }
}
