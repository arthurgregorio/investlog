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
import kotlin.test.assertNotEquals

@TestPropertySource(
    properties = [
        "server.forward-headers-strategy=native",
        "investlog.security.ip-lockout.max-attempts=3",
        "investlog.security.ip-lockout.base-duration=1h",
        "investlog.security.login.lockout-max-attempts=3",
        "investlog.security.login.lockout-base-duration=1h",
    ],
)
class IpRateLimitTest : BaseIntegrationTest() {

    @LocalServerPort
    var port: Int = 0

    private val httpClient = HttpClient.newHttpClient()

    private fun login(email: String, password: String, clientIp: String, forwardedFor: String = clientIp): Int {
        val request = HttpRequest.newBuilder(URI("http://localhost:$port/private/v1/auth/login"))
            .header("Content-Type", "application/json")
            .header("X-Forwarded-For", forwardedFor)
            .POST(HttpRequest.BodyPublishers.ofString("""{"email":"$email","password":"$password"}"""))
            .build()
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString()).statusCode()
    }

    @Test
    fun `spraying one password across many accounts locks the address out`() {
        val sprayer = "203.0.113.10"

        assertEquals(401, login("nobody-1@example.com", "Senha123", sprayer))
        assertEquals(401, login("nobody-2@example.com", "Senha123", sprayer))
        assertEquals(401, login("nobody-3@example.com", "Senha123", sprayer))

        assertEquals(429, login("nobody-4@example.com", "Senha123", sprayer))
        assertEquals(429, login("admin@admin.com", "admin", sprayer))
    }

    @Test
    fun `another address is not affected by a locked one`() {
        repeat(3) { login("nobody-$it@example.com", "Senha123", "203.0.113.20") }

        assertNotEquals(429, login("admin@admin.com", "admin", "203.0.113.21"))
    }

    @Test
    fun `an attacker cannot lock a known account out for everyone else`() {
        val attacker = "203.0.113.30"

        repeat(3) { assertEquals(401, login("admin@admin.com", "wrong", attacker)) }
        assertEquals(429, login("admin@admin.com", "admin", attacker))

        assertNotEquals(429, login("admin@admin.com", "admin", "203.0.113.31"))
    }

    @Test
    fun `forging the leftmost forwarded address does not evade the limit`() {
        val sprayer = "203.0.113.40"

        repeat(3) { attempt ->
            login("nobody-$attempt@example.com", "Senha123", sprayer, forwardedFor = "10.0.0.$attempt, $sprayer")
        }

        assertEquals(429, login("nobody-9@example.com", "Senha123", sprayer, forwardedFor = "10.9.9.9, $sprayer"))
    }
}
