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
        userRepository.findByGoogleSub(DEV_USER_GOOGLE_SUB)
            ?: error("Dev user '$DEV_USER_GOOGLE_SUB' not found — check the 14-1050-seed-dev-data.xml changeset")

    companion object {
        private const val DEV_USER_GOOGLE_SUB = "dev-user"
    }
}
