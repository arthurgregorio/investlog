package br.com.investlog.server.shared.security

import org.springframework.stereotype.Component

@Component
class ActiveAdministratorGuard(private val currentUserProvider: CurrentUserProvider) {

    fun isActiveAdministrator(): Boolean = currentUserProvider.getCurrentUser().role == UserRole.ADMIN
}
