package br.com.investlog.server.shared.security

import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

interface CurrentUserProvider {
    fun getCurrentUser(): CurrentUser
}

@Component
class SecurityContextCurrentUserProvider : CurrentUserProvider {

    override fun getCurrentUser(): CurrentUser =
        SecurityContextHolder.getContext().authentication?.principal as? CurrentUser
            ?: error("No authenticated user in the current security context")
}
