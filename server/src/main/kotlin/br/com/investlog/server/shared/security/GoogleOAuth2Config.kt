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

    @Bean
    @ConditionalOnProperty(prefix = "investlog.google-auth", name = ["enabled"], havingValue = "true")
    fun clientRegistrationRepository(
        @Value($$"${investlog.google-auth.client-id}") googleClientId: String,
        @Value($$"${investlog.google-auth.client-secret}") googleClientSecret: String,
    ): ClientRegistrationRepository {
        val googleRegistration = ClientRegistrations.fromIssuerLocation("https://accounts.google.com")
            .registrationId("google")
            .clientId(googleClientId)
            .clientSecret(googleClientSecret)
            .scope("openid", "profile", "email")
            .redirectUri("{baseUrl}/private/login/oauth2/code/{registrationId}")
            .build()

        return InMemoryClientRegistrationRepository(googleRegistration)
    }
}
