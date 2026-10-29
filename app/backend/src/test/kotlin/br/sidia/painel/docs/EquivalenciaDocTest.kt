package br.sidia.painel.docs

import br.sidia.painel.cards.contrato.catalogoPadrao
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Conferência de equivalência por card (Plano 8, T3): cada card do inventário do app legado
 * (`docs/analise/relatorio-cards-app-atual.md`) tem uma linha em `docs/analise/equivalencia-por-card.md`, com a
 * situação, o(s) card(s) novo(s) do catálogo e a prova (`Classe.metodo` que existe em `src/test`).
 *
 * Só lê documentos e o catálogo em memória: nenhum dado real, nenhum acesso ao Jira.
 */
class EquivalenciaDocTest {
    private val caminhoDoc = "docs/analise/equivalencia-por-card.md"
    private val caminhoRelatorio = "docs/analise/relatorio-cards-app-atual.md"

    /** Uma linha da tabela principal: `| IDs | situação | cards novos | prova | documento |`. */
    private data class Linha(val ids: List<String>, val situacao: String, val cards: List<String>, val provas: List<Pair<String, String>>, val documento: String, val bruta: String)

    private val idRegex = Regex("""\b[A-Z]{2}-\d{2}\b""")

    private fun raiz(): Path {
        var dir: Path? = Path.of("").toAbsolutePath()
        while (dir != null) {
            if (Files.exists(dir.resolve(caminhoRelatorio))) return dir
            dir = dir.parent
        }
        error("$caminhoRelatorio não encontrado")
    }

    private fun texto(caminho: String): String = Files.readString(raiz().resolve(caminho))

    private fun linhas(): List<Linha> {
        val doc = Files.readString(raiz().resolve(caminhoDoc))
        // Só as linhas da tabela principal: a primeira célula é uma lista de ids do inventário (`AB-01`, `CA-02, CA-03`).
        return doc.lines().filter { it.startsWith("| ") }.mapNotNull { l ->
            val c = l.trim().trim('|').split("|").map { it.trim() }
            if (c.size != 5) return@mapNotNull null
            if (!Regex("""^[A-Z]{2}-\d{2}(, [A-Z]{2}-\d{2})*$""").matches(c[0])) return@mapNotNull null
            Linha(
                ids = idRegex.findAll(c[0]).map { it.value }.toList(),
                situacao = c[1],
                cards = Regex("""`([A-Z]{2}-\d{2})`""").findAll(c[2]).map { it.groupValues[1] }.toList(),
                provas = Regex("""`([A-Z]\w*Test)\.(\w+)`""").findAll(c[3]).map { it.groupValues[1] to it.groupValues[2] }.toList(),
                documento = c[4],
                bruta = l,
            )
        }
    }

    private val situacoes = setOf("equivalente", "diferença esperada", "não migrado")

    @Test
    fun todoIdDoRelatorioTemLinha() {
        val doInventario = idRegex.findAll(texto(caminhoRelatorio).substringBefore("## 5.")).map { it.value }.toSortedSet()
        assertTrue(doInventario.size >= 120, "o inventário devia ter mais de 120 ids; achou ${doInventario.size}")
        val classificados = linhas().flatMap { it.ids }
        val faltando = doInventario - classificados.toSet()
        assertTrue(faltando.isEmpty(), "ids do inventário legado sem linha de equivalência: $faltando")
        val repetidos = classificados.groupBy { it }.filterValues { it.size > 1 }.keys
        assertTrue(repetidos.isEmpty(), "ids classificados duas vezes: $repetidos")
    }

    @Test
    fun todoPrefixoDoInventarioTemLinha() {
        // QD, AU e SI entram pelo prefixo (o relatório não numera as sete verificações do QD).
        val prefixos = listOf("VG", "MP", "AB", "EP", "NG", "RI", "PV", "FE", "NE", "TS", "MT", "CR", "IN", "CP", "CA", "GA", "DI", "RB", "QD", "AU", "SI")
        val presentes = linhas().flatMap { it.ids }.map { it.substringBefore('-') }.toSet()
        assertTrue((prefixos.toSet() - presentes).isEmpty(), "prefixos sem linha: ${prefixos.toSet() - presentes}")
    }

    @Test
    fun todoCardCitadoExisteNoCatalogo() {
        val catalogo = catalogoPadrao()
        val naoExistem = linhas().flatMap { l -> l.cards.map { it to l.ids } }.filter { catalogo.porId(it.first) == null }
        assertTrue(naoExistem.isEmpty(), "cards citados que não estão no catálogo (card, linhas): $naoExistem")
        // Todo id de card entre crases no documento inteiro também precisa existir (cobre a seção de cards novos).
        val doc = texto(caminhoDoc)
        val citados = Regex("""`([A-Z]{2}-\d{2})`""").findAll(doc).map { it.groupValues[1] }.toSet()
        val fantasmas = citados.filter { catalogo.porId(it) == null }
        assertTrue(fantasmas.isEmpty(), "ids entre crases que não existem no catálogo: $fantasmas")
    }

