package br.com.investlog.server.auth.rest.controllers

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.auth.rest.payloads.SessionResponse
import org.junit.jupiter.api.Order
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.returnResult
import kotlin.test.Test
import kotlin.test.assertEquals

class AuthControllerTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTestClient: RestTestClient

    @Test
    @Order(1)
    fun `rejects an unknown email`() {
        restTestClient.post()
            .uri("/private/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"email":"nobody@example.com","password":"whatever"}""")
            .exchange()
            .expectStatus().isUnauthorized()
    }

    @Test
    @Order(2)
    fun `rejects the wrong password`() {
        restTestClient.post()
            .uri("/private/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"email":"admin@admin.com","password":"wrong"}""")
            .exchange()
            .expectStatus().isUnauthorized()
    }

    @Test
    @Order(3)
    fun `logs in with the seeded admin's credentials and reports the session`() {
        val response = restTestClient.post()
            .uri("/private/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"email":"admin@admin.com","password":"admin"}""")
            .exchange()
            .expectStatus().isOk()
            .returnResult<SessionResponse>()
            .responseBody

        assertEquals("Administrador", response?.name)
        assertEquals("admin@admin.com", response?.email)
    }
}
