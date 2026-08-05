package br.com.investlog.server.profile.rest

import br.com.investlog.server.profile.services.ProfileService
import br.com.investlog.server.profile.rest.payloads.PasswordChangeRequest
import br.com.investlog.server.profile.rest.payloads.ProfileResponse
import br.com.investlog.server.profile.rest.payloads.ProfileUpdateRequest
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
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
    fun getProfile(): ResponseEntity<ProfileResponse> = ResponseEntity.ok(profileService.getProfile())

    @PatchMapping
    fun updateProfile(@RequestBody request: ProfileUpdateRequest): ResponseEntity<ProfileResponse> =
        ResponseEntity.ok(profileService.updateProfile(request))

    @PatchMapping("/password")
    fun changePassword(@Valid @RequestBody request: PasswordChangeRequest): ResponseEntity<Void> {
        profileService.changePassword(request)
        return ResponseEntity.noContent().build()
    }
}
