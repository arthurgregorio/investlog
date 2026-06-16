package br.com.investlog.server.profile.rest.controllers

import br.com.investlog.server.profile.domain.services.ProfileService
import br.com.investlog.server.profile.rest.payloads.ProfileResponse
import br.com.investlog.server.profile.rest.payloads.ProfileUpdateRequest
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
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
}
