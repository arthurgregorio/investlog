package br.com.investlog.server.auth.security

import br.com.investlog.server.auth.domain.services.AuthService
import br.com.investlog.server.shared.exceptions.GoogleAccountEmailInUseException
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.Authentication
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
            response.sendRedirect("/login?error=email_in_use")
        }
    }
}
