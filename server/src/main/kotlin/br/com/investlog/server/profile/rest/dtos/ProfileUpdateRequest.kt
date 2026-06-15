package br.com.investlog.server.profile.rest.dtos

import jakarta.validation.constraints.Pattern

data class ProfileUpdateRequest(
    @field:Pattern(regexp = "blue|indigo|teal|green", message = "accentColor must be one of: blue, indigo, teal, green")
    val accentColor: String? = null,

    @field:Pattern(regexp = "[A-Z]{3}", message = "preferredCurrency must be a 3-letter ISO currency code")
    val preferredCurrency: String? = null,
)
