package br.com.investlog.server.auth.security

import br.com.investlog.server.auth.domain.services.AuthService
import br.com.investlog.server.shared.exceptions.GoogleAccountEmailInUseException
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

@Component
class GoogleLoginSuccessHandler(private val authService: AuthService) : AuthenticationSuccessHandler {

    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication,
    ) {
        val oauth2Token = authentication as OAuth2AuthenticationToken
        val attributes = oauth2Token.principal.attributes

        try {
            authService.handleGoogleLogin(
                googleSub = attributes["sub"] as String,
                email = attributes["email"] as String,
                name = attributes["name"] as String,
                avatarUrl = attributes["picture"] as String?,
                servletRequest = request,
                servletResponse = response,
            )
            response.sendRedirect("/")
        } catch (exception: GoogleAccountEmailInUseException) {
            log.warn(exception) { "Google login rejected: email already in use by another account" }
            clearStraySessionState(request)
            response.sendRedirect("/login?error=email_in_use")
        } catch (exception: Exception) {
            log.warn(exception) { "Google login failed while processing the OAuth2 callback" }
            clearStraySessionState(request)
            response.sendRedirect("/login?error=oauth_failed")
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
