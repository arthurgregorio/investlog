package br.com.investlog.server.auth.rest

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.auth.rest.payloads.AuthConfigResponse
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.returnResult
import kotlin.test.assertEquals

class AuthConfigControllerTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTestClient: RestTestClient

    @Test
    fun `config reports google auth as disabled by default`() {
        val response = restTestClient.get()
            .uri("/private/v1/auth/config")
            .exchange()
            .expectStatus().isOk()
            .returnResult<AuthConfigResponse>()
            .responseBody

        assertEquals(false, response?.googleAuthEnabled)
    }
}
