package br.com.investlog.server.investmentreport.services

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import org.thymeleaf.TemplateEngine
import org.thymeleaf.context.Context
import java.io.ByteArrayOutputStream
import java.util.Locale

@Component
class InvestmentReportPdfRenderer(private val reportTemplateEngine: TemplateEngine) {

    fun render(report: InvestmentReport): ByteArray {
        val context = Context(Locale.forLanguageTag("pt-BR")).apply {
            setVariable("report", report)
            setVariable("formatter", InvestmentReportFormatter(report.displayCurrency))
        }

        val html = reportTemplateEngine.process(TEMPLATE_NAME, context)

        return ByteArrayOutputStream().use { output ->
            PdfRendererBuilder()
                .useFastMode()
                .apply { FONTS.forEach { (file, weight) -> useFont({ ClassPathResource("fonts/$file").inputStream }, FONT_FAMILY, weight, FontStyle.NORMAL, true) } }
                .withHtmlContent(html, null)
                .toStream(output)
                .run()
            output.toByteArray()
        }
    }

    companion object {
        private const val TEMPLATE_NAME = "investment-report"
        private const val FONT_FAMILY = "Inter"
        private val FONTS = listOf("Inter-Regular.ttf" to 400, "Inter-SemiBold.ttf" to 600, "Inter-Bold.ttf" to 700)
    }
}
