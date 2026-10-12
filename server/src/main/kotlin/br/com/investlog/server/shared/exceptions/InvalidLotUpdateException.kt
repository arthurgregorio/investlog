package br.com.investlog.server.shared.exceptions

class InvalidLotUpdateException(
    message: String = "A quantidade das compras não pode ficar abaixo da quantidade já vendida ou resgatada",
) : RuntimeException(message)
