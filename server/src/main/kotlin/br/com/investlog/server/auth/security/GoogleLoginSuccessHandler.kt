package br.com.investlog.server.auth.security

import br.com.investlog.server.auth.domain.services.AuthService
import br.com.investlog.server.shared.exceptions.GoogleAccountEmailInUseException
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.stereotype.Component
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private val log = KotlinLogging.logger {}

@Component
class GoogleLoginSuccessHandler(
    private val authService: AuthService,
    private val googleLinkTokenStore: GoogleLinkTokenStore,
    @Value("\${investlog.client-base-url}") private val clientBaseUrl: String,
) : AuthenticationSuccessHandler {

    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication,
    ) {
        val oauth2Token = authentication as OAuth2AuthenticationToken
        val attributes = oauth2Token.principal.attributes

        val googleSub = attributes["sub"] as String
        val email = attributes["email"] as String
        val name = attributes["name"] as String
        val avatarUrl = attributes["picture"] as String?

        try {
            authService.handleGoogleLogin(
                googleSub = googleSub,
                email = email,
                name = name,
                avatarUrl = avatarUrl,
                servletRequest = request,
                servletResponse = response,
            )
            response.sendRedirect("$clientBaseUrl/")
        } catch (exception: GoogleAccountEmailInUseException) {
            log.warn(exception) { "Google login collided with an existing local account; offering to link" }
            clearStraySessionState(request)
            val linkToken = googleLinkTokenStore.issue(googleSub, email, name, avatarUrl)
            val encodedEmail = URLEncoder.encode(email, StandardCharsets.UTF_8)
            response.sendRedirect("$clientBaseUrl/login?error=email_in_use&linkToken=$linkToken&linkEmail=$encodedEmail")
        } catch (exception: Exception) {
            log.warn(exception) { "Google login failed while processing the OAuth2 callback" }
            clearStraySessionState(request)
            response.sendRedirect("$clientBaseUrl/login?error=oauth_failed")
        }
    }

    /**
     * Spring Security's OAuth2 login filter already persists a generic [OAuth2AuthenticationToken]
     * into the session before this handler runs. On any failure here, [AuthService.handleGoogleLogin]
     * never gets the chance to overwrite it with a proper `CurrentUser`-principal session via
     * `establishSession`, so that stray token would otherwise be left behind. Clear it so the
     * session ends up "ours or nobody's" rather than holding Spring's default OAuth2 principal.
     */
    private fun clearStraySessionState(request: HttpServletRequest) {
        SecurityContextHolder.clearContext()
        request.getSession(false)?.invalidate()
    }
}
