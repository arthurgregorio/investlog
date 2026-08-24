package br.com.investlog.server.config

import br.com.investlog.server.shared.exceptions.AccountNotLocalException
import br.com.investlog.server.shared.exceptions.DemoModeProtectedAccountException
import br.com.investlog.server.shared.exceptions.InvalidCredentialsException
import br.com.investlog.server.shared.exceptions.InvalidTotpCodeException
import br.com.investlog.server.shared.exceptions.InvalidUserStatusTransitionException
import br.com.investlog.server.shared.exceptions.NotFoundException
import br.com.investlog.server.shared.exceptions.SelfActionNotAllowedException
import br.com.investlog.server.shared.exceptions.TooManyLoginAttemptsException
import br.com.investlog.server.shared.exceptions.TooManyTotpAttemptsException
import br.com.investlog.server.shared.exceptions.TotpAlreadyEnabledException
import br.com.investlog.server.shared.exceptions.TotpRequiredException
import br.com.investlog.server.shared.exceptions.UserNotApprovedException
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.validation.ConstraintViolationException
import org.springframework.context.support.DefaultMessageSourceResolvable
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.CONFLICT
import org.springframework.http.HttpStatus.FORBIDDEN
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.http.HttpStatus.TOO_MANY_REQUESTS
import org.springframework.http.HttpStatus.UNAUTHORIZED
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import java.time.Instant

private val logger = KotlinLogging.logger {}

@RestControllerAdvice
class GlobalExceptionHandler : ResponseEntityExceptionHandler() {

