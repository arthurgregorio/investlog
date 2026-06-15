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
class StockTypeControllerTest {

    @Autowired
    lateinit var restTestClient: RestTestClient

    companion object {
        private var createdId: UUID? = null
    }

    @Test
    @Order(1)
    fun `returns an empty page initially`() {
        restTestClient.get()
            .uri("/private/v1/stock-types")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.page.totalElements").isEqualTo(0)
            .jsonPath("$.page.size").isEqualTo(20)
            .jsonPath("$.content").isArray()
    }

    @Test
    @Order(2)
    fun `creates a stock type`() {
        val response = restTestClient.post()
            .uri("/private/v1/stock-types")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Acoes Brasil"}""")
            .exchange()
            .expectStatus().isCreated()
            .returnResult(TypeResponse::class.java)
            .responseBody

        assertEquals("Acoes Brasil", response?.name)
        createdId = response?.id
    }

    @Test
    @Order(3)
    fun `lists the created stock type`() {
        restTestClient.get()
            .uri("/private/v1/stock-types")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.page.totalElements").isEqualTo(1)
            .jsonPath("$.content[0].name").isEqualTo("Acoes Brasil")
    }

    @Test
    @Order(4)
    fun `rejects a duplicate name with 409`() {
        restTestClient.post()
            .uri("/private/v1/stock-types")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Acoes Brasil"}""")
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.CONFLICT)
    }

    @Test
    @Order(5)
    fun `rejects a blank name with 400`() {
        restTestClient.post()
            .uri("/private/v1/stock-types")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":""}""")
            .exchange()
            .expectStatus().isBadRequest()
    }

    @Test
    @Order(6)
    fun `deletes the created stock type`() {
        restTestClient.delete()
            .uri("/private/v1/stock-types/${createdId}")
            .exchange()
            .expectStatus().isNoContent()
    }

    @Test
    @Order(7)
    fun `returns 404 when deleting an unknown id`() {
        restTestClient.delete()
            .uri("/private/v1/stock-types/${UUID.randomUUID()}")
            .exchange()
            .expectStatus().isNotFound()
    }
}
