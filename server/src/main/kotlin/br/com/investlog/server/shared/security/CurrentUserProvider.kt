package br.com.investlog.server.shared.security

import org.springframework.stereotype.Component

interface CurrentUserProvider {
    fun getCurrentUser(): CurrentUser
}

@Component
class FixedCurrentUserProvider(
    private val userRepository: UserRepository,
) : CurrentUserProvider {

    override fun getCurrentUser(): CurrentUser =
        userRepository.findByEmail(DEV_USER_EMAIL)
            ?: error("Dev user '$DEV_USER_EMAIL' not found — check the 14-1050-seed-dev-data.xml changeset")

    companion object {
        private const val DEV_USER_EMAIL = "admin@admin.com"
    }
}
