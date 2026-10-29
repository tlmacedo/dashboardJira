package br.sidia.painel.lgpd

import br.sidia.painel.TestPaths
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Protege os documentos da LGPD: o relatório para o DPO, o atendimento ao titular e a comunicação à equipe. Só leitura de arquivos. */
class DocumentosLgpdTest {
    private val raiz: Path = TestPaths.configDir.toAbsolutePath().normalize().parent.parent
    private val testes: Path = raiz.resolve("app/backend/src/test/kotlin")

    private fun ler(relativo: String): String {
        val arquivo = raiz.resolve(relativo)
        assertTrue(Files.isRegularFile(arquivo), "Documento ausente: $arquivo")
        return arquivo.readText()
    }

    private val relatorio get() = ler("docs/lgpd/relatorio-para-o-dpo.md")
    private val atendimento get() = ler("docs/lgpd/atendimento-ao-titular.md")
    private val comunicacao get() = ler("docs/lgpd/comunicacao-a-equipe.md")
    private val todos get() = listOf(relatorio, atendimento, comunicacao, ler("docs/lgpd/inventario-de-dados.md"))

    /** As seções `## N. Título` do relatório, na ordem em que aparecem. */
    private fun secoes(texto: String): List<Pair<Int, String>> =
        Regex("(?m)^## (\\d+)\\. (.+)$").findAll(texto).map { it.groupValues[1].toInt() to it.groupValues[2] }.toList()

    private fun secao(n: Int): String {
        val t = relatorio
        val inicio = Regex("(?m)^## $n\\. ").find(t)?.range?.first ?: error("sem a seção $n")
        val fim = Regex("(?m)^## ${n + 1}\\. ").find(t, inicio)?.range?.first ?: t.length
        return t.substring(inicio, fim)
    }

    @Test
    fun relatorioTemAsTrezeSecoes() {
        assertEquals((1..13).toList(), secoes(relatorio).map { it.first }, "as seções 1 a 13, nesta ordem")
        val titulos = secoes(relatorio).map { it.second.lowercase() }
        assertTrue("o que é o painel" in titulos[0], titulos[0])
        assertTrue("titulares" in titulos[1], titulos[1])
        assertTrue("perguntas ao dpo" in titulos[10], titulos[10])
        assertTrue("aceite" in titulos[11], titulos[11])
        // A seção 1 avisa que o Plano 16 (escrita no Jira) exige rever o relatório e o aceite antes de ligar.
        assertTrue(secao(1).contains("Plano 16") && secao(1).contains("somente leitura"), "seção 1 sem o aviso do Plano 16")
    }

    @Test
    fun relatorioCitaCadaModuloSensivelETrava() {
        for (item in listOf("AUDITORIA", "INTEGRIDADE", "DASHBOARD_DPO_ACEITE", "DASHBOARD_DPO_ACEITE_REF")) {
            assertTrue(relatorio.contains(item), "relatório sem '$item'")
        }
        // Quem liga o aceite é o usuário, depois do parecer; o relatório não diz que o aceite já foi dado.
        assertTrue(secao(12).contains("usuário"), "seção 12 sem dizer que quem liga é o usuário")
        assertTrue(relatorio.contains("HANDOFF.md"))
    }

    @Test
    fun relatorioCitaCadaVariavelDeRetencao() {
        for (v in listOf("RETENTION_HISTORICO_MONTHS", "RETENTION_ACCESS_LOG_MONTHS", "RETENTION_DECISOES_MONTHS", "RETENTION_EXECUCOES_MONTHS")) {
            assertTrue(secao(8).contains(v), "seção 8 sem $v")
        }
        assertTrue(secao(8).contains("24 meses"))
    }

    @Test
    fun cadaPerguntaTemOpcoesComRecomendada() {
        val perguntas = Regex("(?m)^### Pergunta (\\d+)\\b.*$").findAll(secao(11)).toList()
        assertEquals((1..7).toList(), perguntas.map { it.groupValues[1].toInt() }, "as sete perguntas, em ordem")
        val s11 = secao(11)
        perguntas.forEachIndexed { i, p ->
            val fim = perguntas.getOrNull(i + 1)?.range?.first ?: s11.length
            val bloco = s11.substring(p.range.last, fim)
            val opcoes = bloco.lines().filter { it.startsWith("- ") }
            assertTrue(opcoes.size >= 2, "Pergunta ${i + 1}: menos de duas opções")
            assertEquals(1, opcoes.count { "(Recomendada)" in it }, "Pergunta ${i + 1}: exatamente uma opção recomendada")
            assertTrue("(Recomendada)" in opcoes.first(), "Pergunta ${i + 1}: a recomendada vem primeiro")
            assertTrue(opcoes.all { "Consequência" in it || "consequência" in it }, "Pergunta ${i + 1}: cada opção diz a consequência no painel")
        }
    }

