package br.sidia.painel.jira

import kotlin.test.Test
import kotlin.test.assertEquals

/** Testes da função pura que varre fontes; usa só texto em memória (nada de rede nem de disco). */
class VarreduraDeFontesTest {
    private val permitidos = setOf("JiraClientFactory.kt", "JiraClient.kt")

    private fun varrer(vararg fontes: Pair<String, String>) = varrerFontes(mapOf(*fontes), permitidos)

    private fun regras(vararg fontes: Pair<String, String>) = varrer(*fontes).map { it.regra }

    @Test
    fun construcaoDireta() {
        val v = varrer("Rota.kt" to "package x\n\nval c = JiraClient(cfg, http, guard)\n")
        assertEquals(listOf(Violacao("Rota.kt", 3, "JIRA_CLIENT_CONSTRUIDO")), v)
    }

    @Test
    fun construcaoComEspacoAntesDoParentese() {
        assertEquals(listOf("JIRA_CLIENT_CONSTRUIDO"), regras("Rota.kt" to "val c = JiraClient (cfg)\n"))
    }

    @Test
    fun construcaoQualificada() {
        assertEquals(
            listOf("JIRA_CLIENT_CONSTRUIDO"),
            regras("Rota.kt" to "val c = br.sidia.painel.jira.JiraClient(cfg, http, guard)\n"),
        )
    }

    @Test
    fun nomesParecidosNaoContam() {
        // Limite de palavra: nem a função de apoio nem a classe falsa nem a fábrica são o cliente.
        val fonte = "val a = createJiraClient(x)\nval b = FakeJiraClient(x)\nval c = JiraClientFactory(s)\n"
        assertEquals(emptyList(), regras("Rota.kt" to fonte))
    }

    @Test
    fun referenciaDeConstrutor() {
        val v = varrer("Rota.kt" to "val fabrica = ::JiraClient\n")
        assertEquals(listOf(Violacao("Rota.kt", 1, "JIRA_CLIENT_REFERENCIA")), v)
    }

    @Test
    fun importComAlias() {
        val fonte = "import br.sidia.painel.jira.JiraClient as Cliente\n\nval c = Cliente(cfg, http, guard)\n"
        val v = varrer("Rota.kt" to fonte)
        assertEquals(
            listOf(Violacao("Rota.kt", 1, "JIRA_CLIENT_ALIAS"), Violacao("Rota.kt", 3, "JIRA_CLIENT_ALIAS")),
            v,
        )
    }

    @Test
    fun typealiasDepoisUsado() {
        // O alias é declarado num arquivo e usado em outro: a regra vale para o projeto inteiro.
        val v = varrer(
            "Alias.kt" to "typealias Cliente = br.sidia.painel.jira.JiraClient\n",
            "Rota.kt" to "fun f() = Cliente(cfg, http, guard)\n",
        )
        assertEquals(
            listOf(Violacao("Alias.kt", 1, "JIRA_CLIENT_ALIAS"), Violacao("Rota.kt", 1, "JIRA_CLIENT_ALIAS")),
            v.sortedBy { it.arquivo },
        )
    }

    @Test
    fun aliasDeAlias() {
        val v = varrer(
            "A.kt" to "typealias Um = JiraClient\n",
            "B.kt" to "typealias Dois = Um\n",
            "C.kt" to "val c = Dois(cfg)\n",
        )
        assertEquals(listOf("C.kt"), v.filter { it.linha == 1 && it.arquivo == "C.kt" }.map { it.arquivo })
    }

    @Test
    fun httpClientForaDaFabrica() {
        val v = varrer("Rota.kt" to "val h = HttpClient(CIO) { }\n")
        assertEquals(listOf(Violacao("Rota.kt", 1, "HTTP_CLIENT_CONSTRUIDO")), v)
    }