    override fun handleMethodArgumentNotValid(
        ex: MethodArgumentNotValidException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {

        br.com.investlog.server.config.logger.debug(ex) { "Data validation failed" }

        val errors = ex.allErrors.map(DefaultMessageSourceResolvable::getDefaultMessage)

        val problemDetail = ProblemDetail.forStatusAndDetail(BAD_REQUEST, "Falha na validação dos dados enviados")
        problemDetail.title = "Erro de validação"
        problemDetail.setProperty("errors", errors)
        problemDetail.setProperty("timestamp", Instant.now())

        return ResponseEntity.status(BAD_REQUEST).body(problemDetail)
    }

    @ExceptionHandler(NotFoundException::class)
    fun handleNotFound(ex: NotFoundException): ProblemDetail {

        val message = ex.message ?: "Recurso não encontrado"

        val problemDetail = ProblemDetail.forStatusAndDetail(NOT_FOUND, message)
        problemDetail.setProperty("timestamp", Instant.now())

        return problemDetail
    }

    @ExceptionHandler(InvalidCredentialsException::class)
    fun handleInvalidCredentials(ex: InvalidCredentialsException): ProblemDetail {

        val message = ex.message ?: "Credenciais inválidas"

        val problemDetail = ProblemDetail.forStatusAndDetail(UNAUTHORIZED, message)
        problemDetail.setProperty("error", "invalid_credentials")
        problemDetail.setProperty("timestamp", Instant.now())

        return problemDetail
    }

    @ExceptionHandler(SelfActionNotAllowedException::class)
    fun handleSelfActionNotAllowed(ex: SelfActionNotAllowedException): ProblemDetail {

        val message = ex.message ?: "Esta ação não pode ser aplicada à sua própria conta"

        val problemDetail = ProblemDetail.forStatusAndDetail(BAD_REQUEST, message)
        problemDetail.setProperty("timestamp", Instant.now())

        return problemDetail
    }

    @ExceptionHandler(UserNotApprovedException::class)
    fun handleUserNotApproved(ex: UserNotApprovedException): ProblemDetail {

        val message = ex.message ?: "Sua conta não está aprovada"

        val problemDetail = ProblemDetail.forStatusAndDetail(FORBIDDEN, message)
        problemDetail.setProperty("error", "pending_approval")
        problemDetail.setProperty("timestamp", Instant.now())

        return problemDetail
    }

    @ExceptionHandler(TotpRequiredException::class)
    fun handleTotpRequired(ex: TotpRequiredException): ProblemDetail {

        val message = ex.message ?: "Código TOTP obrigatório"

        val problemDetail = ProblemDetail.forStatusAndDetail(UNAUTHORIZED, message)
        problemDetail.setProperty("error", "totp_required")
        problemDetail.setProperty("timestamp", Instant.now())

        return problemDetail
    }

    @ExceptionHandler(InvalidTotpCodeException::class)
    fun handleInvalidTotpCode(ex: InvalidTotpCodeException): ProblemDetail {

        val message = ex.message ?: "Código TOTP inválido"

        val problemDetail = ProblemDetail.forStatusAndDetail(UNAUTHORIZED, message)
        problemDetail.setProperty("error", "invalid_totp_code")
        problemDetail.setProperty("timestamp", Instant.now())

        return problemDetail
    }

    @ExceptionHandler(TooManyTotpAttemptsException::class)
    fun handleTooManyTotpAttempts(ex: TooManyTotpAttemptsException): ProblemDetail {

        val message = ex.message ?: "Muitas tentativas de código TOTP inválidas"

        val problemDetail = ProblemDetail.forStatusAndDetail(TOO_MANY_REQUESTS, message)
        problemDetail.setProperty("error", "too_many_totp_attempts")
        problemDetail.setProperty("timestamp", Instant.now())

        return problemDetail
    }

    @ExceptionHandler(TooManyLoginAttemptsException::class)
    fun handleTooManyLoginAttempts(ex: TooManyLoginAttemptsException): ProblemDetail {

        val message = ex.message ?: "Muitas tentativas de login inválidas"

        val problemDetail = ProblemDetail.forStatusAndDetail(TOO_MANY_REQUESTS, message)
        problemDetail.setProperty("error", "too_many_login_attempts")
        problemDetail.setProperty("timestamp", Instant.now())

        return problemDetail
    }

    @ExceptionHandler(TotpAlreadyEnabledException::class)
    fun handleTotpAlreadyEnabled(ex: TotpAlreadyEnabledException): ProblemDetail {

        val message = ex.message ?: "O TOTP já está habilitado"

        val problemDetail = ProblemDetail.forStatusAndDetail(CONFLICT, message)
        problemDetail.setProperty("timestamp", Instant.now())

        return problemDetail
    }

    @ExceptionHandler(InvalidUserStatusTransitionException::class)
    fun handleInvalidUserStatusTransition(ex: InvalidUserStatusTransitionException): ProblemDetail {

        val message = ex.message ?: "Transição de status de usuário inválida"

        val problemDetail = ProblemDetail.forStatusAndDetail(CONFLICT, message)
        problemDetail.setProperty("timestamp", Instant.now())

        return problemDetail
    }

    @ExceptionHandler(DemoModeProtectedAccountException::class)
    fun handleDemoModeProtectedAccount(ex: DemoModeProtectedAccountException): ProblemDetail {

        val message = ex.message ?: "Esta ação está desabilitada para a conta protegida do administrador de demonstração."

        val problemDetail = ProblemDetail.forStatusAndDetail(FORBIDDEN, message)
        problemDetail.setProperty("timestamp", Instant.now())

        return problemDetail
    }

    @ExceptionHandler(AccountNotLocalException::class)
    fun handleAccountNotLocal(ex: AccountNotLocalException): ProblemDetail {

        val problemDetail = ProblemDetail.forStatusAndDetail(
            BAD_REQUEST,
            ex.message ?: "Esta ação está disponível apenas para contas locais"
        )
        problemDetail.setProperty("timestamp", Instant.now())

        return problemDetail
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(ex: ConstraintViolationException): ProblemDetail {

        br.com.investlog.server.config.logger.error(ex) { "Constraint violation occurred" }

        val errors = ex.constraintViolations.map { it.message }
        val problemDetail = ProblemDetail.forStatusAndDetail(BAD_REQUEST, "Falha na validação")

        problemDetail.title = "Erro de validação"
        problemDetail.setProperty("errors", errors)
        problemDetail.setProperty("timestamp", Instant.now())

        return problemDetail
    }

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrityViolation(ex: DataIntegrityViolationException): ProblemDetail {

        br.com.investlog.server.config.logger.error(ex) { "Data integrity violation occurred" }

        val problemDetail = ProblemDetail.forStatusAndDetail(CONFLICT, "A solicitação conflita com dados existentes")
        problemDetail.setProperty("timestamp", Instant.now())

        return problemDetail
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception): ProblemDetail {

        br.com.investlog.server.config.logger.error(ex) { "Unexpected exception occurred" }

        val problemDetail = ProblemDetail.forStatusAndDetail(INTERNAL_SERVER_ERROR, "Ocorreu um erro inesperado")
        problemDetail.setProperty("timestamp", Instant.now())

        return problemDetail
    }
}
