package br.com.investlog.server.shared.security

import br.com.investlog.server.shared.exceptions.InvalidCredentialsException
import br.com.investlog.server.shared.exceptions.UserNotApprovedException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

interface CurrentUserProvider {
    fun getCurrentUser(): CurrentUser
}

@Component
class SecurityContextCurrentUserProvider(private val userRepository: UserRepository) : CurrentUserProvider {

    override fun getCurrentUser(): CurrentUser {
        val sessionPrincipal = SecurityContextHolder.getContext().authentication?.principal as? CurrentUser
            ?: error("No authenticated user in the current security context")

        val user = userRepository.findByEmail(sessionPrincipal.email)
            ?: throw InvalidCredentialsException("O usuário autenticado não existe mais")

        if (user.status != CurrentUser.Status.APPROVED) {
            throw UserNotApprovedException("O usuário ${user.email} não está aprovado")
        }

        return user
    }
}
