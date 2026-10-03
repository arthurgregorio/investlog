package br.com.investlog.server.auth.security

import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.BadCredentialsException
import kotlin.test.Test
import kotlin.test.assertEquals

class GoogleLoginFailureHandlerTest {

    @Test
    fun `a failed Google callback redirects to the client login with oauth_failed`() {
        val handler = GoogleLoginFailureHandler("https://client.test")
        val response = MockHttpServletResponse()

        handler.onAuthenticationFailure(MockHttpServletRequest(), response, BadCredentialsException("the code was rejected"))

        assertEquals("https://client.test/login?error=oauth_failed", response.redirectedUrl)
    }
}