    @Test
    fun medidasCitamTestesQueExistem() {
        val citados = Regex("`([A-Z][A-Za-z0-9]*Test)\\.([a-zA-Z][A-Za-z0-9]*)`").findAll(secao(6)).map { it.groupValues[1] to it.groupValues[2] }.toSet()
        assertTrue(citados.size >= 10, "a seção 6 cita poucos testes: $citados")
        val arquivos = Files.walk(testes).use { s -> s.filter { it.extension == "kt" }.toList() }
        for ((classe, metodo) in citados) {
            val alvo = arquivos.filter { it.name == "$classe.kt" }
            assertTrue(alvo.isNotEmpty(), "teste citado não existe: $classe")
            assertTrue(alvo.any { Regex("fun\\s+$metodo\\s*\\(").containsMatchIn(it.readText()) }, "$classe não tem o método $metodo")
        }
    }

    @Test
    fun baseLegalNuncaAfirmada() {
        for (t in todos) assertTrue(!t.contains("a base legal é", ignoreCase = true), "documento afirma a base legal")
        // As candidatas aparecem como perguntas ao DPO.
        assertTrue(secao(5).contains("?") && secao(5).contains("legítimo interesse"), "seção 5 sem as candidatas como pergunta")
        assertTrue(secao(5).contains("a definir pelo DPO") || secao(5).contains("decisão do DPO"))
    }

    @Test
    fun comunicacaoCitaCadaPapelEIndicioNaoProva() {
        for (papel in listOf("Usuário", "Squad Leader", "Coordenador", "Gerente")) assertTrue(comunicacao.contains(papel), "comunicação sem o papel $papel")
        assertTrue(comunicacao.contains("indício, não prova", ignoreCase = true))
        for (item in listOf("Meus dados", "não escreve no Jira", "ranking", "24 meses", "registrada")) {
            assertTrue(comunicacao.contains(item, ignoreCase = true), "comunicação sem '$item'")
        }
        // Linguagem simples: nenhum termo técnico do painel na comunicação à equipe.
        for (jargao in listOf("access_log", "SQLite", "JSON", "API", "endpoint", "OIDC", "CSP", "CSRF", "migração", "banco de dados")) {
            assertTrue(!comunicacao.contains(jargao), "jargão na comunicação: $jargao")
        }
    }

    @Test
    fun documentosNaoNegamAPontuacaoQueAIntegridadeEntrega() {
        // Cada campo do DTO por pessoa da Integridade tem o seu termo no relatório (campo novo sem termo derruba o teste).
        val termos = mapOf(
            "pessoa" to "pessoa", "nivel" to "nível", "pontos" to "pontos", "concluidas" to "concluídas", "taxa" to "taxa",
            "multiplo" to "múltiplo", "graves" to "graves", "evidencias" to "evidências",
        )
        val campos = br.sidia.painel.integridade.NivelPessoaDto::class.java.declaredFields.map { it.name }.filter { !it.startsWith("$") && it != "Companion" }
        for (c in campos) {
            val termo = termos[c] ?: error("campo '$c' do NivelPessoaDto sem termo no relatório: descreva-o e acrescente aqui")
            assertTrue(secao(4).contains(termo, ignoreCase = true) || secao(7).contains(termo, ignoreCase = true), "relatório (seções 4 e 7) sem '$termo' ($c)")
        }
        // Nem o relatório nem o aviso à equipe voltam a negar nota ou comparação entre pessoas.
        for (t in listOf(relatorio, comunicacao)) {
            for (negacao in listOf("sem nota", "nem nota", "sem comparação entre pessoas", "não compara pessoas", "não pontua")) {
                assertTrue(!t.contains(negacao, ignoreCase = true), "documento nega o que o painel entrega: '$negacao'")
            }
        }
        assertTrue(comunicacao.contains("pontos") && comunicacao.contains("média do time"), "a comunicação não explica a pontuação")
        assertTrue(secao(4).contains("compara") && secao(7).contains("pergunta 2"))
    }

    @Test
    fun atendimentoDizComoConsultarCorrigirEEliminar() {
        for (item in listOf("Meus dados", "JSON", "canal do DPO", "runbook-incidentes", "corrigid", "elimina", "prazo")) {
            assertTrue(atendimento.contains(item, ignoreCase = true), "atendimento sem '$item'")
        }
    }

    @Test
    fun semDadosReais() {
        for (t in todos) {
            assertTrue(!Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.(?!invalid)[A-Za-z]{2,}").containsMatchIn(t), "e-mail real")
            assertTrue(!Regex("https?://(?![^\\s)]*\\.invalid)").containsMatchIn(t), "endereço https fora de .invalid")
        }
    }
}