    @Test
    fun httpClientQualificadoEComAlias() {
        val fonte = "import io.ktor.client.HttpClient as Http\nval h = Http(CIO)\nval k = io.ktor.client.HttpClient(CIO)\n"
        assertEquals(
            listOf("HTTP_CLIENT_ALIAS", "HTTP_CLIENT_ALIAS", "HTTP_CLIENT_CONSTRUIDO"),
            regras("Rota.kt" to fonte),
        )
    }

    @Test
    fun copyDeSettingsDireto() {
        assertEquals(listOf("SETTINGS_COPY"), regras("Rota.kt" to "val s2 = settings.copy(x = 1)\n"))
        assertEquals(listOf("SETTINGS_COPY"), regras("Rota.kt" to "val s2 = Settings.copy(x = 1)\n"))
    }

    @Test
    fun copyDeSettingsAninhado() {
        assertEquals(listOf("SETTINGS_COPY"), regras("Rota.kt" to "val c = ctx.settings.copy(dashboardDevMode = false)\n"))
        // Contexto copiado trocando o campo settings: o `copy` interno é de uma variável qualquer.
        assertEquals(
            listOf("SETTINGS_COPY"),
            regras("Rota.kt" to "val c = ctx.copy(settings = s.copy(dashboardDevMode = false))\n"),
        )
    }

    @Test
    fun copyDaFlagDoJiraEmQualquerVariavel() {
        assertEquals(
            listOf("SETTINGS_COPY"),
            regras("Rota.kt" to "val s2 = cfg.copy(\n    jiraLiveEnabled = false,\n)\n"),
        )
    }

    @Test
    fun copyDeOutraCoisaNaoConta() {
        assertEquals(emptyList(), regras("Rota.kt" to "val r = req.copy(startAt = 10)\nval t = linha.copy(assignee = null)\n"))
    }

    @Test
    fun comentarioNaoConta() {
        val fonte = """
            // val c = JiraClient(cfg)
            /* val h = HttpClient(CIO)
               val k = settings.copy(x = 1) */
            /** Exemplo: ::JiraClient e typealias A = JiraClient */
            val ok = 1
        """.trimIndent() + "\n"
        assertEquals(emptyList(), regras("Rota.kt" to fonte))
    }

    @Test
    fun barrasDentroDeStringNaoAbremComentario() {
        // "http://" não pode esconder o resto da linha: a construção depois dela continua sendo achada.
        val fonte = "val u = \"http://x\"; val c = JiraClient(cfg)\n"
        assertEquals(listOf("JIRA_CLIENT_CONSTRUIDO"), regras("Rota.kt" to fonte))
    }

    @Test
    fun linhasContamDepoisDeComentarioDeBloco() {
        val fonte = "/* a\nb\nc */\nval c = JiraClient(cfg)\n"
        assertEquals(listOf(Violacao("Rota.kt", 4, "JIRA_CLIENT_CONSTRUIDO")), varrer("Rota.kt" to fonte))
    }

    @Test
    fun fabricaPermitida() {
        val fabrica = "val a = JiraClient(c, HttpClient(E), g)\nval r = ::JiraClient\n"
        val cliente = "internal fun createJiraHttpClient(): HttpClient = HttpClient(CIO) { }\n"
        assertEquals(emptyList(), varrer("JiraClientFactory.kt" to fabrica, "JiraClient.kt" to cliente))
    }

    @Test
    fun permitidoVaiPeloNomeDoArquivoMesmoComPasta() {
        val fabrica = "val a = JiraClient(c, g)\n"
        assertEquals(emptyList(), varrer("jira/JiraClientFactory.kt" to fabrica))
    }

    @Test
    fun aliasEmArquivoPermitidoNaoLiberaOUsoFora() {
        val v = varrer(
            "JiraClientFactory.kt" to "typealias Cliente = JiraClient\n",
            "Rota.kt" to "val c = Cliente(cfg)\n",
        )
        assertEquals(listOf(Violacao("Rota.kt", 1, "JIRA_CLIENT_ALIAS")), v)
    }
}
