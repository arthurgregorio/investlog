package br.com.investlog.server.shared.security

import br.com.investlog.server.jooq.system.tables.records.UsersRecord
import br.com.investlog.server.jooq.system.tables.references.USERS
import br.com.investlog.server.profile.rest.payloads.AccentColor
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
class UserRepository(private val dsl: DSLContext) {

    fun findByGoogleSub(googleSub: String): CurrentUser? {
        return dsl.selectFrom(USERS)
            .where(USERS.GOOGLE_SUB.eq(googleSub))
            .fetchOne()
            ?.toCurrentUser()
    }

    fun findByEmail(email: String): CurrentUser? {
        return dsl.selectFrom(USERS)
            .where(USERS.EMAIL.eq(email))
            .fetchOne()
            ?.toCurrentUser()
    }

    fun findPasswordHashByEmail(email: String): String? {
        return dsl.select(USERS.PASSWORD_HASH)
            .from(USERS)
            .where(USERS.EMAIL.eq(email))
            .fetchOne(USERS.PASSWORD_HASH)
    }

    fun updatePasswordHash(userId: Long, passwordHash: String) {
        dsl.update(USERS)
            .set(USERS.PASSWORD_HASH, passwordHash)
            .set(USERS.UPDATED_AT, OffsetDateTime.now())
            .where(USERS.ID.eq(userId))
            .execute()
    }

    fun createLocalUser(name: String, email: String, passwordHash: String): CurrentUser =
        dsl.insertInto(USERS)
            .set(USERS.NAME, name)
            .set(USERS.EMAIL, email)
            .set(USERS.PASSWORD_HASH, passwordHash)
            .set(USERS.AUTH_PROVIDER, AuthProvider.LOCAL.name)
            .set(USERS.ROLE, UserRole.USER.name)
            .set(USERS.STATUS, CurrentUser.Status.PENDING.name)
            .returning()
            .fetchSingle()
            .toCurrentUser()

    fun createGoogleUser(googleSub: String, email: String, name: String, avatarUrl: String?): CurrentUser =
        dsl.insertInto(USERS)
            .set(USERS.NAME, name)
            .set(USERS.EMAIL, email)
            .set(USERS.GOOGLE_SUB, googleSub)
            .set(USERS.AVATAR_URL, avatarUrl)
            .set(USERS.AUTH_PROVIDER, AuthProvider.GOOGLE.name)
            .set(USERS.ROLE, UserRole.USER.name)
            .set(USERS.STATUS, CurrentUser.Status.PENDING.name)
            .returning()
            .fetchSingle()
            .toCurrentUser()

    fun findTotpSecretByEmail(email: String): String? {
        return dsl.select(USERS.TOTP_SECRET)
            .from(USERS)
            .where(USERS.EMAIL.eq(email))
            .fetchOne(USERS.TOTP_SECRET)
    }

    fun updateTotpSecret(userId: Long, secret: String) {
        dsl.update(USERS)
            .set(USERS.TOTP_SECRET, secret)
            .set(USERS.UPDATED_AT, OffsetDateTime.now())
            .where(USERS.ID.eq(userId))
            .execute()
    }

    fun enableTotp(userId: Long, secret: String) {
        dsl.update(USERS)
            .set(USERS.TOTP_SECRET, secret)
            .set(USERS.TOTP_ENABLED, true)
            .set(USERS.UPDATED_AT, OffsetDateTime.now())
            .where(USERS.ID.eq(userId))
            .execute()
    }

    fun updatePreferences(userId: Long, accentColor: String, preferredCurrency: String): CurrentUser {
        return dsl.update(USERS)
            .set(USERS.ACCENT_COLOR, accentColor)
            .set(USERS.PREFERRED_CURRENCY, preferredCurrency)
            .set(USERS.UPDATED_AT, OffsetDateTime.now())
            .where(USERS.ID.eq(userId))
            .returning()
            .fetchSingle()
            .toCurrentUser()
    }

    private fun UsersRecord.toCurrentUser() = CurrentUser(
        id = id!!,
        externalId = externalId!!,
        name = name!!,
        email = email!!,
        avatarUrl = avatarUrl,
        accentColor = AccentColor.fromText(accentColor),
        preferredCurrency = preferredCurrency!!,
        role = UserRole.valueOf(role!!),
        status = CurrentUser.Status.valueOf(status!!),
        authProvider = AuthProvider.valueOf(authProvider!!),
        totpEnabled = totpEnabled!!,
    )
}
