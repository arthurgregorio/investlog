package br.com.investlog.server.config

import br.com.investlog.server.auth.security.GoogleLoginFailureHandler
import br.com.investlog.server.auth.security.GoogleLoginSuccessHandler
import br.com.investlog.server.shared.rest.payloads.AccessDeniedResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import tools.jackson.databind.json.JsonMapper

@Configuration
class SecurityConfig {

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun accessDeniedHandler(jsonMapper: JsonMapper): AccessDeniedHandler = AccessDeniedHandler { _, response, _ ->
        val authentication = SecurityContextHolder.getContext().authentication
        val isPendingApproval = authentication?.authorities.orEmpty().none { it.authority == "STATUS_APPROVED" }

        val body = if (isPendingApproval) {
            AccessDeniedResponse("pending_approval", "Your account is pending administrator approval")
        } else {
            AccessDeniedResponse("forbidden", "You do not have permission to perform this action")
        }

        response.status = HttpStatus.FORBIDDEN.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.writer.write(jsonMapper.writeValueAsString(body))
    }

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        jsonMapper: JsonMapper,
        clientRegistrationRepository: ClientRegistrationRepository?,
        googleLoginSuccessHandler: GoogleLoginSuccessHandler,
        googleLoginFailureHandler: GoogleLoginFailureHandler,
    ): SecurityFilterChain {
        val unauthorizedEntryPoint: AuthenticationEntryPoint = HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)
        http {
            csrf { disable() }
            anonymous { disable() }
            authorizeHttpRequests {
                authorize("/actuator/**", permitAll)
                authorize("/private/v1/auth/login", permitAll)
                authorize("/private/v1/auth/register", permitAll)
                authorize("/private/v1/auth/config", permitAll)
                authorize("/private/v1/auth/totp/enroll", permitAll)
                authorize("/private/v1/auth/totp/verify", permitAll)
                authorize("/private/v1/auth/google/link", permitAll)
                authorize("/private/v1/auth/session", authenticated)
                authorize("/private/v1/auth/logout", authenticated)
                authorize("/private/oauth2/**", permitAll)
                authorize("/private/login/oauth2/**", permitAll)
                authorize("/private/v1/users/**", hasAuthority("ROLE_ADMIN"))
                authorize(HttpMethod.POST, "/private/v1/stock-types/**", hasAuthority("ROLE_ADMIN"))
                authorize(HttpMethod.DELETE, "/private/v1/stock-types/**", hasAuthority("ROLE_ADMIN"))
                authorize(HttpMethod.POST, "/private/v1/fund-types/**", hasAuthority("ROLE_ADMIN"))
                authorize(HttpMethod.DELETE, "/private/v1/fund-types/**", hasAuthority("ROLE_ADMIN"))
                authorize(HttpMethod.PUT, "/private/v1/currency-rates/**", hasAuthority("ROLE_ADMIN"))
                authorize(anyRequest, hasAuthority("STATUS_APPROVED"))
            }
            exceptionHandling {
                authenticationEntryPoint = unauthorizedEntryPoint
                accessDeniedHandler = accessDeniedHandler(jsonMapper)
            }
            if (clientRegistrationRepository != null) {
                oauth2Login {
                    authorizationEndpoint {
                        baseUri = "/private/oauth2/authorization"
                    }
                    redirectionEndpoint {
                        baseUri = "/private/login/oauth2/code/*"
                    }
                    this.clientRegistrationRepository = clientRegistrationRepository
                    authenticationSuccessHandler = googleLoginSuccessHandler
                    authenticationFailureHandler = googleLoginFailureHandler
                }
            }
        }
        return http.build()
    }
}
