package br.com.investlog.server.auth.rest

import br.com.investlog.server.BaseIntegrationTest
import dev.samstevens.totp.code.DefaultCodeGenerator
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.TestPropertySource
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@TestPropertySource(properties = ["server.forward-headers-strategy=native"])
class SecureCookiesTest : BaseIntegrationTest() {

    @LocalServerPort
    var port: Int = 0

    private val httpClient = HttpClient.newHttpClient()

    private fun post(path: String, body: String, headers: Map<String, String> = emptyMap()): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI("http://localhost:$port$path"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
        headers.forEach { (name, value) -> builder.header(name, value) }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun HttpResponse<String>.setCookies(): List<String> = headers().allValues("Set-Cookie")

    private fun HttpResponse<String>.setCookie(name: String): String =
        setCookies().firstOrNull { it.startsWith("$name=") } ?: error("No $name cookie among ${setCookies()}")

    private fun enrolledAdminTotpSecret(): String {
        assertEquals(202, post(LOGIN_PATH, ADMIN_CREDENTIALS).statusCode())
        val enrollment = post(ENROLL_PATH, ADMIN_CREDENTIALS)
        val secret = """"secretKey"\s*:\s*"([^"]+)"""".toRegex().find(enrollment.body())!!.groupValues[1]
        val verification = post(
            VERIFY_PATH,
            """{"email":"admin@admin.com","password":"admin","code":"${currentTotpCode(secret)}"}""",
        )
        assertEquals(200, verification.statusCode())
        return secret
    }

    private fun currentTotpCode(secret: String): String =
        DefaultCodeGenerator().generate(secret, System.currentTimeMillis() / 1000L / 30L)

    @Test
    fun `session and trusted device cookies are Secure only when the proxy reports https`() {
        val secret = enrolledAdminTotpSecret()

        val secureLogin = post(
            LOGIN_PATH,
            """{"email":"admin@admin.com","password":"admin","totpCode":"${currentTotpCode(secret)}","trustDevice":true}""",
            mapOf("X-Forwarded-Proto" to "https"),
        )

        assertEquals(200, secureLogin.statusCode())
        assertTrue(secureLogin.setCookie("JSESSIONID").contains("Secure"), secureLogin.setCookies().toString())
        assertTrue(secureLogin.setCookie("trusted_device").contains("Secure"), secureLogin.setCookies().toString())

        val trustedDeviceCookie = secureLogin.setCookie("trusted_device").substringBefore(";")
        val plainLogin = post(LOGIN_PATH, ADMIN_CREDENTIALS, mapOf("Cookie" to trustedDeviceCookie))

        assertEquals(200, plainLogin.statusCode())
        assertFalse(plainLogin.setCookie("JSESSIONID").contains("Secure"), plainLogin.setCookies().toString())
    }

    companion object {
        private const val LOGIN_PATH = "/private/v1/auth/login"
        private const val ENROLL_PATH = "/private/v1/auth/totp/enroll"
        private const val VERIFY_PATH = "/private/v1/auth/totp/verify"
        private const val ADMIN_CREDENTIALS = """{"email":"admin@admin.com","password":"admin"}"""
    }
}
