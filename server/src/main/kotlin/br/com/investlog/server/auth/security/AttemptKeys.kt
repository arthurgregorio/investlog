package br.com.investlog.server.auth.security

object AttemptKeys {

    private const val SEPARATOR = "|"

    fun of(email: String, clientIp: String) = "$email$SEPARATOR$clientIp"

    fun prefixOf(email: String) = "$email$SEPARATOR"
}
