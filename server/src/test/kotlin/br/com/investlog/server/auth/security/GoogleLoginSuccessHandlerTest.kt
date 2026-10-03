package br.com.investlog.server.auth.security

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.auth.rest.payloads.RegisterRequest
import br.com.investlog.server.auth.services.AuthService
import br.com.investlog.server.shared.security.CurrentUser
import br.com.investlog.server.shared.security.UserRepository
import org.junit.jupiter.api.AfterEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.oauth2.core.user.DefaultOAuth2User
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.test.web.servlet.client.RestTestClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GoogleLoginSuccessHandlerTest : BaseIntegrationTest() {

    @Autowired
    lateinit var authService: AuthService

    @Autowired
    lateinit var googleLinkTokenStore: GoogleLinkTokenStore

    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var restTestClient: RestTestClient

    private val handler by lazy { GoogleLoginSuccessHandler(authService, googleLinkTokenStore, CLIENT_BASE_URL) }

    @AfterEach
    fun clearSecurityContext() {
        SecurityContextHolder.clearContext()
    }

    private fun googleToken(googleSub: String, email: String, name: String, avatarUrl: String? = null): OAuth2AuthenticationToken {
        val attributes = buildMap<String, Any> {
            put("sub", googleSub)
            put("email", email)
            put("name", name)
            avatarUrl?.let { put("picture", it) }
        }
        val authorities = listOf(SimpleGrantedAuthority("ROLE_USER"))
        return OAuth2AuthenticationToken(DefaultOAuth2User(authorities, attributes, "sub"), authorities, "google")
    }

    @Test
    fun `a first Google login establishes the session and redirects to the client root`() {
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()

        handler.onAuthenticationSuccess(
            request,
            response,
            googleToken("handler-sub-success", "handler-success@example.com", "Sucesso Google", "https://example.com/avatar.png"),
        )

        assertEquals("$CLIENT_BASE_URL/", response.redirectedUrl)

        val securityContext = HttpSessionSecurityContextRepository().loadDeferredContext(request).get()
        val principal = securityContext.authentication?.principal
        assertIs<CurrentUser>(principal)
        assertEquals("handler-success@example.com", principal.email)
        assertEquals("https://example.com/avatar.png", userRepository.findByGoogleSub("handler-sub-success")?.avatarUrl)
    }

    @Test
    fun `a Google email owned by a local account redirects to the link step with an encoded email and a usable token`() {
        authService.register(RegisterRequest(name = "Conta Local", email = "local+tag@example.com", password = "Senha123"))
        val request = MockHttpServletRequest()
        val strayAuthentication = SecurityContextHolder.createEmptyContext()
        strayAuthentication.authentication = googleToken("handler-sub-collision", "local+tag@example.com", "Conta Google")
        SecurityContextHolder.setContext(strayAuthentication)
        val session = request.getSession(true) as MockHttpSession
        val response = MockHttpServletResponse()

        handler.onAuthenticationSuccess(
            request,
            response,
            googleToken("handler-sub-collision", "local+tag@example.com", "Conta Google", "https://example.com/collision.png"),
        )

        val redirectedUrl = assertNotNull(response.redirectedUrl)
        val queryPattern = Regex(
            "^${Regex.escape("$CLIENT_BASE_URL/login")}\\?error=email_in_use&linkToken=([0-9a-f-]{36})&linkEmail=local%2Btag%40example\\.com$"
        )
        val linkToken = assertNotNull(queryPattern.matchEntire(redirectedUrl)).groupValues[1]

        val pendingLink = assertNotNull(googleLinkTokenStore.consume(linkToken))
        assertEquals("handler-sub-collision", pendingLink.googleSub)
        assertEquals("local+tag@example.com", pendingLink.email)
        assertEquals("Conta Google", pendingLink.name)
        assertEquals("https://example.com/collision.png", pendingLink.avatarUrl)

        assertTrue(session.isInvalid)
        assertNull(SecurityContextHolder.getContext().authentication)
        assertNull(userRepository.findByGoogleSub("handler-sub-collision"))
    }

    @Test
    fun `any other failure clears the session state and redirects with oauth_failed`() {
        val firstRequest = MockHttpServletRequest()
        authService.handleGoogleLogin(
            googleSub = "handler-sub-blocked",
            email = "handler-blocked@example.com",
            name = "Bloqueada Google",
            avatarUrl = null,
            servletRequest = firstRequest,
            servletResponse = MockHttpServletResponse(),
        )
        val user = userRepository.findByEmail("handler-blocked@example.com") ?: error("User was not created by the first Google login")

        restTestClient.patch()
            .uri("/private/v1/users/${user.externalId}/approve")
            .exchange()
            .expectStatus().isOk()
        restTestClient.patch()
            .uri("/private/v1/users/${user.externalId}/block")
            .contentType(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()

        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()

        handler.onAuthenticationSuccess(
            request,
            response,
            googleToken("handler-sub-blocked", "handler-blocked@example.com", "Bloqueada Google"),
        )

        assertEquals("$CLIENT_BASE_URL/login?error=oauth_failed", response.redirectedUrl)
        assertNull(request.getSession(false))
        assertNull(SecurityContextHolder.getContext().authentication)
    }

    companion object {
        private const val CLIENT_BASE_URL = "https://client.test"
    }
}
