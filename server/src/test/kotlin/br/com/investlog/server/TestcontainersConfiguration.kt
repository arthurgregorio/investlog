package br.com.investlog.server

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
     * issuing a raw login call against the same host/port as the outgoing request, since the embedded
     * web server is not yet listening while this `@TestConfiguration`'s beans are being created.
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
            val isLoginRequest = request.method == HttpMethod.POST && request.uri.path.endsWith(LOGIN_PATH)
            if (!isLoginRequest && request.headers.getFirst(HttpHeaders.COOKIE) == null) {
                request.headers.set(HttpHeaders.COOKIE, adminSessionCookieFor(request.uri))
            }
            return execution.execute(request, body)
        }

        private fun adminSessionCookieFor(requestUri: URI): String =
            adminSessionCookie ?: synchronized(this) {
                adminSessionCookie ?: loginAsAdmin(requestUri).also { adminSessionCookie = it }
            }

        private fun loginAsAdmin(requestUri: URI): String {
            val loginUri = URI(requestUri.scheme, null, requestUri.host, requestUri.port, LOGIN_PATH, null, null)
            val httpRequest = HttpRequest.newBuilder(loginUri)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""{"email":"admin@admin.com","password":"admin"}"""))
                .build()

            val response = HttpClient.newHttpClient().send(httpRequest, HttpResponse.BodyHandlers.discarding())
            check(response.statusCode() == 200) {
                "Admin login failed with status ${response.statusCode()} while authenticating the shared test RestTestClient"
            }

            return response.headers().firstValue("Set-Cookie")
                .orElseThrow { IllegalStateException("Admin login did not set a session cookie") }
                .substringBefore(";")
        }

        companion object {
            private const val LOGIN_PATH = "/private/v1/auth/login"
        }
    }

    companion object {
        const val POSTGRES_IMAGE = "postgres:18-alpine"
    }
}
