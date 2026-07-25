package br.com.investlog.server.auth.security

import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class PendingGoogleLink(
    val googleSub: String,
    val email: String,
    val name: String,
    val avatarUrl: String?,
    val expiresAt: Instant,
)

/**
 * Holds the pending Google profile for an account-linking offer between the OAuth2 redirect
 * (which can't carry arbitrary state itself) and the client's follow-up `POST /auth/google/link`
 * call. In-memory and single-instance is a deliberate, minimal choice — this app has no
 * multi-instance deployment story, so there's no need for a distributed cache just to survive a
 * short-lived, one-time token.
 */
@Component
class GoogleLinkTokenStore {

    private val pendingLinks = ConcurrentHashMap<String, PendingGoogleLink>()

    fun issue(googleSub: String, email: String, name: String, avatarUrl: String?): String {
        pendingLinks.entries.removeIf { it.value.expiresAt.isBefore(Instant.now()) }

        val token = UUID.randomUUID().toString()
        pendingLinks[token] = PendingGoogleLink(
            googleSub = googleSub,
            email = email,
            name = name,
            avatarUrl = avatarUrl,
            expiresAt = Instant.now().plusSeconds(TOKEN_TTL_SECONDS),
        )
        return token
    }

    /** One-time use: the token is removed whether or not it's still valid. */
    fun consume(token: String): PendingGoogleLink? {
        val pending = pendingLinks.remove(token) ?: return null
        return pending.takeIf { it.expiresAt.isAfter(Instant.now()) }
    }

    companion object {
        private const val TOKEN_TTL_SECONDS = 600L
    }
}
