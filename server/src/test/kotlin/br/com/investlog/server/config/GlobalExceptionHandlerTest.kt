package br.com.investlog.server.config

import br.com.investlog.server.shared.exceptions.NotFoundException
import kotlin.test.Test
import kotlin.test.assertEquals
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus

class GlobalExceptionHandlerTest {

    private val handler = GlobalExceptionHandler()

    @Test
    fun `maps NotFoundException to a 404 ProblemDetail`() {
        val problemDetail = handler.handleNotFound(NotFoundException("Stock type abc not found"))

        assertEquals(HttpStatus.NOT_FOUND.value(), problemDetail.status)
        assertEquals("Stock type abc not found", problemDetail.detail)
    }

    @Test
    fun `maps DataIntegrityViolationException to a 409 ProblemDetail`() {
        val problemDetail = handler.handleDataIntegrityViolation(DataIntegrityViolationException("duplicate key value"))

        assertEquals(HttpStatus.CONFLICT.value(), problemDetail.status)
    }
}
