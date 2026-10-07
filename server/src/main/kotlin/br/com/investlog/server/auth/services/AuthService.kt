package br.com.investlog.server.auth.services

import br.com.investlog.server.auth.rest.payloads.AuthConfigResponse
import br.com.investlog.server.auth.rest.payloads.GoogleAccountLinkRequest
import br.com.investlog.server.auth.rest.payloads.LoginRequest
import br.com.investlog.server.auth.rest.payloads.RegisterRequest
import br.com.investlog.server.auth.rest.payloads.SessionResponse
import br.com.investlog.server.auth.rest.payloads.TotpEnrollRequest
import br.com.investlog.server.auth.rest.payloads.TotpEnrollResponse
import br.com.investlog.server.auth.rest.payloads.TotpVerifyRequest
import br.com.investlog.server.auth.security.AttemptKeys
import br.com.investlog.server.auth.security.ClientIpResolver
import br.com.investlog.server.auth.security.GoogleLinkTokenStore
import br.com.investlog.server.auth.security.IpAttemptLimiter
import br.com.investlog.server.auth.security.LoginAttemptLimiter
import br.com.investlog.server.auth.security.TotpAttemptLimiter
import br.com.investlog.server.shared.exceptions.GoogleAccountEmailInUseException
import br.com.investlog.server.shared.exceptions.InvalidCredentialsException
import br.com.investlog.server.shared.exceptions.InvalidTotpCodeException
import br.com.investlog.server.shared.exceptions.TotpAlreadyEnabledException
import br.com.investlog.server.shared.exceptions.TotpRequiredException
import br.com.investlog.server.shared.security.AuthProvider
import br.com.investlog.server.shared.security.CurrentUser
import br.com.investlog.server.shared.security.UserRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

