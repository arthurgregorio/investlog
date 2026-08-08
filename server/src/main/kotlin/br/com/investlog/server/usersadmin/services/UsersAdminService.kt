package br.com.investlog.server.usersadmin.services

import br.com.investlog.server.auth.security.TotpAttemptLimiter
import br.com.investlog.server.jooq.system.tables.records.UsersRecord
import br.com.investlog.server.shared.exceptions.AccountNotLocalException
import br.com.investlog.server.shared.exceptions.InvalidUserStatusTransitionException
import br.com.investlog.server.shared.exceptions.NotFoundException
import br.com.investlog.server.shared.exceptions.SelfActionNotAllowedException
import br.com.investlog.server.shared.security.AuthProvider
import br.com.investlog.server.shared.security.CurrentUser.Status
import br.com.investlog.server.shared.security.CurrentUserProvider
import br.com.investlog.server.shared.security.DemoModeGuard
import br.com.investlog.server.usersadmin.repositories.UsersAdminRepository
import br.com.investlog.server.usersadmin.rest.payloads.PasswordResetRequest
import br.com.investlog.server.usersadmin.rest.payloads.RoleUpdateRequest
import br.com.investlog.server.usersadmin.rest.payloads.UserAdminResponse
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class UsersAdminService(
    private val currentUserProvider: CurrentUserProvider,
    private val usersAdminRepository: UsersAdminRepository,
    private val passwordEncoder: PasswordEncoder,
    private val totpAttemptLimiter: TotpAttemptLimiter,
    private val demoModeGuard: DemoModeGuard,
) {

    fun findAll(pageable: Pageable): PagedModel<UserAdminResponse> = usersAdminRepository.findAll(pageable)

    @Transactional
    fun approve(externalId: UUID): UserAdminResponse = updateStatus(externalId, Status.APPROVED)

    @Transactional
    fun block(externalId: UUID): UserAdminResponse {
        val user = requireUser(externalId)
        requireNotSelf(user)
        demoModeGuard.assertNotProtectedAdminAccount(user.email!!)
        requireCurrentStatus(user, Status.APPROVED, "block")
        return updateStatus(externalId, Status.BLOCKED)
    }

    @Transactional
    fun unblock(externalId: UUID): UserAdminResponse {
        val user = requireUser(externalId)
        requireCurrentStatus(user, Status.BLOCKED, "unblock")
        return updateStatus(externalId, Status.APPROVED)
    }

    @Transactional
    fun changeRole(externalId: UUID, request: RoleUpdateRequest): UserAdminResponse {
        val user = requireUser(externalId)
        requireNotSelf(user)
        demoModeGuard.assertNotProtectedAdminAccount(user.email!!)
        return usersAdminRepository.updateRole(user.id!!, request.role)
    }

    @Transactional
    fun resetTotp(externalId: UUID): UserAdminResponse {
        val user = requireUser(externalId)
        totpAttemptLimiter.recordSuccess(user.email!!)
        return usersAdminRepository.resetTotp(user.id!!)
    }

    @Transactional
    fun delete(externalId: UUID) {
        val user = requireUser(externalId)
        requireNotSelf(user)
        demoModeGuard.assertNotProtectedAdminAccount(user.email!!)
        usersAdminRepository.deleteByExternalId(externalId)
    }

    @Transactional
    fun resetPassword(externalId: UUID, request: PasswordResetRequest): UserAdminResponse {
        val user = requireUser(externalId)
        requireNotSelf(user)

        if (AuthProvider.valueOf(user.authProvider!!) != AuthProvider.LOCAL) {
            throw AccountNotLocalException("Password resets are only available for local accounts")
        }

        demoModeGuard.assertNotProtectedAdminAccount(user.email!!)

        return usersAdminRepository.updatePasswordHash(user.id!!, passwordEncoder.encode(request.newPassword)!!)
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

    private fun requireCurrentStatus(user: UsersRecord, expected: Status, action: String) {
        if (Status.valueOf(user.status!!) != expected) {
            throw InvalidUserStatusTransitionException("Cannot $action a user whose current status is not ${expected.name}")
        }
    }
}
