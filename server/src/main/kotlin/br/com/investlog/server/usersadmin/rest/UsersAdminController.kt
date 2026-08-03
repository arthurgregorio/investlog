package br.com.investlog.server.usersadmin.rest

import br.com.investlog.server.usersadmin.rest.payloads.PasswordResetRequest
import br.com.investlog.server.usersadmin.rest.payloads.RoleUpdateRequest
import br.com.investlog.server.usersadmin.rest.payloads.UserAdminResponse
import br.com.investlog.server.usersadmin.services.UsersAdminService
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/users")
class UsersAdminController(private val usersAdminService: UsersAdminService) {

    @GetMapping
    fun findAll(pageable: Pageable): ResponseEntity<PagedModel<UserAdminResponse>> =
        ResponseEntity.ok(usersAdminService.findAll(pageable))

    @PatchMapping("/{id}/approve")
    fun approve(@PathVariable id: UUID): ResponseEntity<UserAdminResponse> =
        ResponseEntity.ok(usersAdminService.approve(id))

    @PatchMapping("/{id}/block")
    fun block(@PathVariable id: UUID): ResponseEntity<UserAdminResponse> =
        ResponseEntity.ok(usersAdminService.block(id))

    @PatchMapping("/{id}/unblock")
    fun unblock(@PathVariable id: UUID): ResponseEntity<UserAdminResponse> =
        ResponseEntity.ok(usersAdminService.unblock(id))

    @PatchMapping("/{id}/role")
    fun changeRole(@PathVariable id: UUID, @RequestBody request: RoleUpdateRequest): ResponseEntity<UserAdminResponse> =
        ResponseEntity.ok(usersAdminService.changeRole(id, request))

    @PatchMapping("/{id}/totp-reset")
    fun resetTotp(@PathVariable id: UUID): ResponseEntity<UserAdminResponse> =
        ResponseEntity.ok(usersAdminService.resetTotp(id))

    @PatchMapping("/{id}/password")
    fun resetPassword(@PathVariable id: UUID, @Valid @RequestBody request: PasswordResetRequest): ResponseEntity<UserAdminResponse> =
        ResponseEntity.ok(usersAdminService.resetPassword(id, request))

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: UUID): ResponseEntity<Void> {
        usersAdminService.delete(id)
        return ResponseEntity.noContent().build()
    }
}
