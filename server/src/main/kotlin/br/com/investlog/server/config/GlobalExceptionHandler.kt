package br.com.investlog.server.config

import br.com.investlog.server.shared.exceptions.NotFoundException
import java.time.Instant
import org.slf4j.LoggerFactory
import org.springframework.context.support.DefaultMessageSourceResolvable
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

@RestControllerAdvice
class GlobalExceptionHandler : ResponseEntityExceptionHandler() {

    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    override fun handleMethodArgumentNotValid(
        ex: MethodArgumentNotValidException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val errors = ex.allErrors.map(DefaultMessageSourceResolvable::getDefaultMessage)

        val problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.message ?: "Validation failed")
        problemDetail.title = "Validation Error"
        problemDetail.setProperty("errors", errors)
        problemDetail.setProperty("timestamp", Instant.now())

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problemDetail)
    }

    @ExceptionHandler(NotFoundException::class)
    fun handleNotFound(ex: NotFoundException): ProblemDetail {
        val problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.message ?: "Resource not found")
        problemDetail.setProperty("timestamp", Instant.now())

        return problemDetail
    }

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrityViolation(ex: DataIntegrityViolationException): ProblemDetail {
        val problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "The request conflicts with existing data")
        problemDetail.setProperty("timestamp", Instant.now())

        return problemDetail
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception): ProblemDetail {
        log.error("Unexpected exception occurred", ex)

        val problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred")
        problemDetail.setProperty("timestamp", Instant.now())

        return problemDetail
    }
}
