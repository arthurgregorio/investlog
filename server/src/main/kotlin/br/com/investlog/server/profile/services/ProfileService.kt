package br.com.investlog.server.profile.services

import br.com.investlog.server.profile.rest.payloads.PasswordChangeRequest
import br.com.investlog.server.profile.rest.payloads.ProfileResponse
import br.com.investlog.server.profile.rest.payloads.ProfileUpdateRequest
import br.com.investlog.server.shared.exceptions.AccountNotLocalException
import br.com.investlog.server.shared.exceptions.InvalidCredentialsException
import br.com.investlog.server.shared.security.AuthProvider
import br.com.investlog.server.shared.security.CurrentUser
import br.com.investlog.server.shared.security.CurrentUserProvider
import br.com.investlog.server.shared.security.DemoModeGuard
import br.com.investlog.server.shared.security.UserRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class ProfileService(
    private val userRepository: UserRepository,
    private val currentUserProvider: CurrentUserProvider,
    private val passwordEncoder: PasswordEncoder,
    private val demoModeGuard: DemoModeGuard,
) {

    fun getProfile(): ProfileResponse = currentUserProvider.getCurrentUser().toResponse()

    @Transactional
    fun updateProfile(request: ProfileUpdateRequest): ProfileResponse {

        val user = currentUserProvider.getCurrentUser()

        return userRepository.updatePreferences(
            userId = user.id,
            accentColor = request.accentColor?.text ?: user.accentColor.text,
            preferredCurrency = request.preferredCurrency?.text ?: user.preferredCurrency,
        ).toResponse()
    }

    @Transactional
    fun changePassword(request: PasswordChangeRequest) {

        val user = currentUserProvider.getCurrentUser()

        if (user.authProvider != AuthProvider.LOCAL) {
            throw AccountNotLocalException("Alterações de senha estão disponíveis apenas para contas locais")
        }

        demoModeGuard.assertNotProtectedAdminAccount(user.email)

        val currentPasswordHash = userRepository.findPasswordHashByEmail(user.email)
            ?: throw InvalidCredentialsException("A senha atual está incorreta")

        if (!passwordEncoder.matches(request.currentPassword, currentPasswordHash)) {
            throw InvalidCredentialsException("A senha atual está incorreta")
        }

        userRepository.updatePasswordHash(user.id, passwordEncoder.encode(request.newPassword)!!)
    }

    private fun CurrentUser.toResponse() = ProfileResponse(
        name = name,
        email = email,
        avatarUrl = avatarUrl,
        accentColor = accentColor,
        preferredCurrency = preferredCurrency,
    )
}
