package br.com.investlog.server.config.core

import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.deser.jdk.NumberDeserializers
import java.math.BigDecimal
import kotlin.math.abs

class BoundedBigDecimalDeserializer : NumberDeserializers.BigDecimalDeserializer() {

    override fun deserialize(parser: JsonParser, context: DeserializationContext): BigDecimal? {
        val value = super.deserialize(parser, context) ?: return null
        if (value.precision() > MAXIMUM_PRECISION || abs(value.scale()) > MAXIMUM_SCALE) {
            return context.reportInputMismatch(this, "Número fora da faixa permitida")
        }
        return value
    }

    companion object {
        const val MAXIMUM_PRECISION = 64
        const val MAXIMUM_SCALE = 64
    }
}