    @Test
    fun todoCardDoCatalogoApareceNoDocumento() {
        // Os cards sem equivalente no legado entram na seção "Cards novos"; nada do catálogo fica sem classificação.
        val citados = Regex("""`([A-Z]{2}-\d{2})`""").findAll(texto(caminhoDoc)).map { it.groupValues[1] }.toSet()
        val fora = catalogoPadrao().cartoes.map { it.meta.id }.filter { it !in citados }
        assertTrue(fora.isEmpty(), "cards do catálogo que o documento não cita (equivalente ou novo): $fora")
    }

    @Test
    fun situacaoValida() {
        val invalidas = linhas().filter { it.situacao !in situacoes }.map { it.ids to it.situacao }
        assertTrue(invalidas.isEmpty(), "situação fora de $situacoes: $invalidas")
        // Quem foi migrado aponta para ao menos um card; quem não foi explica o motivo no documento.
        val semCard = linhas().filter { it.situacao != "não migrado" && it.cards.isEmpty() }.map { it.ids }
        assertTrue(semCard.isEmpty(), "linhas migradas sem card novo: $semCard")
        val semMotivo = linhas().filter { it.situacao == "não migrado" && it.documento.length < 15 }.map { it.ids }
        assertTrue(semMotivo.isEmpty(), "linhas 'não migrado' sem motivo na última coluna: $semMotivo")
    }

    @Test
    fun nenhumaLinhaSemProvaSalvoNaoMigrado() {
        val semProva = linhas().filter { it.situacao != "não migrado" && it.provas.isEmpty() }.map { it.ids }
        assertTrue(semProva.isEmpty(), "linhas sem prova `Classe.metodo`: $semProva")
    }

    @Test
    fun todaProvaExiste() {
        val fontes = Files.walk(raiz().resolve("app/backend/src/test/kotlin")).use { fluxo ->
            fluxo.filter { it.name.endsWith(".kt") }.toList().associate { it.name.removeSuffix(".kt") to Files.readString(it) }
        }
        val provas = linhas().flatMap { it.provas }.toSet()
        assertTrue(provas.size >= 20, "poucas provas citadas: ${provas.size}")
        val inexistentes = provas.filterNot { (classe, metodo) ->
            val fonte = fontes[classe]
            fonte != null && Regex("""fun\s+`?$metodo\b""").containsMatchIn(fonte)
        }
        assertTrue(inexistentes.isEmpty(), "provas que não existem em src/test: $inexistentes")
    }

    @Test
    fun fechamentoResumeEPerguntaPendenteAoUsuario() {
        val doc = texto(caminhoDoc)
        // O resumo do documento bate com a tabela (não fica defasado).
        val por = linhas().groupBy { it.situacao }.mapValues { (_, v) -> v.sumOf { it.ids.size } }
        val resumo = Regex("""Resumo: (\d+) equivalentes?, (\d+) diferenças? esperadas?, (\d+) não migrados?""").find(doc)
        assertTrue(resumo != null, "o documento precisa da linha 'Resumo: N equivalentes, N diferenças esperadas, N não migrados'")
        assertEquals(por["equivalente"] ?: 0, resumo!!.groupValues[1].toInt(), "equivalentes no resumo")
        assertEquals(por["diferença esperada"] ?: 0, resumo.groupValues[2].toInt(), "diferenças no resumo")
        assertEquals(por["não migrado"] ?: 0, resumo.groupValues[3].toInt(), "não migrados no resumo")

        // Termina na pergunta pendente: a remoção do legacy/ depende do "sim" do usuário (Plano 8, T3 passo 5 e T9).
        val ultima = doc.substringAfterLast("\n## ")
        assertTrue(ultima.startsWith("Pergunta pendente"), "a última seção deve ser 'Pergunta pendente ao usuário'")
        assertTrue("(Recomendada)" in ultima, "a pergunta precisa de uma opção marcada (Recomendada)")
        assertTrue(ultima.indexOf("(Recomendada)") < ultima.indexOf("Aprovar com ajustes"), "a recomendada vem primeiro")
        assertTrue("legacy-final" in ultima && "Tarefa 9" in ultima, "a pergunta cita a tag legacy-final e a Tarefa 9")
        assertTrue("sem o \"sim\"" in doc.lowercase() || "sem o “sim”" in doc.lowercase() || "sem resposta" in ultima.lowercase(), "o documento diz que sem o sim nada é removido")
    }
}
