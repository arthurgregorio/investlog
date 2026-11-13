package br.com.investlog.server.shared.security

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.auth.rest.payloads.SessionResponse
import br.com.investlog.server.auth.rest.payloads.TotpEnrollResponse
import dev.samstevens.totp.code.DefaultCodeGenerator
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.returnResult
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class ActiveAdministratorGuardTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTestClient: RestTestClient

    @Autowired
    lateinit var userRepository: UserRepository

    @Test
    fun `an admin whose session is still valid keeps reaching every admin route`() {
        val session = openAdministratorSession("admin-ativo@example.com")

        assertEquals(200, call(session.cookie, HttpMethod.GET, "/private/v1/users"))
        assertEquals(200, call(session.cookie, HttpMethod.GET, "/private/v1/mail-settings"))
        assertEquals(
            200,
            call(session.cookie, HttpMethod.PATCH, "/private/v1/configurations/stock_price_sync_enabled", """{"value":"false"}"""),
        )
        assertEquals(201, call(session.cookie, HttpMethod.POST, "/private/v1/segments", """{"name":"Segmento Ativo"}"""))
    }

    @Test
    fun `a blocked admin loses every admin route while its session is still open`() {
        val session = openAdministratorSession("admin-bloqueado-guard@example.com")

        restTestClient.patch()
            .uri("/private/v1/users/${session.externalId}/block")
            .exchange()
            .expectStatus().isOk()

        adminRoutes().forEach { route ->
            assertEquals(403, call(session.cookie, route.method, route.uri, route.body), "${route.method} ${route.uri}")
        }
    }

    @Test
    fun `a demoted admin loses every admin route while its session is still open`() {
        val session = openAdministratorSession("admin-rebaixado@example.com")

        restTestClient.patch()
            .uri("/private/v1/users/${session.externalId}/role")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"role":"USER"}""")
            .exchange()
            .expectStatus().isOk()

        adminRoutes().forEach { route ->
            assertEquals(403, call(session.cookie, route.method, route.uri, route.body), "${route.method} ${route.uri}")
        }
    }

    @Test
    fun `a deleted admin loses every admin route while its session is still open`() {
        val session = openAdministratorSession("admin-excluido@example.com")

        restTestClient.delete()
            .uri("/private/v1/users/${session.externalId}")
            .exchange()
            .expectStatus().isNoContent()

        adminRoutes().forEach { route ->
            assertEquals(401, call(session.cookie, route.method, route.uri, route.body), "${route.method} ${route.uri}")
        }
    }

    @Test
    fun `a blocked admin that is unblocked reaches the admin routes again`() {
        val session = openAdministratorSession("admin-desbloqueado@example.com")

        restTestClient.patch().uri("/private/v1/users/${session.externalId}/block").exchange().expectStatus().isOk()
        assertEquals(403, call(session.cookie, HttpMethod.GET, "/private/v1/users"))

        restTestClient.patch().uri("/private/v1/users/${session.externalId}/unblock").exchange().expectStatus().isOk()
        assertEquals(200, call(session.cookie, HttpMethod.GET, "/private/v1/users"))
    }

    private fun adminRoutes(): List<AdminRoute> = listOf(
        AdminRoute(HttpMethod.GET, "/private/v1/users"),
        AdminRoute(HttpMethod.DELETE, "/private/v1/users/${UUID.randomUUID()}"),
        AdminRoute(HttpMethod.GET, "/private/v1/mail-settings"),
        AdminRoute(HttpMethod.PATCH, "/private/v1/mail-settings/enabled", """{"enabled":true}"""),
        AdminRoute(HttpMethod.PATCH, "/private/v1/configurations/stock_price_sync_enabled", """{"value":"true"}"""),
        AdminRoute(HttpMethod.POST, "/private/v1/stock-price-sync"),
        AdminRoute(HttpMethod.POST, "/private/v1/crypto-price-sync"),
        AdminRoute(HttpMethod.POST, "/private/v1/segments", """{"name":"Segmento Proibido"}"""),
        AdminRoute(HttpMethod.POST, "/private/v1/holding-types", """{"name":"Tipo Proibido","kinds":["STOCKS"]}"""),
        AdminRoute(HttpMethod.PUT, "/private/v1/currency-rates/USD", """{"rate":5.60,"isBase":false}"""),
    )

    private fun call(cookie: String, method: HttpMethod, uri: String, body: String? = null): Int {
        val request = restTestClient.method(method).uri(uri).header("Cookie", cookie)
        val exchange = if (body == null) {
            request.exchange()
        } else {
            request.contentType(MediaType.APPLICATION_JSON).body(body).exchange()
        }
        return exchange.returnResult<String>().status.value()
    }

    private fun openAdministratorSession(email: String): AdministratorSession {
        restTestClient.post()
            .uri("/private/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Administrador Secundário","email":"$email","password":"Senha123"}""")
            .exchange()
            .expectStatus().isCreated()

        val externalId = userRepository.findByEmail(email)?.externalId ?: error("User was not created by register")

        restTestClient.patch().uri("/private/v1/users/$externalId/approve").exchange().expectStatus().isOk()
        restTestClient.patch()
            .uri("/private/v1/users/$externalId/role")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"role":"ADMIN"}""")
            .exchange()
            .expectStatus().isOk()

        val secret = restTestClient.post()
            .uri("/private/v1/auth/totp/enroll")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"email":"$email","password":"Senha123"}""")
            .exchange()
            .expectStatus().isOk()
            .returnResult<TotpEnrollResponse>()
            .responseBody
            ?.secretKey
            ?: error("Enroll did not return a secret")

        val code = DefaultCodeGenerator().generate(secret, System.currentTimeMillis() / 1000L / 30L)

        val cookie = restTestClient.post()
            .uri("/private/v1/auth/totp/verify")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"email":"$email","password":"Senha123","code":"$code"}""")
            .exchange()
            .expectStatus().isOk()
            .returnResult<SessionResponse>()
            .responseHeaders
            .getFirst("Set-Cookie")
            ?.substringBefore(";")
            ?: error("Verify did not set a session cookie")

        return AdministratorSession(externalId, cookie)
    }

    private data class AdministratorSession(val externalId: UUID, val cookie: String)

    private data class AdminRoute(val method: HttpMethod, val uri: String, val body: String? = null)
}
