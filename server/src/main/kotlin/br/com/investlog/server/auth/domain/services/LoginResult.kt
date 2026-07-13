package br.com.investlog.server.auth.domain.services

import br.com.investlog.server.auth.rest.payloads.SessionResponse

sealed interface LoginResult {
    data class Authenticated(val session: SessionResponse) : LoginResult
    data object EnrollmentRequired : LoginResult
}
