package br.com.investlog.server.shared.utils

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

fun LocalDateTime.humanReadable(): String = this.format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"))
