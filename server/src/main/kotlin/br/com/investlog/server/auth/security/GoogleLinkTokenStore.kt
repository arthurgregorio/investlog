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

    fun consume(token: String): PendingGoogleLink? {
        val pending = pendingLinks.remove(token) ?: return null
        return pending.takeIf { it.expiresAt.isAfter(Instant.now()) }
    }

    companion object {
        private const val TOKEN_TTL_SECONDS = 600L
    }
}
