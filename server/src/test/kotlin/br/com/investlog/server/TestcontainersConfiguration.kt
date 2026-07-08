package br.com.investlog.server

import dev.samstevens.totp.code.DefaultCodeGenerator
import org.springframework.boot.resttestclient.autoconfigure.RestTestClientBuilderCustomizer
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    fun postgresContainer(): PostgreSQLContainer = PostgreSQLContainer(DockerImageName.parse(POSTGRES_IMAGE))

    /**
     * Registers an interceptor on the autoconfigured [org.springframework.test.web.servlet.client.RestTestClient]
     * builder so that every request issued through any test class's `@Autowired restTestClient` field
     * (there are ~13 of them, none of which can be edited for this task) automatically carries an
     * authenticated admin session cookie. The admin session is established lazily, on first use, by
     * issuing raw login/enroll/verify calls against the same host/port as the outgoing request, since
     * the embedded web server is not yet listening while this `@TestConfiguration`'s beans are being
     * created. Mandatory TOTP means a plain login now returns 202 "needs_enrollment" for a fresh admin
     * row, so this interceptor completes the enroll+verify dance itself, computing a valid code with
     * the same TOTP library the server uses.
     */
    @Bean
    fun adminSessionRestTestClientBuilderCustomizer(): RestTestClientBuilderCustomizer =
        RestTestClientBuilderCustomizer { builder -> builder.requestInterceptor(AdminSessionCookieInterceptor()) }

    private class AdminSessionCookieInterceptor : ClientHttpRequestInterceptor {

        @Volatile
        private var adminSessionCookie: String? = null

        override fun intercept(
            request: org.springframework.http.HttpRequest,
            body: ByteArray,
            execution: org.springframework.http.client.ClientHttpRequestExecution,
        ): org.springframework.http.client.ClientHttpResponse {
            val isAuthEndpoint = request.method == HttpMethod.POST &&
                (request.uri.path.endsWith(LOGIN_PATH) || request.uri.path.endsWith(ENROLL_PATH) || request.uri.path.endsWith(VERIFY_PATH))
            if (!isAuthEndpoint && request.headers.getFirst(HttpHeaders.COOKIE) == null) {
                request.headers.set(HttpHeaders.COOKIE, adminSessionCookieFor(request.uri))
            }
            return execution.execute(request, body)
        }

        private fun adminSessionCookieFor(requestUri: URI): String =
            adminSessionCookie ?: synchronized(this) {
                adminSessionCookie ?: loginAsAdmin(requestUri).also { adminSessionCookie = it }
            }

        private fun loginAsAdmin(requestUri: URI): String {
            val httpClient = HttpClient.newHttpClient()

            val loginResponse = postJson(httpClient, requestUri, LOGIN_PATH, ADMIN_CREDENTIALS_JSON)

            if (loginResponse.statusCode() == 200) {
                return loginResponse.setCookie()
            }

            check(loginResponse.statusCode() == 202) {
                "Admin login failed with status ${loginResponse.statusCode()} while authenticating the shared test RestTestClient"
            }

            val enrollResponse = postJson(httpClient, requestUri, ENROLL_PATH, ADMIN_CREDENTIALS_JSON)
            check(enrollResponse.statusCode() == 200) {
                "Admin TOTP enrollment failed with status ${enrollResponse.statusCode()}"
            }

            val secret = extractJsonField(enrollResponse.body(), "secretKey")
            val code = DefaultCodeGenerator().generate(secret, System.currentTimeMillis() / 1000L / 30L)

            val verifyResponse = postJson(
                httpClient,
                requestUri,
                VERIFY_PATH,
                """{"email":"admin@admin.com","password":"admin","code":"$code"}""",
            )
            check(verifyResponse.statusCode() == 200) {
                "Admin TOTP verification failed with status ${verifyResponse.statusCode()}"
            }

            return verifyResponse.setCookie()
        }

        private fun postJson(httpClient: HttpClient, requestUri: URI, path: String, jsonBody: String): HttpResponse<String> {
            val targetUri = URI(requestUri.scheme, null, requestUri.host, requestUri.port, path, null, null)
            val httpRequest = HttpRequest.newBuilder(targetUri)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build()
            return httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())
        }

        private fun HttpResponse<String>.setCookie(): String =
            headers().firstValue("Set-Cookie")
                .orElseThrow { IllegalStateException("Expected a session cookie in the response from ${uri()}") }
                .substringBefore(";")

        private fun extractJsonField(json: String, field: String): String {
            val regex = """"$field"\s*:\s*"([^"]+)"""".toRegex()
            return regex.find(json)?.groupValues?.get(1)
                ?: error("Field \"$field\" not found in response body: $json")
        }

        companion object {
            private const val LOGIN_PATH = "/private/v1/auth/login"
            private const val ENROLL_PATH = "/private/v1/auth/totp/enroll"
            private const val VERIFY_PATH = "/private/v1/auth/totp/verify"
            private const val ADMIN_CREDENTIALS_JSON = """{"email":"admin@admin.com","password":"admin"}"""
        }
    }

    companion object {
        const val POSTGRES_IMAGE = "postgres:18-alpine"
    }
}
