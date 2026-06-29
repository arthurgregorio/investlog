package br.com.investlog.server.shared.security

import br.com.investlog.server.shared.exceptions.InvalidCredentialsException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

interface CurrentUserProvider {
    fun getCurrentUser(): CurrentUser
}

/**
 * Resolves the current user's identity from the session's [Authentication] principal, then
 * re-fetches the row from the database so callers always see the latest persisted preferences
 * (the session principal is set once at login and is never refreshed in place, so reading it
 * directly would return stale `accentColor`/`preferredCurrency` values after a profile update).
 */
@Component
class SecurityContextCurrentUserProvider(private val userRepository: UserRepository) : CurrentUserProvider {

    override fun getCurrentUser(): CurrentUser {
        val sessionPrincipal = SecurityContextHolder.getContext().authentication?.principal as? CurrentUser
            ?: error("No authenticated user in the current security context")

        return userRepository.findByEmail(sessionPrincipal.email)
            ?: throw InvalidCredentialsException("Authenticated user no longer exists")
    }
}
