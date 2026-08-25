package br.com.investlog.server.auth.services

import br.com.investlog.server.auth.repositories.TrustedDeviceRepository
import br.com.investlog.server.auth.rest.payloads.TrustedDeviceResponse
import br.com.investlog.server.config.InvestlogConfigurations
import br.com.investlog.server.shared.exceptions.NotFoundException
import br.com.investlog.server.shared.security.CurrentUserProvider
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.OffsetDateTime
import java.util.Base64
import java.util.UUID

@Service
@Transactional(readOnly = true)
class TrustedDeviceService(
    private val trustedDeviceRepository: TrustedDeviceRepository,
    private val currentUserProvider: CurrentUserProvider,
    private val investlogConfigurations: InvestlogConfigurations,
) {

    @Transactional
    fun isTrusted(userId: Long, servletRequest: HttpServletRequest): Boolean {
        val token = readCookieToken(servletRequest) ?: return false
        val device = trustedDeviceRepository.findByTokenHashAndUserId(hashToken(token), userId) ?: return false
        trustedDeviceRepository.touch(device.id!!)
        return true
    }

    @Transactional
    fun trust(userId: Long, servletRequest: HttpServletRequest, servletResponse: HttpServletResponse) {
        val expiry = investlogConfigurations.security.trustedDevice.expiry
        val token = generateToken()
        val label = deriveDeviceLabel(servletRequest.getHeader("User-Agent"))

        trustedDeviceRepository.create(
            userId = userId,
            tokenHash = hashToken(token),
            label = label,
            expiresAt = OffsetDateTime.now().plus(expiry),
        )

        val cookie = ResponseCookie.from(COOKIE_NAME, token)
            .httpOnly(true)
            .secure(servletRequest.isSecure)
            .sameSite("Lax")
            .path(COOKIE_PATH)
            .maxAge(expiry)
            .build()

        servletResponse.addHeader(HttpHeaders.SET_COOKIE, cookie.toString())
    }

    fun findAllForCurrentUser(): List<TrustedDeviceResponse> =
        trustedDeviceRepository.findAllByUserId(currentUserProvider.getCurrentUser().id)

    @Transactional
    fun revoke(externalId: UUID) {
        val userId = currentUserProvider.getCurrentUser().id
        val deletedCount = trustedDeviceRepository.deleteByExternalIdAndUserId(externalId, userId)
        if (deletedCount == 0) {
            throw NotFoundException("Dispositivo confiável $externalId não encontrado")
        }
    }

    private fun readCookieToken(servletRequest: HttpServletRequest): String? =
        servletRequest.cookies?.firstOrNull { it.name == COOKIE_NAME }?.value

    private fun generateToken(): String {
        val randomBytes = ByteArray(32)
        SecureRandom().nextBytes(randomBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes)
    }

    private fun hashToken(token: String): String {
        val digestBytes = MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8))
        return digestBytes.joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun deriveDeviceLabel(userAgent: String?): String {
        if (userAgent.isNullOrBlank()) return "Dispositivo desconhecido"

        val browser = when {
            userAgent.contains("Edg/") -> "Edge"
            userAgent.contains("OPR/") || userAgent.contains("Opera") -> "Opera"
            userAgent.contains("Chrome/") -> "Chrome"
            userAgent.contains("Firefox/") -> "Firefox"
            userAgent.contains("Safari/") && userAgent.contains("Version/") -> "Safari"
            else -> "Navegador"
        }

        val operatingSystem = when {
            userAgent.contains("Windows") -> "Windows"
            userAgent.contains("Mac OS X") -> "macOS"
            userAgent.contains("Android") -> "Android"
            userAgent.contains("iPhone") || userAgent.contains("iPad") -> "iOS"
            userAgent.contains("Linux") -> "Linux"
            else -> null
        }

        return if (operatingSystem != null) "$browser em $operatingSystem" else browser
    }

    companion object {
        const val COOKIE_NAME = "trusted_device"
        private const val COOKIE_PATH = "/private/v1/auth"
    }
}
