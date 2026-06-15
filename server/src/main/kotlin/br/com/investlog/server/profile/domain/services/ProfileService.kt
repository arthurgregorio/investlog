package br.com.investlog.server.profile.domain.services

import br.com.investlog.server.profile.rest.dtos.ProfileResponse
import br.com.investlog.server.profile.rest.dtos.ProfileUpdateRequest
import br.com.investlog.server.shared.security.CurrentUser
import br.com.investlog.server.shared.security.CurrentUserProvider
import br.com.investlog.server.shared.security.UserRepository
import org.springframework.stereotype.Service

@Service
class ProfileService(
    private val currentUserProvider: CurrentUserProvider,
    private val userRepository: UserRepository,
) {

    fun getProfile(): ProfileResponse =
        currentUserProvider.getCurrentUser().toResponse()

    fun updateProfile(request: ProfileUpdateRequest): ProfileResponse {
        val user = currentUserProvider.getCurrentUser()

        return userRepository.updatePreferences(
            userId = user.id,
            accentColor = request.accentColor ?: user.accentColor,
            preferredCurrency = request.preferredCurrency ?: user.preferredCurrency,
        ).toResponse()
    }

    private fun CurrentUser.toResponse() = ProfileResponse(
        name = name,
        email = email,
        avatarUrl = avatarUrl,
        accentColor = accentColor,
        preferredCurrency = preferredCurrency,
    )
}
