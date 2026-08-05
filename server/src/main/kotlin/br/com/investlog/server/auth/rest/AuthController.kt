package br.com.investlog.server.auth.rest

import br.com.investlog.server.auth.rest.payloads.AuthConfigResponse
import br.com.investlog.server.auth.rest.payloads.GoogleAccountLinkRequest
import br.com.investlog.server.auth.rest.payloads.LoginRequest
import br.com.investlog.server.auth.rest.payloads.RegisterRequest
import br.com.investlog.server.auth.rest.payloads.SessionResponse
import br.com.investlog.server.auth.rest.payloads.TotpEnrollRequest
import br.com.investlog.server.auth.rest.payloads.TotpEnrollResponse
import br.com.investlog.server.auth.rest.payloads.TotpEnrollmentRequiredResponse
import br.com.investlog.server.auth.rest.payloads.TotpVerifyRequest
import br.com.investlog.server.auth.services.AuthService
import br.com.investlog.server.auth.services.LoginResult
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/auth")
class AuthController(private val authService: AuthService) {

    @PostMapping("/login")
    fun login(
        @RequestBody request: LoginRequest,
        servletRequest: HttpServletRequest,
        servletResponse: HttpServletResponse,
    ): ResponseEntity<Any> =
        when (val result = authService.login(request, servletRequest, servletResponse)) {
            is LoginResult.Authenticated -> ResponseEntity.ok(result.session)
            is LoginResult.EnrollmentRequired -> ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(TotpEnrollmentRequiredResponse())
        }

    @PostMapping("/totp/enroll")
    fun enrollTotp(@RequestBody request: TotpEnrollRequest): ResponseEntity<TotpEnrollResponse> =
        ResponseEntity.ok(authService.enrollTotp(request))

    @PostMapping("/totp/verify")
    fun verifyTotp(
        @RequestBody request: TotpVerifyRequest,
        servletRequest: HttpServletRequest,
        servletResponse: HttpServletResponse,
    ): ResponseEntity<SessionResponse> =
        ResponseEntity.ok(authService.verifyTotp(request, servletRequest, servletResponse))

    @GetMapping("/session")
    fun session(): ResponseEntity<SessionResponse> = ResponseEntity.ok(authService.currentSession())

    @PostMapping("/logout")
    fun logout(servletRequest: HttpServletRequest): ResponseEntity<Void> {
        authService.logout(servletRequest)
        return ResponseEntity.ok().build()
    }

    @GetMapping("/config")
    fun config(): ResponseEntity<AuthConfigResponse> = ResponseEntity.ok(authService.authConfig())

    @PostMapping("/register")
    fun register(@Valid @RequestBody request: RegisterRequest): ResponseEntity<Void> {
        authService.register(request)
        return ResponseEntity.status(HttpStatus.CREATED).build()
    }

    @PostMapping("/google/link")
    fun linkGoogleAccount(
        @Valid @RequestBody request: GoogleAccountLinkRequest,
        servletRequest: HttpServletRequest,
        servletResponse: HttpServletResponse,
    ): ResponseEntity<SessionResponse> =
        ResponseEntity.ok(authService.linkGoogleAccount(request, servletRequest, servletResponse))
}
