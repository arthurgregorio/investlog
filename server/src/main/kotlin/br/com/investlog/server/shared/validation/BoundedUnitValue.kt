package br.com.investlog.server.shared.validation

import jakarta.validation.Constraint
import jakarta.validation.Payload
import jakarta.validation.ReportAsSingleViolation
import jakarta.validation.constraints.Digits
import kotlin.reflect.KClass

@Digits(integer = 15, fraction = 18)
@ReportAsSingleViolation
@Constraint(validatedBy = [])
@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class BoundedUnitValue(
    val message: String = "O valor está fora da faixa permitida",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)
