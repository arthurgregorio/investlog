package br.com.investlog.server.shared.security

import org.springframework.security.access.prepost.PreAuthorize

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@PreAuthorize("hasAuthority('ROLE_ADMIN') and @activeAdministratorGuard.isActiveAdministrator()")
annotation class RequiresActiveAdministrator
