package br.com.investlog.server.auth.services

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.auth.rest.payloads.GoogleAccountLinkRequest
import br.com.investlog.server.auth.rest.payloads.RegisterRequest
import br.com.investlog.server.auth.security.GoogleLinkTokenStore
import br.com.investlog.server.auth.services.AuthService
import br.com.investlog.server.shared.exceptions.GoogleAccountEmailInUseException
import br.com.investlog.server.shared.exceptions.InvalidCredentialsException
import br.com.investlog.server.shared.security.UserRepository
import br.com.investlog.server.usersadmin.services.UsersAdminService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.test.web.servlet.client.RestTestClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthServiceGoogleLoginTest : BaseIntegrationTest() {

    @Autowired
    lateinit var authService: AuthService

    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var usersAdminService: UsersAdminService

    @Autowired
    lateinit var googleLinkTokenStore: GoogleLinkTokenStore

    @Autowired
    lateinit var restTestClient: RestTestClient

    @Test
    fun `first Google login creates a pending user and swaps in a CurrentUser-authorized session`() {
        val servletRequest = MockHttpServletRequest()
        val servletResponse = MockHttpServletResponse()

        val session = authService.handleGoogleLogin(
            googleSub = "google-sub-first-login",
            email = "primeira@example.com",
            name = "Primeira Vez",
            avatarUrl = "https://example.com/avatar.png",
            servletRequest = servletRequest,
            servletResponse = servletResponse,
        )

        assertEquals("PENDING", session.status.name)
        assertEquals("USER", session.role.name)

        val securityContext = HttpSessionSecurityContextRepository()
            .loadDeferredContext(servletRequest)
            .get()
        val authorities = securityContext.authentication!!.authorities.map { it.authority }

        assertTrue(authorities.contains("ROLE_USER"))
        assertTrue(authorities.contains("STATUS_PENDING"))
    }

    @Test
    fun `logging in again with the same google subject reuses the same user`() {
        val firstRequest = MockHttpServletRequest()
        val firstResponse = MockHttpServletResponse()
        authService.handleGoogleLogin(
            googleSub = "google-sub-repeat-login",
            email = "repetida@example.com",
            name = "Usuária Repetida",
            avatarUrl = null,
            servletRequest = firstRequest,
            servletResponse = firstResponse,
        )

        val secondRequest = MockHttpServletRequest()
        val secondResponse = MockHttpServletResponse()
        val secondSession = authService.handleGoogleLogin(
            googleSub = "google-sub-repeat-login",
            email = "repetida@example.com",
            name = "Usuária Repetida",
            avatarUrl = null,
            servletRequest = secondRequest,
            servletResponse = secondResponse,
        )

        assertEquals("repetida@example.com", secondSession.email)
    }

    @Test
    fun `a google login with an email already used by another account is rejected`() {
        val servletRequest = MockHttpServletRequest()
        val servletResponse = MockHttpServletResponse()

        assertFailsWith<GoogleAccountEmailInUseException> {
            authService.handleGoogleLogin(
                googleSub = "google-sub-duplicate-email",
                email = "admin@admin.com",
                name = "Impostor",
                avatarUrl = null,
                servletRequest = servletRequest,
                servletResponse = servletResponse,
            )
        }
    }

    @Test
    fun `an already-approved Google user's next login carries STATUS_APPROVED`() {
        val firstRequest = MockHttpServletRequest()
        val firstResponse = MockHttpServletResponse()
        authService.handleGoogleLogin(
            googleSub = "google-sub-approved-login",
            email = "aprovada@example.com",
            name = "Usuária Aprovada",
            avatarUrl = null,
            servletRequest = firstRequest,
            servletResponse = firstResponse,
        )

        val user = userRepository.findByEmail("aprovada@example.com")
            ?: error("User was not created by the first Google login")
        usersAdminService.approve(user.externalId)

        val secondRequest = MockHttpServletRequest()
        val secondResponse = MockHttpServletResponse()
        val secondSession = authService.handleGoogleLogin(
            googleSub = "google-sub-approved-login",
            email = "aprovada@example.com",
            name = "Usuária Aprovada",
            avatarUrl = null,
            servletRequest = secondRequest,
            servletResponse = secondResponse,
        )

        assertEquals("APPROVED", secondSession.status.name)

        val securityContext = HttpSessionSecurityContextRepository()
            .loadDeferredContext(secondRequest)
            .get()
        val authorities = securityContext.authentication!!.authorities.map { it.authority }

        assertTrue(authorities.contains("STATUS_APPROVED"))
    }

    @Test
    fun `linking a Google account to an existing local account succeeds with the correct password`() {
        authService.register(RegisterRequest(name = "Para Vincular", email = "vincular@example.com", password = "senha123"))

        val token = googleLinkTokenStore.issue(
            googleSub = "google-sub-link-success",
            email = "vincular@example.com",
            name = "Para Vincular",
            avatarUrl = null,
        )

        val servletRequest = MockHttpServletRequest()
        val servletResponse = MockHttpServletResponse()

        val session = authService.linkGoogleAccount(
            request = GoogleAccountLinkRequest(linkToken = token, password = "senha123"),
            servletRequest = servletRequest,
            servletResponse = servletResponse,
        )

        assertEquals("GOOGLE", session.authProvider.name)

        val updatedUser = userRepository.findByEmail("vincular@example.com")
        assertEquals("GOOGLE", updatedUser?.authProvider?.name)
        assertEquals(updatedUser?.id, userRepository.findByGoogleSub("google-sub-link-success")?.id)
        assertNull(userRepository.findPasswordHashByEmail("vincular@example.com"))
    }

    @Test
    fun `linking rejects the wrong password`() {
        authService.register(RegisterRequest(name = "Senha Errada", email = "senha-errada-link@example.com", password = "senha123"))

        val token = googleLinkTokenStore.issue(
            googleSub = "google-sub-link-wrong-password",
            email = "senha-errada-link@example.com",
            name = "Senha Errada",
            avatarUrl = null,
        )

        assertFailsWith<InvalidCredentialsException> {
            authService.linkGoogleAccount(
                request = GoogleAccountLinkRequest(linkToken = token, password = "wrong"),
                servletRequest = MockHttpServletRequest(),
                servletResponse = MockHttpServletResponse(),
            )
        }
    }

    @Test
    fun `linking rejects an unknown or expired token`() {
        assertFailsWith<InvalidCredentialsException> {
            authService.linkGoogleAccount(
                request = GoogleAccountLinkRequest(linkToken = "not-a-real-token", password = "whatever"),
                servletRequest = MockHttpServletRequest(),
                servletResponse = MockHttpServletResponse(),
            )
        }
    }

    @Test
    fun `linking rejects a blocked account`() {
        authService.register(RegisterRequest(name = "Bloqueada Link", email = "bloqueada-link@example.com", password = "senha123"))
        val user = userRepository.findByEmail("bloqueada-link@example.com")
            ?: error("User was not created by register")

        // requireNotSelf (called by block()) resolves the acting admin from the security context,
        // which only exists on a real HTTP request — go through restTestClient (auto-authenticated
        // as admin) rather than calling usersAdminService directly, as the other tests here do.
        restTestClient.patch()
            .uri("/private/v1/users/${user.externalId}/approve")
            .exchange()
            .expectStatus().isOk()
        restTestClient.patch()
            .uri("/private/v1/users/${user.externalId}/block")
            .contentType(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()

        val token = googleLinkTokenStore.issue(
            googleSub = "google-sub-link-blocked",
            email = "bloqueada-link@example.com",
            name = "Bloqueada Link",
            avatarUrl = null,
        )

        assertFailsWith<InvalidCredentialsException> {
            authService.linkGoogleAccount(
                request = GoogleAccountLinkRequest(linkToken = token, password = "senha123"),
                servletRequest = MockHttpServletRequest(),
                servletResponse = MockHttpServletResponse(),
            )
        }
    }
}
