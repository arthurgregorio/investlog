package br.com.investlog.server.usersadmin.domain.services

import br.com.investlog.server.auth.security.TotpAttemptLimiter
import br.com.investlog.server.jooq.system.tables.records.UsersRecord
import br.com.investlog.server.shared.exceptions.NotFoundException
import br.com.investlog.server.shared.exceptions.SelfActionNotAllowedException
import br.com.investlog.server.shared.security.CurrentUser.Status
import br.com.investlog.server.shared.security.CurrentUserProvider
import br.com.investlog.server.usersadmin.domain.repositories.UsersAdminRepository
import br.com.investlog.server.usersadmin.rest.payloads.RoleUpdateRequest
import br.com.investlog.server.usersadmin.rest.payloads.UserAdminResponse
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class UsersAdminService(
    private val currentUserProvider: CurrentUserProvider,
    private val usersAdminRepository: UsersAdminRepository,
    private val totpAttemptLimiter: TotpAttemptLimiter,
) {

    fun findAll(pageable: Pageable): PagedModel<UserAdminResponse> = usersAdminRepository.findAll(pageable)

    fun approve(externalId: UUID): UserAdminResponse = updateStatus(externalId, Status.APPROVED)

    fun reject(externalId: UUID): UserAdminResponse {
        val user = requireUser(externalId)
        requireNotSelf(user)
        return updateStatus(externalId, Status.REJECTED)
    }

    fun changeRole(externalId: UUID, request: RoleUpdateRequest): UserAdminResponse {
        val user = requireUser(externalId)
        requireNotSelf(user)
        return usersAdminRepository.updateRole(user.id!!, request.role)
    }

    fun resetTotp(externalId: UUID): UserAdminResponse {
        val user = requireUser(externalId)
        totpAttemptLimiter.recordSuccess(user.email!!)
        return usersAdminRepository.resetTotp(user.id!!)
    }

    fun delete(externalId: UUID) {
        val user = requireUser(externalId)
        requireNotSelf(user)
        usersAdminRepository.deleteByExternalId(externalId)
    }

    private fun updateStatus(externalId: UUID, status: Status): UserAdminResponse {
        val user = requireUser(externalId)
        return usersAdminRepository.updateStatus(user.id!!, status)
    }

    private fun requireUser(externalId: UUID): UsersRecord =
        usersAdminRepository.findByExternalId(externalId)
            ?: throw NotFoundException("User $externalId not found")

    private fun requireNotSelf(user: UsersRecord) {
        if (user.id == currentUserProvider.getCurrentUser().id) {
            throw SelfActionNotAllowedException("This action cannot target your own account")
        }
    }
}
