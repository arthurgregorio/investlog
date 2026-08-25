package br.com.investlog.server.auth.repositories

import br.com.investlog.server.auth.rest.payloads.TrustedDeviceResponse
import br.com.investlog.server.jooq.system.tables.records.TrustedDevicesRecord
import br.com.investlog.server.jooq.system.tables.references.TRUSTED_DEVICES
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.util.UUID

@Repository
class TrustedDeviceRepository(private val dsl: DSLContext) {

    fun findByTokenHashAndUserId(tokenHash: String, userId: Long): TrustedDevicesRecord? =
        dsl.selectFrom(TRUSTED_DEVICES)
            .where(TRUSTED_DEVICES.TOKEN_HASH.eq(tokenHash))
            .and(TRUSTED_DEVICES.USER_ID.eq(userId))
            .and(TRUSTED_DEVICES.EXPIRES_AT.gt(OffsetDateTime.now()))
            .fetchOne()

    fun touch(id: Long) {
        dsl.update(TRUSTED_DEVICES)
            .set(TRUSTED_DEVICES.LAST_USED_AT, OffsetDateTime.now())
            .where(TRUSTED_DEVICES.ID.eq(id))
            .execute()
    }

    fun create(userId: Long, tokenHash: String, label: String, expiresAt: OffsetDateTime) {
        dsl.insertInto(TRUSTED_DEVICES)
            .set(TRUSTED_DEVICES.USER_ID, userId)
            .set(TRUSTED_DEVICES.TOKEN_HASH, tokenHash)
            .set(TRUSTED_DEVICES.LABEL, label)
            .set(TRUSTED_DEVICES.EXPIRES_AT, expiresAt)
            .execute()
    }

    fun findAllByUserId(userId: Long): List<TrustedDeviceResponse> =
        dsl.selectFrom(TRUSTED_DEVICES)
            .where(TRUSTED_DEVICES.USER_ID.eq(userId))
            .orderBy(TRUSTED_DEVICES.LAST_USED_AT.desc())
            .fetch()
            .map { it.toResponse() }

    fun deleteByExternalIdAndUserId(externalId: UUID, userId: Long): Int =
        dsl.deleteFrom(TRUSTED_DEVICES)
            .where(TRUSTED_DEVICES.EXTERNAL_ID.eq(externalId))
            .and(TRUSTED_DEVICES.USER_ID.eq(userId))
            .execute()

    private fun TrustedDevicesRecord.toResponse() = TrustedDeviceResponse(
        id = externalId!!,
        label = label!!,
        lastUsedAt = lastUsedAt!!,
        expiresAt = expiresAt!!,
    )
}
