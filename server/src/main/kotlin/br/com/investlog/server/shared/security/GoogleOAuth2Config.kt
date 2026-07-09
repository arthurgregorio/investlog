package br.com.investlog.server.shared.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.registration.ClientRegistrations
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository

@Configuration
class GoogleOAuth2Config {

    // NOTE: CommonOAuth2Provider (the brief's originally specified API) was removed in Spring
    // Security 7.x (pulled in by Spring Boot 4.1.0 here), so this uses its OIDC-discovery-based
    // replacement instead. ClientRegistrations.fromIssuerLocation performs a blocking HTTP call
    // to Google's issuer metadata endpoint at bean-creation time (i.e. only when
    // google-auth-enabled=true), pre-populating authorizationUri/tokenUri/jwkSetUri/userInfoUri,
    // redirectUri, authorizationGrantType, and clientAuthenticationMethod the same way
    // CommonOAuth2Provider.GOOGLE used to.
    @Bean
    @ConditionalOnProperty(prefix = "investlog", name = ["google-auth-enabled"], havingValue = "true")
    fun clientRegistrationRepository(
        @Value("\${investlog.google-client-id}") googleClientId: String,
        @Value("\${investlog.google-client-secret}") googleClientSecret: String,
    ): ClientRegistrationRepository {
        val googleRegistration = ClientRegistrations.fromIssuerLocation("https://accounts.google.com")
            .registrationId("google")
            .clientId(googleClientId)
            .clientSecret(googleClientSecret)
            .build()

        return InMemoryClientRegistrationRepository(googleRegistration)
    }
}
