package br.com.investlog.server.investmentreport.rest

import br.com.investlog.server.investmentreport.services.InvestmentReportFilters
import br.com.investlog.server.investmentreport.services.InvestmentReportPdfRenderer
import br.com.investlog.server.investmentreport.services.InvestmentReportService
import br.com.investlog.server.shared.security.CurrentUserProvider
import br.com.investlog.server.wallets.rest.payloads.WalletKind
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.context.request.WebRequest
import java.util.UUID

@RestController
@RequestMapping("/holdings/report/pdf")
class InvestmentReportController(
    private val currentUserProvider: CurrentUserProvider,
    private val investmentReportService: InvestmentReportService,
    private val investmentReportPdfRenderer: InvestmentReportPdfRenderer,
) {

    @GetMapping
    fun export(
        @RequestParam(required = false) kind: WalletKind?,
        webRequest: WebRequest,
        @RequestParam(required = false) walletId: List<UUID>?,
        @RequestParam(required = false) search: String?,
        @RequestParam(required = false, defaultValue = "false") hideFrozen: Boolean,
    ): ResponseEntity<ByteArray> {

        val user = currentUserProvider.getCurrentUser()
        val filters = InvestmentReportFilters(kind, webRequest.getParameterValues("typeLabel")?.toList(), walletId, search, hideFrozen)

        val report = investmentReportService.build(user, filters) ?: return ResponseEntity.noContent().build()

        val contentDisposition = ContentDisposition.attachment()
            .filename("relatorio-investimentos-${report.generatedAt.toLocalDate()}.pdf")
            .build()

        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
            .body(investmentReportPdfRenderer.render(report))
    }
}
