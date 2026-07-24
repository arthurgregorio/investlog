package br.com.investlog.server.profile.rest.controllers

import br.com.investlog.server.profile.domain.services.ProfileService
import br.com.investlog.server.profile.rest.payloads.PasswordChangeRequest
import br.com.investlog.server.profile.rest.payloads.ProfileResponse
import br.com.investlog.server.profile.rest.payloads.ProfileUpdateRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/profile")
class ProfileController(
    private val profileService: ProfileService
) {

    @GetMapping
    fun getProfile(): ProfileResponse = profileService.getProfile()

    @PatchMapping
    fun updateProfile(@RequestBody request: ProfileUpdateRequest): ProfileResponse =
        profileService.updateProfile(request)

    @PatchMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun changePassword(@Valid @RequestBody request: PasswordChangeRequest) {
        profileService.changePassword(request)
    }
}
