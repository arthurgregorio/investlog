package br.com.investlog.server.auth.domain.services

import br.com.investlog.server.auth.rest.payloads.AuthConfigResponse
import br.com.investlog.server.auth.rest.payloads.LoginRequest
import br.com.investlog.server.auth.rest.payloads.RegisterRequest
import br.com.investlog.server.auth.rest.payloads.SessionResponse
import br.com.investlog.server.auth.rest.payloads.TotpEnrollRequest
import br.com.investlog.server.auth.rest.payloads.TotpEnrollResponse
import br.com.investlog.server.auth.rest.payloads.TotpVerifyRequest
import br.com.investlog.server.shared.exceptions.GoogleAccountEmailInUseException
import br.com.investlog.server.shared.exceptions.InvalidCredentialsException
import br.com.investlog.server.shared.exceptions.InvalidTotpCodeException
import br.com.investlog.server.shared.exceptions.TotpAlreadyEnabledException
import br.com.investlog.server.shared.exceptions.TotpRequiredException
import br.com.investlog.server.shared.security.CurrentUser
import br.com.investlog.server.shared.security.UserRepository
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.stereotype.Service

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val totpService: TotpService,
    @Value("\${investlog.google-auth-enabled:false}") private val googleAuthEnabled: Boolean,
) {

    fun login(request: LoginRequest, servletRequest: HttpServletRequest, servletResponse: HttpServletResponse): LoginResult {

        if (userRepository.findByEmail(request.email)?.status == CurrentUser.Status.BLOCKED) {
            throw InvalidCredentialsException(BLOCKED_MESSAGE)
        }

        val user = verifyCredentials(request.email, request.password)

        if (!user.totpEnabled) {
            return LoginResult.EnrollmentRequired
        }

        val code = request.totpCode
            ?: throw TotpRequiredException("A TOTP code is required to complete login")

        val secret = userRepository.findTotpSecretByEmail(request.email)
            ?: throw InvalidTotpCodeException(INVALID_TOTP_CODE_MESSAGE)

        if (!totpService.isCodeValid(secret, code)) {
            throw InvalidTotpCodeException(INVALID_TOTP_CODE_MESSAGE)
        }

        return LoginResult.Authenticated(establishSession(user, servletRequest, servletResponse))
    }

    fun enrollTotp(request: TotpEnrollRequest): TotpEnrollResponse {

        val user = verifyCredentials(request.email, request.password)

        if (user.totpEnabled) {
            throw TotpAlreadyEnabledException("TOTP is already enabled for this account")
        }

        val secret = totpService.generateSecret()
        userRepository.updateTotpSecret(user.id, secret)

        return TotpEnrollResponse(
            secretKey = secret,
            qrCodeDataUri = totpService.qrCodeDataUri(user.email, secret),
        )
    }

    fun verifyTotp(request: TotpVerifyRequest, servletRequest: HttpServletRequest, servletResponse: HttpServletResponse): SessionResponse {

        val user = verifyCredentials(request.email, request.password)

        val secret = userRepository.findTotpSecretByEmail(request.email)
            ?: throw InvalidTotpCodeException(INVALID_TOTP_CODE_MESSAGE)

        if (!totpService.isCodeValid(secret, request.code)) {
            throw InvalidTotpCodeException(INVALID_TOTP_CODE_MESSAGE)
        }

        userRepository.enableTotp(user.id, secret)

        return establishSession(user.copy(totpEnabled = true), servletRequest, servletResponse)
    }

    fun register(request: RegisterRequest) {
        userRepository.createLocalUser(request.name, request.email, passwordEncoder.encode(request.password)!!)
    }

    fun handleGoogleLogin(
        googleSub: String,
        email: String,
        name: String,
        avatarUrl: String?,
        servletRequest: HttpServletRequest,
        servletResponse: HttpServletResponse,
    ): SessionResponse {

        val user = userRepository.findByGoogleSub(googleSub) ?: run {
            if (userRepository.findByEmail(email) != null) {
                throw GoogleAccountEmailInUseException("An account with email $email already exists")
            }
            userRepository.createGoogleUser(googleSub, email, name, avatarUrl)
        }

        if (user.status == CurrentUser.Status.BLOCKED) {
            throw InvalidCredentialsException(BLOCKED_MESSAGE)
        }

        return establishSession(user, servletRequest, servletResponse)
    }

    fun currentSession(): SessionResponse {
        val user = SecurityContextHolder.getContext().authentication?.principal as? CurrentUser
            ?: throw InvalidCredentialsException("Not authenticated")
        return SessionResponse(name = user.name, email = user.email, role = user.role, status = user.status)
    }

    fun authConfig(): AuthConfigResponse = AuthConfigResponse(googleAuthEnabled = googleAuthEnabled)

    fun logout(servletRequest: HttpServletRequest) {
        servletRequest.getSession(false)?.invalidate()
        SecurityContextHolder.clearContext()
    }

    private fun verifyCredentials(email: String, password: String): CurrentUser {
        val user = userRepository.findByEmail(email)
            ?: throw InvalidCredentialsException(INVALID_CREDENTIALS_MESSAGE)

        val passwordHash = userRepository.findPasswordHashByEmail(email)
            ?: throw InvalidCredentialsException(INVALID_CREDENTIALS_MESSAGE)

        if (!passwordEncoder.matches(password, passwordHash)) {
            throw InvalidCredentialsException(INVALID_CREDENTIALS_MESSAGE)
        }

        return user
    }

    private fun establishSession(user: CurrentUser, servletRequest: HttpServletRequest, servletResponse: HttpServletResponse): SessionResponse {

        servletRequest.getSession(true)
        servletRequest.changeSessionId()

        val authorities = listOf(
            SimpleGrantedAuthority("ROLE_${user.role}"),
            SimpleGrantedAuthority("STATUS_${user.status}"),
        )
        val authentication = UsernamePasswordAuthenticationToken(user, null, authorities)

        val context = SecurityContextHolder.createEmptyContext()
        context.authentication = authentication
        SecurityContextHolder.setContext(context)

        HttpSessionSecurityContextRepository().saveContext(context, servletRequest, servletResponse)

        return SessionResponse(name = user.name, email = user.email, role = user.role, status = user.status)
    }

    companion object {
        private const val INVALID_TOTP_CODE_MESSAGE = "Invalid TOTP code"
        private const val INVALID_CREDENTIALS_MESSAGE = "Invalid email or password"
        private const val BLOCKED_MESSAGE = "Login failed. Contact an administrator."
    }
}
