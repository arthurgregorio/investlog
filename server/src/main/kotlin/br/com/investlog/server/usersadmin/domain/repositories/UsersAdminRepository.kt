package br.com.investlog.server.usersadmin.domain.repositories

import br.com.investlog.server.jooq.system.tables.records.UsersRecord
import br.com.investlog.server.jooq.system.tables.references.USERS
import br.com.investlog.server.shared.persistence.pagedModelOf
import br.com.investlog.server.shared.security.AuthProvider
import br.com.investlog.server.shared.security.CurrentUser.Status
import br.com.investlog.server.shared.security.UserRole
import br.com.investlog.server.usersadmin.rest.payloads.UserAdminResponse
import org.jooq.DSLContext
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.util.UUID

@Repository
class UsersAdminRepository(private val dsl: DSLContext) {

    fun findAll(pageable: Pageable): PagedModel<UserAdminResponse> {
        val content = dsl.selectFrom(USERS)
            .orderBy(USERS.NAME)
            .limit(pageable.pageSize)
            .offset(pageable.offset.toInt())
            .fetch()
            .map { it.toAdminResponse() }

        val total = dsl.fetchCount(dsl.selectFrom(USERS))

        return pagedModelOf(content, pageable, total.toLong())
    }

    fun findByExternalId(externalId: UUID): UsersRecord? =
        dsl.selectFrom(USERS)
            .where(USERS.EXTERNAL_ID.eq(externalId))
            .fetchOne()

    fun updateStatus(userId: Long, status: Status): UserAdminResponse =
        dsl.update(USERS)
            .set(USERS.STATUS, status.name)
            .set(USERS.UPDATED_AT, OffsetDateTime.now())
            .where(USERS.ID.eq(userId))
            .returning()
            .fetchSingle()
            .toAdminResponse()

    fun updateRole(userId: Long, role: UserRole): UserAdminResponse =
        dsl.update(USERS)
            .set(USERS.ROLE, role.name)
            .set(USERS.UPDATED_AT, OffsetDateTime.now())
            .where(USERS.ID.eq(userId))
            .returning()
            .fetchSingle()
            .toAdminResponse()

    fun resetTotp(userId: Long): UserAdminResponse =
        dsl.update(USERS)
            .set(USERS.TOTP_SECRET, null as String?)
            .set(USERS.TOTP_ENABLED, false)
            .set(USERS.UPDATED_AT, OffsetDateTime.now())
            .where(USERS.ID.eq(userId))
            .returning()
            .fetchSingle()
            .toAdminResponse()

    fun updatePasswordHash(userId: Long, passwordHash: String): UserAdminResponse =
        dsl.update(USERS)
            .set(USERS.PASSWORD_HASH, passwordHash)
            .set(USERS.UPDATED_AT, OffsetDateTime.now())
            .where(USERS.ID.eq(userId))
            .returning()
            .fetchSingle()
            .toAdminResponse()

    fun deleteByExternalId(externalId: UUID): Int =
        dsl.deleteFrom(USERS)
            .where(USERS.EXTERNAL_ID.eq(externalId))
            .execute()

    private fun UsersRecord.toAdminResponse() = UserAdminResponse(
        id = externalId!!,
        name = name!!,
        email = email!!,
        role = UserRole.valueOf(role!!),
        status = Status.valueOf(status!!),
        authProvider = AuthProvider.valueOf(authProvider!!),
        totpEnabled = totpEnabled!!,
    )
}
