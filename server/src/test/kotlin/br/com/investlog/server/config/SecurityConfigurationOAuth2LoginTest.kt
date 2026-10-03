package br.com.investlog.server.config

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.RestClientTestConfiguration
import br.com.investlog.server.TestcontainersConfiguration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.web.util.UriComponentsBuilder
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Import(
    value = [
        TestcontainersConfiguration::class,
        RestClientTestConfiguration::class,
        SecurityConfigurationOAuth2LoginTest.GoogleRegistrationConfiguration::class,
    ]
)
class SecurityConfigurationOAuth2LoginTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTestClient: RestTestClient

    @Value($$"${investlog.google-auth.client-base-url}")
    lateinit var clientBaseUrl: String

    @TestConfiguration(proxyBeanMethods = false)
    class GoogleRegistrationConfiguration {

        @Bean
        fun clientRegistrationRepository(): ClientRegistrationRepository = InMemoryClientRegistrationRepository(
            ClientRegistration.withRegistrationId("google")
                .clientId("test-client-id")
                .clientSecret("test-client-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/private/login/oauth2/code/{registrationId}")
                .scope("openid", "profile", "email")
                .authorizationUri(AUTHORIZATION_URI)
                .tokenUri("https://accounts.example.test/token")
                .userInfoUri("https://accounts.example.test/userinfo")
                .userNameAttributeName("sub")
                .build()
        )
    }

    @Test
    fun `the authorization endpoint under private redirects the browser to the identity provider`() {
        val location = restTestClient.get()
            .uri("/private/oauth2/authorization/google")
            .exchange()
            .expectStatus().isFound()
            .returnResult(String::class.java)
            .responseHeaders
            .location
            .toString()

        assertTrue(location.startsWith("$AUTHORIZATION_URI?"))
        assertTrue(location.contains("response_type=code"))
        assertTrue(location.contains("client_id=test-client-id"))
        val redirectUri = UriComponentsBuilder.fromUriString(location).build().queryParams.getFirst("redirect_uri")
        assertTrue(URLDecoder.decode(redirectUri, StandardCharsets.UTF_8).endsWith("/private/login/oauth2/code/google"))
    }

    @Test
    fun `a callback without an authorization state is sent to the client with oauth_failed`() {
        val location = restTestClient.get()
            .uri("/private/login/oauth2/code/google")
            .exchange()
            .expectStatus().isFound()
            .returnResult(String::class.java)
            .responseHeaders
            .location
            .toString()

        assertEquals("$clientBaseUrl/login?error=oauth_failed", location)
    }

    companion object {
        private const val AUTHORIZATION_URI = "https://accounts.example.test/o/oauth2/v2/auth"
    }
}
