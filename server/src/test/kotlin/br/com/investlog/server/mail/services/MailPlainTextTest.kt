package br.com.investlog.server.mail.services

import kotlin.test.Test
import kotlin.test.assertEquals

class MailPlainTextTest {

    @Test
    fun `drops the head, styles and tags and keeps one blank line between blocks`() {
        val html = """
            <!DOCTYPE html>
            <html>
            <head><title>Assunto</title><style>p { color: red; }</style></head>
            <body>
                <table><tr><td>Invest<span style="color: #087f5b;">Log</span></td></tr></table>


                <h1>Título</h1>
                <p>Primeira   linha<br/>segunda linha</p>
            </body>
            </html>
        """.trimIndent()

        assertEquals("InvestLog\n\nTítulo\n\nPrimeira linha\nsegunda linha", MailPlainText.from(html))
    }

    @Test
    fun `writes a labelled link as label and address, and a bare link once`() {
        val html = """<p><a href="https://investlog.example.com/login" style="color: #fff;"><span>Entrar</span> no InvestLog</a></p>""" +
            """<p><a href="https://investlog.example.com">https://investlog.example.com</a></p>""" +
            """<p><a href="https://investlog.example.com/vazio"></a></p>"""

        assertEquals(
            "Entrar no InvestLog: https://investlog.example.com/login\nhttps://investlog.example.com\nhttps://investlog.example.com/vazio",
            MailPlainText.from(html),
        )
    }

    @Test
    fun `decodes named and numeric entities`() {
        val html = "<p>A &amp; B &lt;tag&gt; &quot;aspas&quot; &#39;simples&#39; &apos;x&apos;&nbsp;fim &#233; &#xE7;&#8203;</p>"

        assertEquals("A & B <tag> \"aspas\" 'simples' 'x' fim é ç", MailPlainText.from(html))
    }

    @Test
    fun `does not decode an escaped entity twice`() {
        assertEquals("&lt;", MailPlainText.from("<p>&amp;lt;</p>"))
    }
}
