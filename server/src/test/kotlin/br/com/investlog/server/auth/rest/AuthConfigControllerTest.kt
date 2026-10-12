package br.com.investlog.server.auth.rest

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.RestClientTestConfiguration
import br.com.investlog.server.TestcontainersConfiguration
import br.com.investlog.server.auth.rest.payloads.AuthConfigResponse
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.NestedTestConfiguration
import org.springframework.test.context.NestedTestConfiguration.EnclosingConfiguration
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.returnResult
import kotlin.test.assertEquals

private const val NO_SESSION_COOKIE = "anonymous=true"

private fun RestTestClient.fetchConfigBody(): String? =
    get()
        .uri("/private/v1/auth/config")
        .header(HttpHeaders.COOKIE, NO_SESSION_COOKIE)
        .exchange()
        .expectStatus().isOk()
        .returnResult<String>()
        .responseBody

private fun RestTestClient.fetchConfig(): AuthConfigResponse? =
    get()
        .uri("/private/v1/auth/config")
        .header(HttpHeaders.COOKIE, NO_SESSION_COOKIE)
        .exchange()
        .expectStatus().isOk()
        .returnResult<AuthConfigResponse>()
        .responseBody

class AuthConfigControllerTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTestClient: RestTestClient

    @Test
    fun `config reports google auth as disabled by default`() {
        val response = restTestClient.fetchConfig()

        assertEquals(false, response?.googleAuthEnabled)
    }

    @Test
    fun `config reports demo mode as disabled by default`() {
        val response = restTestClient.fetchConfig()

        assertEquals(false, response?.demoModeEnabled)
    }

    @Test
    fun `config carries only the two flags and no credentials`() {
        val body = restTestClient.fetchConfigBody()

        assertEquals("""{"googleAuthEnabled":false,"demoModeEnabled":false}""", body)
    }

    @Nested
    @NestedTestConfiguration(EnclosingConfiguration.OVERRIDE)
    @AutoConfigureRestTestClient
    @ActiveProfiles("test")
    @Import(value = [TestcontainersConfiguration::class, RestClientTestConfiguration::class])
    @SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = ["investlog.demo-mode.enabled=true"],
    )
    @DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
    inner class WhenDemoModeIsEnabled {

        @Autowired
        lateinit var restTestClient: RestTestClient

        @Test
        fun `config reports demo mode as enabled without a session`() {
            val response = restTestClient.fetchConfig()

            assertEquals(true, response?.demoModeEnabled)
            assertEquals(false, response?.googleAuthEnabled)
        }

        @Test
        fun `config still carries no credentials in demo mode`() {
            val body = restTestClient.fetchConfigBody()

            assertEquals("""{"googleAuthEnabled":false,"demoModeEnabled":true}""", body)
        }
    }
}

