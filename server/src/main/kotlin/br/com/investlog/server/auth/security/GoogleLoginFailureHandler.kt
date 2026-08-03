package br.com.investlog.server.auth.security

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.authentication.AuthenticationFailureHandler
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
class GoogleLoginFailureHandler(
    @Value($$"${investlog.google-auth.client-base-url}")
    private val clientBaseUrl: String
) : AuthenticationFailureHandler {

    override fun onAuthenticationFailure(
        request: HttpServletRequest,
        response: HttpServletResponse,
        exception: AuthenticationException,
    ) {
        logger.warn(exception) { "Google login failed" }
        response.sendRedirect("$clientBaseUrl/login?error=oauth_failed")
    }
}
