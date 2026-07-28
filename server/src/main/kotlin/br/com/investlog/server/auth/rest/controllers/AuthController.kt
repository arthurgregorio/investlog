package br.com.investlog.server.auth.rest.controllers

import br.com.investlog.server.auth.domain.services.AuthService
import br.com.investlog.server.auth.domain.services.LoginResult
import br.com.investlog.server.auth.rest.payloads.AuthConfigResponse
import br.com.investlog.server.auth.rest.payloads.GoogleAccountLinkRequest
import br.com.investlog.server.auth.rest.payloads.LoginRequest
import br.com.investlog.server.auth.rest.payloads.RegisterRequest
import br.com.investlog.server.auth.rest.payloads.SessionResponse
import br.com.investlog.server.auth.rest.payloads.TotpEnrollRequest
import br.com.investlog.server.auth.rest.payloads.TotpEnrollResponse
import br.com.investlog.server.auth.rest.payloads.TotpEnrollmentRequiredResponse
import br.com.investlog.server.auth.rest.payloads.TotpVerifyRequest
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
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
            is LoginResult.EnrollmentRequired -> ResponseEntity.status(HttpStatus.ACCEPTED).body(TotpEnrollmentRequiredResponse())
        }

    @PostMapping("/totp/enroll")
    fun enrollTotp(@RequestBody request: TotpEnrollRequest): TotpEnrollResponse =
        authService.enrollTotp(request)

    @PostMapping("/totp/verify")
    fun verifyTotp(
        @RequestBody request: TotpVerifyRequest,
        servletRequest: HttpServletRequest,
        servletResponse: HttpServletResponse,
    ): SessionResponse =
        authService.verifyTotp(request, servletRequest, servletResponse)

    @GetMapping("/session")
    fun session(): SessionResponse = authService.currentSession()

    @PostMapping("/logout")
    fun logout(servletRequest: HttpServletRequest) = authService.logout(servletRequest)

    @GetMapping("/config")
    fun config(): AuthConfigResponse = authService.authConfig()

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    fun register(@Valid @RequestBody request: RegisterRequest) {
        authService.register(request)
    }

    @PostMapping("/google/link")
    fun linkGoogleAccount(
        @Valid @RequestBody request: GoogleAccountLinkRequest,
        servletRequest: HttpServletRequest,
        servletResponse: HttpServletResponse,
    ): SessionResponse =
        authService.linkGoogleAccount(request, servletRequest, servletResponse)
}
