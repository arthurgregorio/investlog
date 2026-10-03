package br.com.investlog.server.config

import br.com.investlog.server.shared.exceptions.NotFoundException
import jakarta.validation.ConstraintViolationException
import jakarta.validation.Validation
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import java.time.Instant

class GlobalExceptionHandlerTest {

    private val handler = GlobalExceptionHandler()

    private data class Subject(
        @field:NotBlank(message = "o nome é obrigatório")
        val name: String,
        @field:Size(max = 3, message = "o código é longo demais")
        val code: String,
    )

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

    @Test
    fun `maps ConstraintViolationException to a 400 ProblemDetail listing every violated message`() {
        val violations = Validation.buildDefaultValidatorFactory().validator.validate(Subject(name = "", code = "toolong"))

        val problemDetail = handler.handleConstraintViolation(ConstraintViolationException(violations))

        assertEquals(HttpStatus.BAD_REQUEST.value(), problemDetail.status)
        assertEquals("Erro de validação", problemDetail.title)
        assertEquals("Falha na validação", problemDetail.detail)
        val errors = problemDetail.properties?.get("errors")
        assertIs<List<*>>(errors)
        assertEquals(setOf("o nome é obrigatório", "o código é longo demais"), errors.toSet())
        assertIs<Instant>(problemDetail.properties?.get("timestamp"))
    }

    @Test
    fun `maps an unexpected exception to a 500 ProblemDetail that hides the cause`() {
        val problemDetail = handler.handleUnexpected(IllegalStateException("internal detail that must not leak"))

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), problemDetail.status)
        assertEquals("Ocorreu um erro inesperado", problemDetail.detail)
        assertNotNull(problemDetail.properties?.get("timestamp"))
    }
}
