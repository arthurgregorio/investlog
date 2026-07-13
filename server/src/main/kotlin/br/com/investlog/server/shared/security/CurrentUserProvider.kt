package br.com.investlog.server.shared.security

import br.com.investlog.server.shared.exceptions.InvalidCredentialsException
import br.com.investlog.server.shared.exceptions.UserNotApprovedException
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
 *
 * This re-fetch also enforces approval status on every call: the security filter chain's
 * `STATUS_APPROVED` check only ever consults the session's authorities, which are fixed at login
 * time, so it alone would let a rejected or deleted user keep using every business endpoint for
 * the rest of their already-open session. Throwing here — rather than only at login — is what
 * actually revokes that access, on the user's very next request rather than their next login.
 */
@Component
class SecurityContextCurrentUserProvider(private val userRepository: UserRepository) : CurrentUserProvider {

    override fun getCurrentUser(): CurrentUser {
        val sessionPrincipal = SecurityContextHolder.getContext().authentication?.principal as? CurrentUser
            ?: error("No authenticated user in the current security context")

        val user = userRepository.findByEmail(sessionPrincipal.email)
            ?: throw InvalidCredentialsException("Authenticated user no longer exists")

        if (user.status != CurrentUser.Status.APPROVED) {
            throw UserNotApprovedException("User ${user.email} is not approved")
        }

        return user
    }
}
