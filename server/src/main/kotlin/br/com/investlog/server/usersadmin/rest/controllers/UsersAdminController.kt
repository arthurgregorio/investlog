package br.com.investlog.server.usersadmin.rest.controllers

import br.com.investlog.server.usersadmin.domain.services.UsersAdminService
import br.com.investlog.server.usersadmin.rest.payloads.PasswordResetRequest
import br.com.investlog.server.usersadmin.rest.payloads.RoleUpdateRequest
import br.com.investlog.server.usersadmin.rest.payloads.UserAdminResponse
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/users")
class UsersAdminController(private val usersAdminService: UsersAdminService) {

    @GetMapping
    fun findAll(pageable: Pageable): PagedModel<UserAdminResponse> = usersAdminService.findAll(pageable)

    @PatchMapping("/{id}/approve")
    fun approve(@PathVariable id: UUID): UserAdminResponse = usersAdminService.approve(id)

    @PatchMapping("/{id}/block")
    fun block(@PathVariable id: UUID): UserAdminResponse = usersAdminService.block(id)

    @PatchMapping("/{id}/unblock")
    fun unblock(@PathVariable id: UUID): UserAdminResponse = usersAdminService.unblock(id)

    @PatchMapping("/{id}/role")
    fun changeRole(@PathVariable id: UUID, @RequestBody request: RoleUpdateRequest): UserAdminResponse =
        usersAdminService.changeRole(id, request)

    @PatchMapping("/{id}/totp-reset")
    fun resetTotp(@PathVariable id: UUID): UserAdminResponse = usersAdminService.resetTotp(id)

    @PatchMapping("/{id}/password")
    fun resetPassword(@PathVariable id: UUID, @Valid @RequestBody request: PasswordResetRequest): UserAdminResponse =
        usersAdminService.resetPassword(id, request)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: UUID) {
        usersAdminService.delete(id)
    }
}
