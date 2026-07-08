package br.com.investlog.server.usersadmin.rest.controllers

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.usersadmin.rest.payloads.UserAdminResponse
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

    companion object {
        private lateinit var approveTargetId: String
        private lateinit var rejectTargetId: String
    }
}
