package br.com.investlog.server.profile.domain.services

import br.com.investlog.server.profile.rest.payloads.AccentColor
import br.com.investlog.server.profile.rest.payloads.ProfileResponse
import br.com.investlog.server.profile.rest.payloads.ProfileUpdateRequest
import br.com.investlog.server.shared.rest.payloads.CurrencyCode
import br.com.investlog.server.shared.security.CurrentUser
import br.com.investlog.server.shared.security.CurrentUserProvider
import br.com.investlog.server.shared.security.UserRepository
import org.springframework.stereotype.Service

@Service
class ProfileService(
    private val userRepository: UserRepository,
    private val currentUserProvider: CurrentUserProvider
) {

    fun getProfile(): ProfileResponse = currentUserProvider.getCurrentUser().toResponse()

    fun updateProfile(request: ProfileUpdateRequest): ProfileResponse {

        val user = currentUserProvider.getCurrentUser()

        return userRepository.updatePreferences(
            userId = user.id,
            accentColor = request.accentColor?.text ?: AccentColor.TEAL.text,
            preferredCurrency = request.preferredCurrency?.text ?: CurrencyCode.BRL.name,
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
