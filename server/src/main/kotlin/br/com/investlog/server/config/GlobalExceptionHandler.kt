package br.com.investlog.server.config

import br.com.investlog.server.shared.exceptions.InvalidCredentialsException
import br.com.investlog.server.shared.exceptions.InvalidTotpCodeException
import br.com.investlog.server.shared.exceptions.NotFoundException
import br.com.investlog.server.shared.exceptions.TotpAlreadyEnabledException
import br.com.investlog.server.shared.exceptions.TotpRequiredException
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.validation.ConstraintViolationException
import org.springframework.context.support.DefaultMessageSourceResolvable
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.CONFLICT
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.HttpStatus.NOT_FOUND
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

private val log = KotlinLogging.logger {}

@RestControllerAdvice
class GlobalExceptionHandler : ResponseEntityExceptionHandler() {

    override fun handleMethodArgumentNotValid(
        ex: MethodArgumentNotValidException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {

        log.debug(ex) { "Data validation failed" }

        val errors = ex.allErrors.map(DefaultMessageSourceResolvable::getDefaultMessage)

        val problemDetail = ProblemDetail.forStatusAndDetail(BAD_REQUEST, ex.message)
        problemDetail.title = "Validation Error"
        problemDetail.setProperty("errors", errors)
        problemDetail.setProperty("timestamp", Instant.now())

        return ResponseEntity.status(BAD_REQUEST).body(problemDetail)
    }

    @ExceptionHandler(NotFoundException::class)
    fun handleNotFound(ex: NotFoundException): ProblemDetail {

        val problemDetail = ProblemDetail.forStatusAndDetail(NOT_FOUND, ex.message ?: "Resource not found")
        problemDetail.setProperty("timestamp", Instant.now())

        return problemDetail
    }

    @ExceptionHandler(InvalidCredentialsException::class)
    fun handleInvalidCredentials(ex: InvalidCredentialsException): ProblemDetail {

        val problemDetail = ProblemDetail.forStatusAndDetail(UNAUTHORIZED, ex.message ?: "Invalid credentials")
        problemDetail.setProperty("timestamp", Instant.now())

        return problemDetail
    }

    @ExceptionHandler(TotpRequiredException::class)
    fun handleTotpRequired(ex: TotpRequiredException): ProblemDetail {

        val problemDetail = ProblemDetail.forStatusAndDetail(UNAUTHORIZED, ex.message ?: "TOTP code required")
        problemDetail.setProperty("error", "totp_required")
        problemDetail.setProperty("timestamp", Instant.now())

        return problemDetail
    }

    @ExceptionHandler(InvalidTotpCodeException::class)
    fun handleInvalidTotpCode(ex: InvalidTotpCodeException): ProblemDetail {

        val problemDetail = ProblemDetail.forStatusAndDetail(UNAUTHORIZED, ex.message ?: "Invalid TOTP code")
        problemDetail.setProperty("error", "invalid_totp_code")
        problemDetail.setProperty("timestamp", Instant.now())

        return problemDetail
    }

    @ExceptionHandler(TotpAlreadyEnabledException::class)
    fun handleTotpAlreadyEnabled(ex: TotpAlreadyEnabledException): ProblemDetail {

        val problemDetail = ProblemDetail.forStatusAndDetail(CONFLICT, ex.message ?: "TOTP is already enabled")
        problemDetail.setProperty("timestamp", Instant.now())

        return problemDetail
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(ex: ConstraintViolationException): ProblemDetail {

        log.error(ex) { "Constraint violation occurred" }

        val errors = ex.constraintViolations.map { it.message }
        val problemDetail = ProblemDetail.forStatusAndDetail(BAD_REQUEST, "Validation failed")

        problemDetail.title = "Validation Error"
        problemDetail.setProperty("errors", errors)
        problemDetail.setProperty("timestamp", Instant.now())

        return problemDetail
    }

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrityViolation(ex: DataIntegrityViolationException): ProblemDetail {

        log.error(ex) { "Data integrity violation occurred" }

        val problemDetail = ProblemDetail.forStatusAndDetail(CONFLICT, "The request conflicts with existing data")
        problemDetail.setProperty("timestamp", Instant.now())

        return problemDetail
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception): ProblemDetail {

        log.error(ex) { "Unexpected exception occurred" }

        val problemDetail = ProblemDetail.forStatusAndDetail(INTERNAL_SERVER_ERROR, "An unexpected error occurred")
        problemDetail.setProperty("timestamp", Instant.now())

        return problemDetail
    }
}
