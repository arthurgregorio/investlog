package br.com.investlog.server.typelists.rest.payloads

import java.util.UUID

data class HoldingSegmentRequest(
    val segmentId: UUID?,
)
