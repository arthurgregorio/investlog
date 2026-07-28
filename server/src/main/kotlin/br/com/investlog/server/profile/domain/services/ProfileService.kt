package br.com.investlog.server.profile.domain.services

import br.com.investlog.server.profile.rest.payloads.PasswordChangeRequest
import br.com.investlog.server.profile.rest.payloads.ProfileResponse
import br.com.investlog.server.profile.rest.payloads.ProfileUpdateRequest
import br.com.investlog.server.shared.exceptions.AccountNotLocalException
import br.com.investlog.server.shared.exceptions.InvalidCredentialsException
import br.com.investlog.server.shared.security.AuthProvider
import br.com.investlog.server.shared.security.CurrentUser
import br.com.investlog.server.shared.security.CurrentUserProvider
import br.com.investlog.server.shared.security.UserRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

@Service
class ProfileService(
    private val userRepository: UserRepository,
    private val currentUserProvider: CurrentUserProvider,
    private val passwordEncoder: PasswordEncoder,
) {

    fun getProfile(): ProfileResponse = currentUserProvider.getCurrentUser().toResponse()

    fun updateProfile(request: ProfileUpdateRequest): ProfileResponse {

        val user = currentUserProvider.getCurrentUser()

        return userRepository.updatePreferences(
            userId = user.id,
            accentColor = request.accentColor?.text ?: user.accentColor.text,
            preferredCurrency = request.preferredCurrency?.text ?: user.preferredCurrency,
        ).toResponse()
    }

    fun changePassword(request: PasswordChangeRequest) {

        val user = currentUserProvider.getCurrentUser()

        if (user.authProvider != AuthProvider.LOCAL) {
            throw AccountNotLocalException("Password changes are only available for local accounts")
        }

        val currentPasswordHash = userRepository.findPasswordHashByEmail(user.email)
            ?: throw InvalidCredentialsException("Current password is incorrect")

        if (!passwordEncoder.matches(request.currentPassword, currentPasswordHash)) {
            throw InvalidCredentialsException("Current password is incorrect")
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
