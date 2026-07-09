package br.com.investlog.server.usersadmin.rest.controllers

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.auth.rest.payloads.SessionResponse
import br.com.investlog.server.auth.rest.payloads.TotpEnrollResponse
import br.com.investlog.server.usersadmin.rest.payloads.UserAdminResponse
import dev.samstevens.totp.code.DefaultCodeGenerator
import org.junit.jupiter.api.Order
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.returnResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UsersAdminControllerTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTestClient: RestTestClient

    @Test
    @Order(1)
    fun `list includes the seeded admin`() {
        val response = restTestClient.get()
            .uri("/private/v1/users?size=200")
            .exchange()
            .expectStatus().isOk()
            .returnResult<Map<String, Any?>>()
            .responseBody

        @Suppress("UNCHECKED_CAST")
        val content = response?.get("content") as List<Map<String, Any?>>
        assertTrue(content.any { it["email"] == "admin@admin.com" })
    }

    @Test
    @Order(2)
    fun `registers two users to approve and reject`() {
        restTestClient.post()
            .uri("/private/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Para Aprovar","email":"aprovar@example.com","password":"senha123"}""")
            .exchange()
            .expectStatus().isCreated()

        restTestClient.post()
            .uri("/private/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Para Rejeitar","email":"rejeitar@example.com","password":"senha123"}""")
            .exchange()
            .expectStatus().isCreated()

        val response = restTestClient.get()
            .uri("/private/v1/users?size=200")
            .exchange()
            .expectStatus().isOk()
            .returnResult<Map<String, Any?>>()
            .responseBody

        @Suppress("UNCHECKED_CAST")
        val content = response?.get("content") as List<Map<String, Any?>>
        approveTargetId = content.single { it["email"] == "aprovar@example.com" }["id"] as String
        rejectTargetId = content.single { it["email"] == "rejeitar@example.com" }["id"] as String
    }

    @Test
    @Order(3)
    fun `approve sets the user's status to APPROVED`() {
        val response = restTestClient.patch()
            .uri("/private/v1/users/$approveTargetId/approve")
            .exchange()
            .expectStatus().isOk()
            .returnResult<UserAdminResponse>()
            .responseBody

        assertEquals("APPROVED", response?.status?.name)
    }

    @Test
    @Order(4)
    fun `reject sets the user's status to REJECTED`() {
        val response = restTestClient.patch()
            .uri("/private/v1/users/$rejectTargetId/reject")
            .exchange()
            .expectStatus().isOk()
            .returnResult<UserAdminResponse>()
            .responseBody

        assertEquals("REJECTED", response?.status?.name)
    }

    @Test
    @Order(5)
    fun `approve on an unknown id returns 404`() {
        restTestClient.patch()
            .uri("/private/v1/users/00000000-0000-0000-0000-000000000000/approve")
            .exchange()
            .expectStatus().isNotFound()
    }

    @Test
    @Order(6)
    fun `registers a third user to manage role, totp, and deletion`() {
        restTestClient.post()
            .uri("/private/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Para Gerenciar","email":"gerenciar@example.com","password":"senha123"}""")
            .exchange()
            .expectStatus().isCreated()

        val response = restTestClient.get()
            .uri("/private/v1/users?size=200")
            .exchange()
            .expectStatus().isOk()
            .returnResult<Map<String, Any?>>()
            .responseBody

        @Suppress("UNCHECKED_CAST")
        val content = response?.get("content") as List<Map<String, Any?>>
        manageTargetId = content.single { it["email"] == "gerenciar@example.com" }["id"] as String
    }

    @Test
    @Order(7)
    fun `role change promotes the target user to admin`() {
        val response = restTestClient.patch()
            .uri("/private/v1/users/$manageTargetId/role")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"role":"ADMIN"}""")
            .exchange()
            .expectStatus().isOk()
            .returnResult<UserAdminResponse>()
            .responseBody

        assertEquals("ADMIN", response?.role?.name)
    }

    @Test
    @Order(8)
    fun `role change rejects targeting your own account`() {
        val adminId = (
            restTestClient.get()
                .uri("/private/v1/users?size=200")
                .exchange()
                .expectStatus().isOk()
                .returnResult<Map<String, Any?>>()
                .responseBody
                ?.get("content") as List<*>
            )
            .map { it as Map<*, *> }
            .single { it["email"] == "admin@admin.com" }["id"] as String

        restTestClient.patch()
            .uri("/private/v1/users/$adminId/role")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"role":"USER"}""")
            .exchange()
            .expectStatus().isBadRequest()
    }

    @Test
    @Order(9)
    fun `totp reset clears the enabled flag`() {
        val response = restTestClient.patch()
            .uri("/private/v1/users/$manageTargetId/totp-reset")
            .exchange()
            .expectStatus().isOk()
            .returnResult<UserAdminResponse>()
            .responseBody

        assertEquals(false, response?.totpEnabled)
    }

    @Test
    @Order(10)
    fun `delete rejects targeting your own account`() {
        val adminId = (
            restTestClient.get()
                .uri("/private/v1/users?size=200")
                .exchange()
                .expectStatus().isOk()
                .returnResult<Map<String, Any?>>()
                .responseBody
                ?.get("content") as List<*>
            )
            .map { it as Map<*, *> }
            .single { it["email"] == "admin@admin.com" }["id"] as String

        restTestClient.delete()
            .uri("/private/v1/users/$adminId")
            .exchange()
            .expectStatus().isBadRequest()
    }

    @Test
    @Order(11)
    fun `delete removes the target user`() {
        restTestClient.delete()
            .uri("/private/v1/users/$manageTargetId")
            .exchange()
            .expectStatus().isNoContent()

        val response = restTestClient.get()
            .uri("/private/v1/users?size=200")
            .exchange()
            .expectStatus().isOk()
            .returnResult<Map<String, Any?>>()
            .responseBody

        @Suppress("UNCHECKED_CAST")
        val content = response?.get("content") as List<Map<String, Any?>>
        assertTrue(content.none { it["email"] == "gerenciar@example.com" })
    }

    @Test
    @Order(12)
    fun `rejecting a user revokes their already-open session on the next request, without a new login`() {
        restTestClient.post()
            .uri("/private/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Sessão Aberta","email":"sessao@example.com","password":"senha123"}""")
            .exchange()
            .expectStatus().isCreated()

        val targetId = (
            restTestClient.get()
                .uri("/private/v1/users?size=200")
                .exchange()
                .expectStatus().isOk()
                .returnResult<Map<String, Any?>>()
                .responseBody
                ?.get("content") as List<*>
            )
            .map { it as Map<*, *> }
            .single { it["email"] == "sessao@example.com" }["id"] as String

        restTestClient.patch()
            .uri("/private/v1/users/$targetId/approve")
            .exchange()
            .expectStatus().isOk()

        val secret = restTestClient.post()
            .uri("/private/v1/auth/totp/enroll")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"email":"sessao@example.com","password":"senha123"}""")
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
            .body("""{"email":"sessao@example.com","password":"senha123","code":"$code"}""")
            .exchange()
            .expectStatus().isOk()
            .returnResult<SessionResponse>()
            .responseHeaders
            .getFirst("Set-Cookie")
            ?.substringBefore(";")
            ?: error("Verify did not set a session cookie")

        // The session's cached authorities now say STATUS_APPROVED. Rejecting the user changes
        // the database but cannot reach into that already-issued session.
        restTestClient.patch()
            .uri("/private/v1/users/$targetId/reject")
            .exchange()
            .expectStatus().isOk()

        // The filter chain alone would still admit this request (its cached authority is stale) —
        // this must be blocked by SecurityContextCurrentUserProvider's fresh status re-check
        // instead, proving the gap from Task 1 Step 11 is actually closed, not just unit-level.
        restTestClient.get()
            .uri("/private/v1/profile")
            .header("Cookie", cookie)
            .exchange()
            .expectStatus().isEqualTo(403)
            .returnResult<Map<String, Any?>>()
            .responseBody
            .let { assertEquals("pending_approval", it?.get("error")) }
    }

    @Test
    @Order(13)
    fun `reject rejects targeting your own account`() {
        val adminId = (
            restTestClient.get()
                .uri("/private/v1/users?size=200")
                .exchange()
                .expectStatus().isOk()
                .returnResult<Map<String, Any?>>()
                .responseBody
                ?.get("content") as List<*>
            )
            .map { it as Map<*, *> }
            .single { it["email"] == "admin@admin.com" }["id"] as String

        restTestClient.patch()
            .uri("/private/v1/users/$adminId/reject")
            .exchange()
            .expectStatus().isBadRequest()
    }

    @Test
    @Order(14)
    fun `a non-admin user is forbidden from the users-admin endpoints`() {
        restTestClient.post()
            .uri("/private/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Não Admin","email":"naoadmin@example.com","password":"senha123"}""")
            .exchange()
            .expectStatus().isCreated()

        val targetId = (
            restTestClient.get()
                .uri("/private/v1/users?size=200")
                .exchange()
                .expectStatus().isOk()
                .returnResult<Map<String, Any?>>()
                .responseBody
                ?.get("content") as List<*>
            )
            .map { it as Map<*, *> }
            .single { it["email"] == "naoadmin@example.com" }["id"] as String

        restTestClient.patch()
            .uri("/private/v1/users/$targetId/approve")
            .exchange()
            .expectStatus().isOk()

        val secret = restTestClient.post()
            .uri("/private/v1/auth/totp/enroll")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"email":"naoadmin@example.com","password":"senha123"}""")
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
            .body("""{"email":"naoadmin@example.com","password":"senha123","code":"$code"}""")
            .exchange()
            .expectStatus().isOk()
            .returnResult<SessionResponse>()
            .responseHeaders
            .getFirst("Set-Cookie")
            ?.substringBefore(";")
            ?: error("Verify did not set a session cookie")

        restTestClient.get()
            .uri("/private/v1/users")
            .header("Cookie", cookie)
            .exchange()
            .expectStatus().isEqualTo(403)
    }

    companion object {
        private lateinit var approveTargetId: String
        private lateinit var rejectTargetId: String
        private lateinit var manageTargetId: String
    }
}
