package br.com.investlog.server.profile

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.auth.services.AuthService
import br.com.investlog.server.auth.rest.payloads.SessionResponse
import br.com.investlog.server.auth.rest.payloads.TotpEnrollResponse
import br.com.investlog.server.profile.services.ProfileService
import br.com.investlog.server.profile.rest.payloads.PasswordChangeRequest
import br.com.investlog.server.profile.rest.payloads.ProfileResponse
import br.com.investlog.server.shared.exceptions.AccountNotLocalException
import br.com.investlog.server.shared.security.UserRepository
import br.com.investlog.server.usersadmin.services.UsersAdminService
import dev.samstevens.totp.code.DefaultCodeGenerator
import org.junit.jupiter.api.Order
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.returnResult
import kotlin.collections.get
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ProfileControllerTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTestClient: RestTestClient

    @Autowired
    lateinit var authService: AuthService

    @Autowired
    lateinit var profileService: ProfileService

    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var usersAdminService: UsersAdminService

    @Test
    @Order(1)
    fun `returns the current user's profile`() {

        val response = restTestClient.get()
            .uri("/private/v1/profile")
            .exchange()
            .expectStatus().isOk()
            .returnResult<ProfileResponse>()
            .responseBody

        assertEquals("Administrador", response?.name)
        assertEquals("admin@admin.com", response?.email)
        assertEquals("teal", response?.accentColor?.text)
        assertEquals("BRL", response?.preferredCurrency)
    }

    @Test
    @Order(2)
    fun `updates accent color and preserves preferred currency`() {

        val response = restTestClient.patch()
            .uri("/private/v1/profile")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"accentColor":"INDIGO"}""")
            .exchange()
            .expectStatus().isOk()
            .returnResult<ProfileResponse>()
            .responseBody

        assertEquals("indigo", response?.accentColor?.text)
        assertEquals("BRL", response?.preferredCurrency)
    }

    @Test
    @Order(3)
    fun `updates preferred currency and preserves the previously set accent color`() {

        val response = restTestClient.patch()
            .uri("/private/v1/profile")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"preferredCurrency":"USD"}""")
            .exchange()
            .expectStatus().isOk()
            .returnResult<ProfileResponse>()
            .responseBody

        assertEquals("indigo", response?.accentColor?.text)
        assertEquals("USD", response?.preferredCurrency)
    }

    @Test
    @Order(4)
    fun `rejects an invalid accent color`() {
        restTestClient.patch()
            .uri("/private/v1/profile")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"accentColor":"purple"}""")
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.title").isEqualTo("Bad Request")
    }

    @Test
    @Order(5)
    fun `self-service password change rejects the wrong current password`() {
        val cookie = registerApproveAndLogin("senha-errada@example.com", "senha123")

        restTestClient.patch()
            .uri("/private/v1/profile/password")
            .header("Cookie", cookie)
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"currentPassword":"wrong","newPassword":"novaSenha456"}""")
            .exchange()
            .expectStatus().isUnauthorized()
    }

    @Test
    @Order(6)
    fun `self-service password change updates the password, allowing login with the new one`() {
        val email = "trocar-senha@example.com"
        val cookie = registerApproveAndLogin(email, "senha123")

        restTestClient.patch()
            .uri("/private/v1/profile/password")
            .header("Cookie", cookie)
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"currentPassword":"senha123","newPassword":"novaSenha456"}""")
            .exchange()
            .expectStatus().isNoContent()

        val response = restTestClient.post()
            .uri("/private/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"email":"$email","password":"novaSenha456"}""")
            .exchange()
            .expectStatus().isUnauthorized()
            .returnResult<Map<String, Any?>>()
            .responseBody

        // Reaching the totp_required branch (rather than invalid_credentials) proves the new
        // password was accepted — this account already has totp enabled from the login helper.
        assertEquals("totp_required", response?.get("error"))
    }

    @Test
    @Order(7)
    fun `self-service password change is rejected for a Google-linked account`() {
        val servletRequest = MockHttpServletRequest()
        val servletResponse = MockHttpServletResponse()

        authService.handleGoogleLogin(
            googleSub = "google-sub-profile-password-test",
            email = "googleuser-password@example.com",
            name = "Google User",
            avatarUrl = null,
            servletRequest = servletRequest,
            servletResponse = servletResponse,
        )

        val user = userRepository.findByEmail("googleuser-password@example.com")!!
        usersAdminService.approve(user.externalId)

        assertFailsWith<AccountNotLocalException> {
            profileService.changePassword(
                PasswordChangeRequest(currentPassword = "whatever", newPassword = "novaSenha456"),
            )
        }
    }

    @Test
    @Order(8)
    fun `self-service password change rejects a new password shorter than 8 characters`() {
        val email = "senha-curta@example.com"
        val cookie = registerApproveAndLogin(email, "senha123")

        restTestClient.patch()
            .uri("/private/v1/profile/password")
            .header("Cookie", cookie)
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"currentPassword":"senha123","newPassword":"1234567"}""")
            .exchange()
            .expectStatus().isBadRequest()
    }

    private fun registerApproveAndLogin(email: String, password: String): String {
        restTestClient.post()
            .uri("/private/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Teste","email":"$email","password":"$password"}""")
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
            .single { it["email"] == email }["id"] as String

        restTestClient.patch()
            .uri("/private/v1/users/$targetId/approve")
            .exchange()
            .expectStatus().isOk()

        val secret = restTestClient.post()
            .uri("/private/v1/auth/totp/enroll")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"email":"$email","password":"$password"}""")
            .exchange()
            .expectStatus().isOk()
            .returnResult<TotpEnrollResponse>()
            .responseBody
            ?.secretKey
            ?: error("Enroll did not return a secret")

        val code = DefaultCodeGenerator().generate(secret, System.currentTimeMillis() / 1000L / 30L)

        return restTestClient.post()
            .uri("/private/v1/auth/totp/verify")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"email":"$email","password":"$password","code":"$code"}""")
            .exchange()
            .expectStatus().isOk()
            .returnResult<SessionResponse>()
            .responseHeaders
            .getFirst("Set-Cookie")
            ?.substringBefore(";")
            ?: error("Verify did not set a session cookie")
    }
}
