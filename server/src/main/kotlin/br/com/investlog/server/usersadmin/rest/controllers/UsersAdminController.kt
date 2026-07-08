package br.com.investlog.server.usersadmin.rest.controllers

import br.com.investlog.server.usersadmin.domain.services.UsersAdminService
import br.com.investlog.server.usersadmin.rest.payloads.UserAdminResponse
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/users")
class UsersAdminController(private val usersAdminService: UsersAdminService) {

    @GetMapping
    fun findAll(pageable: Pageable): PagedModel<UserAdminResponse> = usersAdminService.findAll(pageable)

    @PatchMapping("/{id}/approve")
    fun approve(@PathVariable id: UUID): UserAdminResponse = usersAdminService.approve(id)

    @PatchMapping("/{id}/reject")
    fun reject(@PathVariable id: UUID): UserAdminResponse = usersAdminService.reject(id)
}
