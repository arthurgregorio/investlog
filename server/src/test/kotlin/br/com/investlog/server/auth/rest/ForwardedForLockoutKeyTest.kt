package br.com.investlog.server.auth.rest

import br.com.investlog.server.BaseIntegrationTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@ActiveProfiles("prod")
@TestPropertySource(
    properties = [
        "DB_HOST=localhost",
        "DB_PORT=5432",
        "DB_NAME=investlog",
        "DB_USER=investlog",
        "DB_PASSWORD=investlog",
        "investlog.security.login.lockout-max-attempts=3",
        "investlog.security.login.lockout-base-duration=1h",
        "investlog.security.ip-lockout.max-attempts=50",
        "investlog.security.ip-lockout.base-duration=1h",
    ],
)
class ForwardedForLockoutKeyTest : BaseIntegrationTest() {

    @LocalServerPort
    var port: Int = 0

    private val httpClient = HttpClient.newHttpClient()

    private fun login(realIp: String, forwardedFor: String, password: String): Int {
        val request = HttpRequest.newBuilder(URI("http://localhost:$port/private/v1/auth/login"))
            .header("Content-Type", "application/json")
            .header("X-Real-IP", realIp)
            .header("X-Forwarded-For", forwardedFor)
            .POST(HttpRequest.BodyPublishers.ofString("""{"email":"admin@admin.com","password":"$password"}"""))
            .build()
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString()).statusCode()
    }

    private fun failedLogin(realIp: String, forwardedFor: String) = login(realIp, forwardedFor, "wrong")

    @Test
    fun `a forged forwarded header cannot shift the lockout bucket`() {
        assertEquals(401, failedLogin(realIp = "172.18.0.1", forwardedFor = "198.51.100.1"))
        assertEquals(401, failedLogin(realIp = "172.18.0.1", forwardedFor = "198.51.100.2"))
        assertEquals(401, failedLogin(realIp = "172.18.0.1", forwardedFor = "198.51.100.3"))

        assertEquals(429, failedLogin(realIp = "172.18.0.1", forwardedFor = "198.51.100.4"))
    }

    @Test
    fun `the real client address comes from X-Real-IP and is isolated per address`() {
        assertEquals(401, failedLogin(realIp = "203.0.113.9", forwardedFor = "10.9.9.9"))
        assertEquals(401, failedLogin(realIp = "203.0.113.9", forwardedFor = "10.9.9.9"))
        assertEquals(401, failedLogin(realIp = "203.0.113.9", forwardedFor = "10.9.9.9"))
        assertEquals(429, failedLogin(realIp = "203.0.113.9", forwardedFor = "10.9.9.9"))

        assertNotEquals(429, login(realIp = "203.0.113.77", forwardedFor = "10.9.9.9", password = "admin"))
    }
}
