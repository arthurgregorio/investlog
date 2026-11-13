package br.com.investlog.server.auth.rest

import br.com.investlog.server.BaseIntegrationTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.TestPropertySource
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@TestPropertySource(
    properties = [
        "server.forward-headers-strategy=native",
        "investlog.security.registration-limit.max-attempts=3",
        "investlog.security.registration-limit.base-duration=1h",
    ],
)
class RegistrationAbuseTest : BaseIntegrationTest() {

    @LocalServerPort
    var port: Int = 0

    private val httpClient = HttpClient.newHttpClient()

    private fun register(
        name: String,
        email: String,
        clientIp: String,
        password: String = "Senha123",
    ): HttpResponse<String> {
        val request = HttpRequest.newBuilder(URI("http://localhost:$port/private/v1/auth/register"))
            .header("Content-Type", "application/json")
            .header("X-Forwarded-For", clientIp)
            .POST(HttpRequest.BodyPublishers.ofString("""{"name":"$name","email":"$email","password":"$password"}"""))
            .build()
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    }

    @Test
    fun `a name beyond the allowed length is rejected`() {
        val response = register("a".repeat(64 * 1024), "longname@example.com", "203.0.113.50")

        assertEquals(400, response.statusCode())
    }

    @Test
    fun `an email beyond the allowed length is rejected`() {
        val response = register("Nome Longo", "a".repeat(300) + "@example.com", "203.0.113.51")

        assertEquals(400, response.statusCode())
    }

    @Test
    fun `a password beyond the allowed length is rejected`() {
        val response = register("Nome", "longpassword@example.com", "203.0.113.52", password = "Aa1" + "a".repeat(200))

        assertEquals(400, response.statusCode())
    }

    @Test
    fun `a name and an email at the allowed length are accepted`() {
        val email = "a".repeat(64) + "@" + "b".repeat(185) + ".com"

        val response = register("n".repeat(100), email, "203.0.113.53")

        assertEquals(201, response.statusCode())
    }

    @Test
    fun `rapid registrations from one address are throttled`() {
        val abuser = "203.0.113.60"

        repeat(3) { assertEquals(201, register("Pessoa $it", "burst-$it@example.com", abuser).statusCode()) }

        val throttled = register("Pessoa 4", "burst-4@example.com", abuser)

        assertEquals(429, throttled.statusCode())
        assertTrue(throttled.body().contains("too_many_registration_attempts"))
    }

    @Test
    fun `another address is not affected by a throttled one`() {
        repeat(3) { register("Pessoa $it", "shared-$it@example.com", "203.0.113.70") }

        assertEquals(201, register("Pessoa", "shared-other@example.com", "203.0.113.71").statusCode())
    }

    @Test
    fun `registrations under the limit keep returning created`() {
        val visitor = "203.0.113.80"

        assertEquals(201, register("Primeira", "normal-1@example.com", visitor).statusCode())
        assertEquals(201, register("Segunda", "normal-2@example.com", visitor).statusCode())
    }
}
