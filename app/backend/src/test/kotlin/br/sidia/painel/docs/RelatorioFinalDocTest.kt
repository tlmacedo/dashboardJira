package br.sidia.painel.docs

import br.sidia.painel.TestPaths
import java.nio.file.Files
import kotlin.io.path.name
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Relatório final do lado "antes da flag" (roteiro A16). O documento é uma fotografia datada; por isso os números que o
 * código muda (migrações, cartões, itens do menu) são amarrados por LIMITE INFERIOR: o relatório diz quantos existiam no
 * fechamento e o teste só reprova se o código passar a ter MENOS (algo foi removido). Quem acrescentar cartão ou migração
 * não precisa mexer no relatório. Nada aqui acessa o Jira nem a rede.
 */
class RelatorioFinalDocTest {
    private val caminho = "docs/superpowers/relatorio-final-antes-da-flag.md"

    private fun doc(): String = ApoioDeDocs.texto(caminho)

    /** Corpo da seção `## (x) ...`, até a próxima seção de nível 2. */
    private fun secao(letra: String): String {
        val partes = doc().split(Regex("(?m)^(?=## )"))
        return partes.firstOrNull { it.startsWith("## ($letra)") } ?: error("seção ($letra) ausente em $caminho")
    }

    @Test
    fun temAsSeisSecoesNaOrdemDoPedido() {
        val titulos = Regex("(?m)^## \\(([a-f])\\)").findAll(doc()).map { it.groupValues[1] }.toList()
        assertEquals(listOf("a", "b", "c", "d", "e", "f"), titulos)
    }

    @Test
    fun citaCadaPrMescladoNaOrdemDeMerge() {
        // Ordem em que os PRs entraram na main (histórico do git; não muda).
        val ordem = listOf(31, 32, 33, 34, 35, 36, 38, 37, 39, 41, 40, 42, 43, 44, 45, 46, 47, 49, 50, 48)
        val texto = secao("a")
        val posicoes = ordem.map { pr ->
            val m = Regex("""#$pr\b""").find(texto)
            assertTrue(m != null, "PR #$pr sem menção na seção (a)")
            m.range.first
        }
        assertEquals(posicoes.sorted(), posicoes, "os PRs da seção (a) não seguem a ordem de merge")
    }

    @Test
    fun citaOsAchadosImportantsMaisRelevantes() {
        val texto = secao("b")
        for (pr in listOf(32, 36, 37, 40, 42, 43, 44, 45, 48, 49, 50)) {
            assertTrue(Regex("""#$pr\b""").containsMatchIn(texto), "seção (b) sem achado do PR #$pr")
        }
        for (tema in listOf("access_log", "escalada de privilégio", "TOCTOU", "expurgo", "TravaDpo")) {
            assertTrue(tema in texto, "seção (b) sem o tema '$tema'")
        }
    }

    @Test
    fun rulingsSeparamOQuePrecisaDeConfirmacao() {
        val texto = secao("c")
        assertTrue("Precisam de confirmação" in texto, "falta o bloco das decisões que precisam de confirmação")
        val confirmacao = texto.substringAfter("Precisam de confirmação")
        val linhas = confirmacao.substringBefore("### Demais").lines().filter { Regex("^\\| \\d+ \\|").containsMatchIn(it) }
        assertEquals((1..10).map { "$it" }, linhas.map { it.trim().trim('|').split('|')[0].trim() }, "a tabela de confirmações deve ter as 10 linhas")
        val expurgo = linhas.last()
        for (termo in listOf("BANCO_DESCONHECIDO_SEM_SINCRONIZACAO", "BANCO_COM_SINCRONIZACAO_REAL", "expurgo", "apagar")) {
            assertTrue(termo in expurgo, "linha 10 sem '$termo'")
        }
        assertTrue(!texto.substringAfter("### Demais").contains("| expurgo |"), "o expurgo ficou também entre as decisões sem confirmação")
        for (item in listOf("Plano 11", "V7", "person", "Qualidade", "gaveta", "OIDC_DOMINIOS_PERMITIDOS", "DPO")) {
            assertTrue(item in confirmacao, "decisão a confirmar sem '$item'")
        }
        assertTrue("Como reverter" in texto || "como reverter" in texto, "as decisões não dizem como reverter")
    }

