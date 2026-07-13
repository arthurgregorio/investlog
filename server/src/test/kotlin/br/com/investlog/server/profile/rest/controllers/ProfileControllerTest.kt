package br.com.investlog.server.profile.rest.controllers

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.profile.rest.payloads.ProfileResponse
import org.junit.jupiter.api.Order
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.returnResult
import kotlin.test.Test
import kotlin.test.assertEquals

class ProfileControllerTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTestClient: RestTestClient

    @Test
    @Order(1)
    fun `returns the current user's profile`() {

        val response = restTestClient.get()
            .uri("/private/v1/profile")
            .exchange()
            .expectStatus().isOk()
            .returnResult<ProfileResponse>()
            .responseBody

        assertEquals("Administrador", response?.name)
        assertEquals("admin@admin.com", response?.email)
        assertEquals("teal", response?.accentColor?.text)
        assertEquals("BRL", response?.preferredCurrency)
    }

    @Test
    @Order(2)
    fun `updates accent color and preserves preferred currency`() {

        val response = restTestClient.patch()
            .uri("/private/v1/profile")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"accentColor":"INDIGO"}""")
            .exchange()
            .expectStatus().isOk()
            .returnResult<ProfileResponse>()
            .responseBody

        assertEquals("indigo", response?.accentColor?.text)
        assertEquals("BRL", response?.preferredCurrency)
    }

    @Test
    @Order(3)
    fun `updates preferred currency and preserves the previously set accent color`() {

        val response = restTestClient.patch()
            .uri("/private/v1/profile")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"preferredCurrency":"USD"}""")
            .exchange()
            .expectStatus().isOk()
            .returnResult<ProfileResponse>()
            .responseBody

        assertEquals("indigo", response?.accentColor?.text)
        assertEquals("USD", response?.preferredCurrency)
    }

    @Test
    @Order(4)
    fun `rejects an invalid accent color`() {
        restTestClient.patch()
            .uri("/private/v1/profile")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"accentColor":"purple"}""")
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.title").isEqualTo("Bad Request")
    }
}
