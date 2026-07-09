package br.com.investlog.server.config

import br.com.investlog.server.shared.rest.payloads.AccessDeniedResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import tools.jackson.databind.ObjectMapper

@Configuration
class SecurityConfig {

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun accessDeniedHandler(objectMapper: ObjectMapper): AccessDeniedHandler = AccessDeniedHandler { _, response, _ ->
        val authentication = SecurityContextHolder.getContext().authentication
        val isPendingApproval = authentication?.authorities.orEmpty().none { it.authority == "STATUS_APPROVED" }

        val body = if (isPendingApproval) {
            AccessDeniedResponse("pending_approval", "Your account is pending administrator approval")
        } else {
            AccessDeniedResponse("forbidden", "You do not have permission to perform this action")
        }

        response.status = HttpStatus.FORBIDDEN.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.writer.write(objectMapper.writeValueAsString(body))
    }

    @Bean
    fun securityFilterChain(http: HttpSecurity, objectMapper: ObjectMapper): SecurityFilterChain {
        val unauthorizedEntryPoint: AuthenticationEntryPoint = HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)
        http {
            csrf { disable() }
            anonymous { disable() }
            authorizeHttpRequests {
                authorize("/private/v1/auth/login", permitAll)
                authorize("/private/v1/auth/register", permitAll)
                authorize("/private/v1/auth/totp/enroll", permitAll)
                authorize("/private/v1/auth/totp/verify", permitAll)
                authorize("/private/v1/auth/session", authenticated)
                authorize("/private/v1/auth/logout", authenticated)
                authorize("/private/v1/users/**", hasAuthority("ROLE_ADMIN"))
                authorize(anyRequest, hasAuthority("STATUS_APPROVED"))
            }
            exceptionHandling {
                authenticationEntryPoint = unauthorizedEntryPoint
                accessDeniedHandler = accessDeniedHandler(objectMapper)
            }
        }
        return http.build()
    }
}