@Service
@Transactional(readOnly = true)
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val totpService: TotpService,
    private val googleLinkTokenStore: GoogleLinkTokenStore,
    private val totpAttemptLimiter: TotpAttemptLimiter,
    private val loginAttemptLimiter: LoginAttemptLimiter,
    private val ipAttemptLimiter: IpAttemptLimiter,
    private val clientIpResolver: ClientIpResolver,
    private val trustedDeviceService: TrustedDeviceService,
    @Value($$"${investlog.google-auth.enabled:false}")
    private val googleAuthEnabled: Boolean,
    @Value($$"${investlog.security.totp.enabled:true}")
    private val totpRequired: Boolean,
    @Value($$"${investlog.demo-mode.enabled:false}")
    private val demoModeEnabled: Boolean,
) {

    @Transactional
    fun login(request: LoginRequest, servletRequest: HttpServletRequest, servletResponse: HttpServletResponse): LoginResult {

        val clientIp = clientIpResolver.resolve(servletRequest)
        val user = verifyCredentials(request.email, request.password, clientIp)

        if (user.status == CurrentUser.Status.BLOCKED) {
            throw InvalidCredentialsException(BLOCKED_MESSAGE)
        }

        if (!totpRequired) {
            return LoginResult.Authenticated(establishSession(user, servletRequest, servletResponse))
        }

        if (!user.totpEnabled) {
            return LoginResult.EnrollmentRequired
        }

        if (trustedDeviceService.isTrusted(user.id, servletRequest)) {
            return LoginResult.Authenticated(establishSession(user, servletRequest, servletResponse))
        }

        val code = request.totpCode
            ?: throw TotpRequiredException("Um código TOTP é necessário para concluir o login")

        val attemptKey = AttemptKeys.of(request.email, clientIp)
        totpAttemptLimiter.checkNotLocked(attemptKey)

        val secret = userRepository.findTotpSecretByEmail(request.email)
            ?: throw InvalidTotpCodeException(INVALID_TOTP_CODE_MESSAGE)

        if (!totpService.isCodeValid(secret, code)) {
            totpAttemptLimiter.recordFailure(attemptKey)
            ipAttemptLimiter.recordFailure(clientIp)
            throw InvalidTotpCodeException(INVALID_TOTP_CODE_MESSAGE)
        }

        totpAttemptLimiter.recordSuccess(attemptKey)

        if (request.trustDevice) {
            trustedDeviceService.trust(user.id, servletRequest, servletResponse)
        }

        return LoginResult.Authenticated(establishSession(user, servletRequest, servletResponse))
    }

    @Transactional
    fun enrollTotp(request: TotpEnrollRequest, servletRequest: HttpServletRequest): TotpEnrollResponse {

        val user = verifyCredentials(request.email, request.password, clientIpResolver.resolve(servletRequest))

        if (user.totpEnabled) {
            throw TotpAlreadyEnabledException("O TOTP já está habilitado para esta conta")
        }

        val secret = totpService.generateSecret()
        userRepository.updateTotpSecret(user.id, secret)

        return TotpEnrollResponse(
            secretKey = secret,
            qrCodeDataUri = totpService.qrCodeDataUri(user.email, secret),
        )
    }

    @Transactional
    fun verifyTotp(request: TotpVerifyRequest, servletRequest: HttpServletRequest, servletResponse: HttpServletResponse): SessionResponse {

        val clientIp = clientIpResolver.resolve(servletRequest)
        val user = verifyCredentials(request.email, request.password, clientIp)

        val attemptKey = AttemptKeys.of(request.email, clientIp)
        totpAttemptLimiter.checkNotLocked(attemptKey)

        val secret = userRepository.findTotpSecretByEmail(request.email)
            ?: throw InvalidTotpCodeException(INVALID_TOTP_CODE_MESSAGE)

        if (!totpService.isCodeValid(secret, request.code)) {
            totpAttemptLimiter.recordFailure(attemptKey)
            ipAttemptLimiter.recordFailure(clientIp)
            throw InvalidTotpCodeException(INVALID_TOTP_CODE_MESSAGE)
        }

        totpAttemptLimiter.recordSuccess(attemptKey)

        userRepository.enableTotp(user.id, secret)

        return establishSession(user.copy(totpEnabled = true), servletRequest, servletResponse)
    }

    @Transactional
    fun register(request: RegisterRequest) {
        try {
            userRepository.createLocalUser(request.name, request.email, passwordEncoder.encode(request.password)!!)
        } catch (exception: DataIntegrityViolationException) {
            logger.debug(exception) { "Registration attempted for an email that is already registered" }
        }
    }

    @Transactional
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
                throw GoogleAccountEmailInUseException("Já existe uma conta com o e-mail $email")
            }
            userRepository.createGoogleUser(googleSub, email, name, avatarUrl)
        }

        if (user.status == CurrentUser.Status.BLOCKED) {
            throw InvalidCredentialsException(BLOCKED_MESSAGE)
        }

        return establishSession(user, servletRequest, servletResponse)
    }

    @Transactional
    fun linkGoogleAccount(
        request: GoogleAccountLinkRequest,
        servletRequest: HttpServletRequest,
        servletResponse: HttpServletResponse,
    ): SessionResponse {

        val pending = googleLinkTokenStore.consume(request.linkToken)
            ?: throw InvalidCredentialsException("Solicitação de vínculo expirada ou inválida — entre novamente com o Google")

        val user = verifyCredentials(pending.email, request.password, clientIpResolver.resolve(servletRequest))

        if (user.status == CurrentUser.Status.BLOCKED) {
            throw InvalidCredentialsException(BLOCKED_MESSAGE)
        }

        userRepository.linkGoogleAccount(user.id, pending.googleSub)

        return establishSession(user.copy(authProvider = AuthProvider.GOOGLE), servletRequest, servletResponse)
    }

    fun currentSession(): SessionResponse {
        val user = SecurityContextHolder.getContext().authentication?.principal as? CurrentUser
            ?: throw InvalidCredentialsException("Não autenticado")
        return SessionResponse(
            name = user.name,
            email = user.email,
            role = user.role,
            status = user.status,
            authProvider = user.authProvider,
            demoModeEnabled = demoModeEnabled,
        )
    }

    fun authConfig(): AuthConfigResponse = AuthConfigResponse(googleAuthEnabled = googleAuthEnabled)

    fun logout(servletRequest: HttpServletRequest) {
        servletRequest.getSession(false)?.invalidate()
        SecurityContextHolder.clearContext()
    }

    private fun verifyCredentials(email: String, password: String, clientIp: String): CurrentUser {

        val attemptKey = AttemptKeys.of(email, clientIp)

        ipAttemptLimiter.checkNotLocked(clientIp)
        loginAttemptLimiter.checkNotLocked(attemptKey)

        val user = userRepository.findByEmail(email)
        val passwordHash = userRepository.findPasswordHashByEmail(email)

        if (user == null || passwordHash == null) {
            ipAttemptLimiter.recordFailure(clientIp)
            throw InvalidCredentialsException(INVALID_CREDENTIALS_MESSAGE)
        }

        if (!passwordEncoder.matches(password, passwordHash)) {
            loginAttemptLimiter.recordFailure(attemptKey)
            ipAttemptLimiter.recordFailure(clientIp)
            throw InvalidCredentialsException(INVALID_CREDENTIALS_MESSAGE)
        }

        loginAttemptLimiter.recordSuccess(attemptKey)

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

        return SessionResponse(
            name = user.name,
            email = user.email,
            role = user.role,
            status = user.status,
            authProvider = user.authProvider,
            demoModeEnabled = demoModeEnabled,
        )
    }

    companion object {
        private const val INVALID_TOTP_CODE_MESSAGE = "Código TOTP inválido"
        private const val INVALID_CREDENTIALS_MESSAGE = "E-mail ou senha inválidos"
        private const val BLOCKED_MESSAGE = "Login falhou. Entre em contato com um administrador."
    }
}
