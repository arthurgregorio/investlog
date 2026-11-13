package br.com.investlog.server.shared.utils

private val UNSAFE_CHARACTERS = Regex("[\\p{Cc}\\p{Zl}\\p{Zp}]")

fun String.sanitizedForLog(): String =
    UNSAFE_CHARACTERS.replace(this) { match -> "\\u%04x".format(match.value.single().code) }