    @Test
    fun limitesDizemOQueNaoFoiPossivelEPorQue() {
        val texto = secao("d").lowercase()
        for (tema in listOf(
            "captura", "leitor de tela", ".ps1", "legacy/", "tag", "serviço", "firewall", "proxy", "certificado",
            "dpo", "ativos de marca", "gitguardian", "restauração", "fase 0", "plano 14",
        )) {
            assertTrue(tema in texto, "seção (d) sem o tema '$tema'")
        }
    }

    @Test
    fun numerosBatemComOCodigoPorLimiteInferior() {
        val texto = secao("e")
        val migracoes = Files.list(TestPaths.mainSrcDir.resolve("resources/db/migration")).use { f ->
            f.map { it.name }.filter { Regex("""V\d+__.*\.sql""").matches(it) }.toList()
        }
        val ultima = Regex("""V1 a V(\d+)""").find(texto)?.groupValues?.get(1)?.toInt() ?: error("seção (e) sem 'V1 a Vn'")
        assertTrue(migracoes.size >= ultima, "o código tem ${migracoes.size} migrações; o relatório cita V1 a V$ultima")
        for (n in 1..ultima) assertTrue(migracoes.any { it.startsWith("V${n}__") }, "migração V$n sumiu")
        assertEquals("${ultima} migrações", Regex("""(\d+) migrações""").find(texto)?.value, "contagem de migrações incoerente com 'V1 a V$ultima'")

        val cartoes = Regex("""(\d+) cartões no catálogo""").find(texto)?.groupValues?.get(1)?.toInt() ?: error("seção (e) sem 'N cartões no catálogo'")
        assertTrue(ApoioDeDocs.cartoes().size >= cartoes, "o catálogo tem ${ApoioDeDocs.cartoes().size} cartões; o relatório cita $cartoes")

        val itens = Regex("""(\d+) itens no menu""").find(texto)?.groupValues?.get(1)?.toInt() ?: error("seção (e) sem 'N itens no menu'")
        assertTrue(ApoioDeDocs.menu().size >= itens, "o menu tem ${ApoioDeDocs.menu().size} itens; o relatório cita $itens")
    }

    @Test
    fun listaDoUsuarioVemNaOrdemCombinada() {
        val texto = secao("f")
        val ordem = listOf(
            "proteção da `main`", "VARREDURA_NOMES", "GitGuardian", "ativos de marca", "parecer do DPO", "worktrees",
            "YAML", "Fase 0", "mudar a flag",
        )
        val posicoes = ordem.map { termo ->
            val i = texto.indexOf(termo)
            assertTrue(i >= 0, "lista do usuário sem '$termo'")
            i
        }
        assertEquals(posicoes.sorted(), posicoes, "a lista de ações do usuário está fora da ordem combinada")
        assertTrue("guarda-windows" in texto && "backend" in texto && "frontend" in texto && "guarda" in texto && "varredura" in texto)
    }

    @Test
    fun relatorioNaoLigaAFlagNemCitaHost() {
        val texto = doc()
        assertTrue(!Regex("""JIRA_LIVE_ENABLED\s*[=:]\s*true""", RegexOption.IGNORE_CASE).containsMatchIn(texto), "o relatório escreve a flag como ligada")
        assertEquals(emptyList(), ApoioDeDocs.enderecosDeRede(texto), "o relatório cita endereço de rede")
        assertTrue("ligada" in texto || "valor verdadeiro" in texto, "o relatório deveria dizer 'ligada' ou 'valor verdadeiro' ao falar da flag")
    }
}
