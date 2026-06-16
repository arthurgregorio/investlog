package br.com.investlog.server.typelists.rest.controllers

import br.com.investlog.server.TestcontainersConfiguration
import br.com.investlog.server.typelists.rest.dtos.TypeResponse
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.RestTestClient

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration::class)
@AutoConfigureRestTestClient
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class FundTypeControllerTest {

    @Autowired
    lateinit var restTestClient: RestTestClient

    companion object {
        private var createdId: UUID? = null
    }

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
            .returnResult(TypeResponse::class.java)
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
}
