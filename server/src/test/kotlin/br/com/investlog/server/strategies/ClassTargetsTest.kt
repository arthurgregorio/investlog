package br.com.investlog.server.strategies

import br.com.investlog.server.shared.exceptions.InvalidStrategyException
import br.com.investlog.server.strategies.services.ClassTargets
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClassTargetsTest {

    private fun percentages(vararg values: String?): List<BigDecimal?> = values.map { value -> value?.let(::BigDecimal) }

    @Test
    fun `blank holdings split equally what the typed targets leave`() {
        val targets = percentages("15", "15", "15", null, null, null, null, null, null, null)

        assertEquals(BigDecimal("7.8571"), ClassTargets.blankShare(targets))
        assertEquals(BigDecimal("45"), ClassTargets.typedSum(targets))
        assertEquals(
            listOf("15", "15", "15", "7.8571", "7.8571", "7.8571", "7.8571", "7.8571", "7.8571", "7.8571").map(::BigDecimal),
            ClassTargets.effective(targets),
        )
    }

    @Test
    fun `nothing typed splits the class equally`() {
        assertEquals(listOf("33.3333", "33.3333", "33.3333").map(::BigDecimal), ClassTargets.effective(percentages(null, null, null)))
    }

    @Test
    fun `a fully typed class has no blank share`() {
        assertNull(ClassTargets.blankShare(percentages("60", "40")))
        assertEquals(listOf(BigDecimal("60"), BigDecimal("40")), ClassTargets.effective(percentages("60", "40")))
    }

    @Test
    fun `blanks get zero when the typed targets already reach 100`() {
        assertEquals(BigDecimal("0.0000"), ClassTargets.blankShare(percentages("60", "40", null)))
    }

    @Test
    fun `accepts typed targets under 100 with blanks and a fully typed class adding up to 100`() {
        ClassTargets.validate(percentages("30", null))
        ClassTargets.validate(percentages("30", "70"))
        ClassTargets.validate(percentages(null, null))
        ClassTargets.validate(emptyList())
    }

    @Test
    fun `rejects typed targets above 100`() {
        val error = assertFailsWith<InvalidStrategyException> { ClassTargets.validate(percentages("60", "60", null)) }
        assertTrue(error.message!!.contains("120%"))
    }

    @Test
    fun `rejects a fully typed class that does not add up to 100`() {
        val error = assertFailsWith<InvalidStrategyException> { ClassTargets.validate(percentages("30", "30")) }
        assertTrue(error.message!!.contains("60%"))
    }

    @Test
    fun `class targets must add up to exactly 100`() {
        ClassTargets.validateClassTargets(listOf(BigDecimal("55"), BigDecimal("45")))
        val error = assertFailsWith<InvalidStrategyException> {
            ClassTargets.validateClassTargets(listOf(BigDecimal("55"), BigDecimal("40")))
        }
        assertTrue(error.message!!.contains("95%"))
    }
}
