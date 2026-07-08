package br.com.investlog.server.usersadmin.domain.services

import br.com.investlog.server.shared.exceptions.NotFoundException
import br.com.investlog.server.shared.security.UserStatus
import br.com.investlog.server.usersadmin.domain.repositories.UsersAdminRepository
import br.com.investlog.server.usersadmin.rest.payloads.UserAdminResponse
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class UsersAdminService(private val usersAdminRepository: UsersAdminRepository) {

    fun findAll(pageable: Pageable): PagedModel<UserAdminResponse> = usersAdminRepository.findAll(pageable)

    fun approve(externalId: UUID): UserAdminResponse = updateStatus(externalId, UserStatus.APPROVED)

    fun reject(externalId: UUID): UserAdminResponse = updateStatus(externalId, UserStatus.REJECTED)

    private fun updateStatus(externalId: UUID, status: UserStatus): UserAdminResponse {
        val user = usersAdminRepository.findByExternalId(externalId)
            ?: throw NotFoundException("User $externalId not found")

        return usersAdminRepository.updateStatus(user.id!!, status)
    }
}
