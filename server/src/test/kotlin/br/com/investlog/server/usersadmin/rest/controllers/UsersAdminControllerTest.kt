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
    fun `registers two users to approve and block`() {
        restTestClient.post()
            .uri("/private/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Para Aprovar","email":"aprovar@example.com","password":"senha123"}""")
            .exchange()
            .expectStatus().isCreated()

        restTestClient.post()
            .uri("/private/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Para Bloquear","email":"bloquear@example.com","password":"senha123"}""")
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
        blockTargetId = content.single { it["email"] == "bloquear@example.com" }["id"] as String
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
    fun `block is rejected when the target isn't currently APPROVED`() {
        restTestClient.patch()
            .uri("/private/v1/users/$blockTargetId/block")
            .exchange()
            .expectStatus().isEqualTo(409)
    }

    @Test
    @Order(5)
    fun `block sets the user's status to BLOCKED once approved`() {
        restTestClient.patch()
            .uri("/private/v1/users/$blockTargetId/approve")
            .exchange()
            .expectStatus().isOk()

        val response = restTestClient.patch()
            .uri("/private/v1/users/$blockTargetId/block")
            .exchange()
            .expectStatus().isOk()
            .returnResult<UserAdminResponse>()
            .responseBody

        assertEquals("BLOCKED", response?.status?.name)
    }

    @Test
    @Order(6)
    fun `unblock is rejected when the target isn't currently BLOCKED`() {
        restTestClient.patch()
            .uri("/private/v1/users/$approveTargetId/unblock")
            .exchange()
            .expectStatus().isEqualTo(409)
    }

    @Test
    @Order(7)
    fun `unblock sets the user's status back to APPROVED`() {
        val response = restTestClient.patch()
            .uri("/private/v1/users/$blockTargetId/unblock")
            .exchange()
            .expectStatus().isOk()
            .returnResult<UserAdminResponse>()
            .responseBody

        assertEquals("APPROVED", response?.status?.name)
    }

    @Test
    @Order(8)
    fun `a blocked user's login fails with a generic error and no session cookie`() {
        restTestClient.post()
            .uri("/private/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Bloqueada","email":"bloqueada@example.com","password":"senha123"}""")
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
            .single { it["email"] == "bloqueada@example.com" }["id"] as String

        restTestClient.patch()
            .uri("/private/v1/users/$targetId/approve")
            .exchange()
            .expectStatus().isOk()

        restTestClient.patch()
            .uri("/private/v1/users/$targetId/block")
            .exchange()
            .expectStatus().isOk()

        val responseHeaders = restTestClient.post()
            .uri("/private/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"email":"bloqueada@example.com","password":"senha123"}""")
            .exchange()
            .expectStatus().isUnauthorized()
            .returnResult<Unit>()
            .responseHeaders

        assertTrue(responseHeaders.getFirst("Set-Cookie").isNullOrEmpty())
    }

    @Test
    @Order(9)
    fun `approve on an unknown id returns 404`() {
        restTestClient.patch()
            .uri("/private/v1/users/00000000-0000-0000-0000-000000000000/approve")
            .exchange()
            .expectStatus().isNotFound()
    }

    @Test
    @Order(10)
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
    @Order(11)
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
    @Order(12)
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
    @Order(13)
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
    @Order(14)
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
    @Order(15)
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
    @Order(16)
    fun `blocking a user revokes their already-open session on the next request, without a new login`() {
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

        // The session's cached authorities now say STATUS_APPROVED. Blocking the user changes
        // the database but cannot reach into that already-issued session.
        restTestClient.patch()
            .uri("/private/v1/users/$targetId/block")
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
    @Order(17)
    fun `block rejects targeting your own account`() {
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
            .uri("/private/v1/users/$adminId/block")
            .exchange()
            .expectStatus().isBadRequest()
    }

    @Test
    @Order(18)
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
        private lateinit var blockTargetId: String
        private lateinit var manageTargetId: String
    }
}
