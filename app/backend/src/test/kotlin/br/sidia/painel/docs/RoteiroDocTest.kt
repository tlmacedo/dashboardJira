package br.sidia.painel.docs

import br.sidia.painel.TestPaths
import java.nio.file.Files
import kotlin.io.path.name
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * O roteiro antes/depois da flag (`docs/superpowers/plans/2026-10-28-roteiro-antes-e-depois-da-flag.md`) fechado em 29/10/2026:
 * cada entrega do lado "antes" tem o PR real, a seção 3 traz os itens novos e a seção 4 os reparos que as revisões apontaram,
 * com os nomes (passos do checklist, códigos de aviso) conferidos contra os outros documentos e o código. Nada aqui acessa o Jira.
 */
class RoteiroDocTest {
    private val roteiro = "docs/superpowers/plans/2026-10-28-roteiro-antes-e-depois-da-flag.md"
    private val checklist = "docs/jira/checklist-primeira-conexao.md"

    /** Seção de nível 2 cujo título começa por [inicio]. */
    private fun secao(texto: String, inicio: String): String =
        texto.split(Regex("(?m)^(?=## )")).firstOrNull { it.startsWith("## $inicio") } ?: error("seção '$inicio' ausente")

    /** Células de uma linha de tabela, na ordem. */
    private fun celulas(linha: String): List<String> = linha.trim().trim('|').split(Regex("(?<!\\\\)\\|")).map { it.trim() }

    @Test
    fun roteiroSemLinhaAFazerEComPrDeCadaEntrega() {
        val linhas = ApoioDeDocs.texto(roteiro).lines().filter { Regex("^\\| A\\d+ \\|").containsMatchIn(it) }
        assertEquals((1..16).map { "A$it" }, linhas.map { celulas(it)[0] })
        for (linha in linhas) {
            val c = celulas(linha)
            val estado = c.last()
            assertTrue(!estado.lowercase().contains("a fazer"), "${c[0]} ainda 'a fazer': $estado")
            assertTrue(!estado.contains("PR pendente"), "${c[0]} ainda com 'PR pendente': $estado")
            if (c[0] != "A16") assertTrue(Regex("""PR #\d+""").containsMatchIn(estado), "${c[0]} sem o número do PR: $estado")
        }
        assertTrue("Neste PR" in celulas(linhas.last()).last())
    }

    @Test
    fun roteiroTrazOsItensNovosDaSecao3() {
        val s3 = secao(ApoioDeDocs.texto(roteiro), "3.")
        for (termo in listOf(
            "V7", "watermark", ".ps1", "instalar-servico", "docs/analise/equivalencia-por-card.md", "Pergunta pendente", "CompatLegado",
            "PAINEL_USUARIO", "ESTIMATIVA_AUMENTADA", "timeoriginalestimate", "created", "Plano 13, T1, T7, T8, T11, T12, T13",
        )) {
            assertTrue(termo in s3, "seção 3 do roteiro sem '$termo'")
        }
        val ids = Regex("(?m)^\\| (D\\d+) \\|").findAll(s3).map { it.groupValues[1] }.toList()
        assertEquals((1..23).map { "D$it" }, ids, "itens D1 a D23 da seção 3 fora de ordem ou faltando")
    }

    @Test
    fun roteiroCitaOsPassosExtrasQueOChecklistRealmenteTem() {
        val doChecklist = ApoioDeDocs.texto(checklist)
        val s3 = secao(ApoioDeDocs.texto(roteiro), "3.")
        for (passo in listOf(
            "vocabulário de prioridade e de criticidade", "ordem do fluxo e resoluções negativas", "formato do worklog",
            "formato das estimativas no changelog",
        )) {
            assertTrue("## Passo extra (fora dos cinco): $passo" in doChecklist, "o checklist não tem o passo extra '$passo'")
            assertTrue(passo in s3, "a seção 3 do roteiro não cita o passo extra '$passo'")
        }
        assertTrue(Regex("(?m)^## Passo extra .*Reporter").containsMatchIn(doChecklist) && "Reporter" in s3)
        assertTrue(Regex("(?m)^## Passo extra .*papeis\\.yaml").containsMatchIn(doChecklist) && "ids de `papeis.yaml`" in s3)
    }

    @Test
    fun roteiroSecao4TemOsReparosQueAsRevisoesApontaramEOsCodigosExistem() {
        val s4 = secao(ApoioDeDocs.texto(roteiro), "4.")
        val ids = Regex("(?m)^\\| (R\\d+) \\|").findAll(s4).map { it.groupValues[1] }.toList()
        assertEquals((1..22).map { "R$it" }, ids)
        val fontes = Files.walk(TestPaths.mainSrcDir.resolve("kotlin")).use { f ->
            f.filter { it.name.endsWith(".kt") }.toList().joinToString("\n") { Files.readString(it) }
        }
        for (codigo in listOf("HISTORICO_DE_ESTIMATIVA_INCOMPLETO", "HISTORICO_DE_WORKLOG_INCOMPLETO", "CAMPOS_DE_TRABALHO_INDISPONIVEIS", "AUTOR_DESCONHECIDO")) {
            assertTrue(codigo in s4, "seção 4 não cita $codigo")
            assertTrue(codigo in fontes, "$codigo não existe mais em src/main")
        }
    }
}
