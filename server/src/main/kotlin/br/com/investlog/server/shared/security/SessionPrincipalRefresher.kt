package br.com.investlog.server.shared.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.stereotype.Component

@Component
class SessionPrincipalRefresher {

    fun refresh(user: CurrentUser, servletRequest: HttpServletRequest, servletResponse: HttpServletResponse) {

        val authorities = SecurityContextHolder.getContext().authentication?.authorities
            ?: error("No authenticated user in the current security context")

        val context = SecurityContextHolder.createEmptyContext()
        context.authentication = UsernamePasswordAuthenticationToken(user, null, authorities)
        SecurityContextHolder.setContext(context)

        HttpSessionSecurityContextRepository().saveContext(context, servletRequest, servletResponse)
    }
}
