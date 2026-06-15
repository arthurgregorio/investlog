package br.com.investlog.server.profile.rest.controllers

import br.com.investlog.server.TestcontainersConfiguration
import br.com.investlog.server.profile.rest.dtos.ProfileResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.RestTestClient

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration::class)
@AutoConfigureRestTestClient
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class ProfileControllerTest {

    @Autowired
    lateinit var restTestClient: RestTestClient

    @Test
    @Order(1)
    fun `returns the current user's profile`() {
        val response = restTestClient.get()
            .uri("/private/v1/profile")
            .exchange()
            .expectStatus().isOk()
            .returnResult(ProfileResponse::class.java)
            .responseBody

        assertEquals("Arthur Gregorio", response?.name)
        assertEquals("arthurshakal@gmail.com", response?.email)
        assertEquals("teal", response?.accentColor)
        assertEquals("BRL", response?.preferredCurrency)
    }

    @Test
    @Order(2)
    fun `updates accent color and preserves preferred currency`() {
        val response = restTestClient.patch()
            .uri("/private/v1/profile")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"accentColor":"indigo"}""")
            .exchange()
            .expectStatus().isOk()
            .returnResult(ProfileResponse::class.java)
            .responseBody

        assertEquals("indigo", response?.accentColor)
        assertEquals("BRL", response?.preferredCurrency)
    }

    @Test
    @Order(3)
    fun `rejects an invalid accent color`() {
        restTestClient.patch()
            .uri("/private/v1/profile")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"accentColor":"purple"}""")
            .exchange()
            .expectStatus().isBadRequest()
    }
}
