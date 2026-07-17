package br.com.investlog.server.auth.rest.controllers

import br.com.investlog.server.RestClientTestConfiguration
import br.com.investlog.server.TestcontainersConfiguration
import br.com.investlog.server.auth.rest.payloads.SessionResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.returnResult
import kotlin.test.Test
import kotlin.test.assertEquals

@AutoConfigureRestTestClient
@ActiveProfiles("test")
@Import(value = [TestcontainersConfiguration::class, RestClientTestConfiguration::class])
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["investlog.totp-required=false"],
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuthControllerTotpNotRequiredTest {

    @Autowired
    lateinit var restTestClient: RestTestClient

    @Test
    fun `login authenticates immediately without enrollment or a totp code when totp is not required`() {
        val response = restTestClient.post()
            .uri("/private/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"email":"admin@admin.com","password":"admin"}""")
            .exchange()
            .expectStatus().isOk()
            .returnResult<SessionResponse>()
            .responseBody

        assertEquals("admin@admin.com", response?.email)
    }
}
