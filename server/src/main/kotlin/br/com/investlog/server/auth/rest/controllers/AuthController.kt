package br.com.investlog.server.auth.rest.controllers

import br.com.investlog.server.auth.domain.services.AuthService
import br.com.investlog.server.auth.rest.payloads.LoginRequest
import br.com.investlog.server.auth.rest.payloads.SessionResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
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
    ): SessionResponse =
        authService.login(request, servletRequest, servletResponse)
}
