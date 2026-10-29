package br.sidia.painel.docs

import br.sidia.painel.TestPaths
import java.nio.file.Files
import kotlin.io.path.name
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guia do painel e perguntas frequentes em pt-BR e inglês (Plano 15, Tarefas 6 e 7). Os documentos têm de bater com o código:
 * o menu (`nav.ts`), o catálogo de cartões (`catalogoPadrao()`), as fichas e as traduções das telas. Nada aqui acessa o Jira.
 */
class GuiasDocTest {
    private val guiaPt = "docs/manual/guia-do-painel.md"
    private val guiaEn = "docs/manual/panel-guide.md"
    private val faqPt = "docs/manual/perguntas-frequentes.md"
    private val faqEn = "docs/manual/faq.md"

    private fun guia(lang: String) = ApoioDeDocs.texto(if (lang == "pt") guiaPt else guiaEn)

    /** Ids das seções `### \`id\` — Nome`, na ordem do documento. */
    private fun idsDasSecoes(texto: String): List<String> = Regex("(?m)^### `(\\w+)`").findAll(texto).map { it.groupValues[1] }.toList()

    /** Corpo de cada seção de tela, por id. */
    private fun corposDasSecoes(texto: String): Map<String, String> =
        texto.split(Regex("(?m)^(?=### `)")).drop(1).associate { Regex("^### `(\\w+)`").find(it)!!.groupValues[1] to it }

    private data class Linha(val id: String, val titulo: String, val oQueMostra: String, val papeis: List<String>, val abreTasks: String)

    /** Linhas das tabelas de cartões (`| Id | Cartão | O que mostra | Quem vê | Abre tasks |`). */
    private fun linhasDeCartoes(texto: String): List<Linha> = texto.lines().filter { Regex("^\\| [A-Z]{2}-\\d{2} \\|").containsMatchIn(it) }.map { l ->
        val c = l.trim().trim('|').split(Regex("(?<!\\\\)\\|")).map { it.trim().replace("\\|", "|") }
        assertEquals(5, c.size, "linha de cartão malformada: $l")
        Linha(c[0], c[1], c[2], c[3].split(",").map { it.trim() }, c[4])
    }

    private val normaliza = { s: String -> s.replace(Regex("\\s+"), " ").trim() }

    // ---------------------------------------------------------------------------------------------------------------- guia

    @Test
    fun guiaCitaCadaItemDoMenu() {
        val ids = ApoioDeDocs.menu().map { it.id }
        assertTrue(ids.size > 30, "o menu tem poucos itens: ${ids.size}")
        for (lang in listOf("pt", "en")) assertEquals(ids, idsDasSecoes(guia(lang)), "seções do guia ($lang) diferentes do menu (nav.ts), na ordem do menu")
    }

    @Test
    fun guiaEnMesmasSecoes() {
        assertEquals(idsDasSecoes(guia("pt")), idsDasSecoes(guia("en")))
        val titulos = { t: String -> Regex("(?m)^## ").findAll(t).count() }
        assertEquals(titulos(guia("pt")), titulos(guia("en")), "número de seções de nível 2 diferente entre os guias")
        val tabelas = { t: String -> linhasDeCartoes(t).map { it.id } }
        assertEquals(tabelas(guia("pt")), tabelas(guia("en")), "cartões citados (ou a ordem) diferentes entre os guias")
    }

    @Test
    fun guiaCitaCadaFicha() {
        val fichas = Files.list(TestPaths.configDir.resolve("cards")).use { f -> f.map { it.name }.filter { it.endsWith(".card.yaml") }.toList() }
        assertTrue(fichas.size >= 30, "poucas fichas: ${fichas.size}")
        for (lang in listOf("pt", "en")) {
            val texto = guia(lang)
            val faltando = fichas.filterNot { "app/config/cards/$it" in texto }
            assertTrue(faltando.isEmpty(), "guia ($lang) sem citar as fichas: $faltando")
        }
    }

    @Test
    fun cadaCartaoDoCatalogoEstaNosGuias() {
        val catalogo = ApoioDeDocs.cartoes().keys
        for (lang in listOf("pt", "en")) {
            val citados = linhasDeCartoes(guia(lang)).map { it.id }
            assertEquals(citados.size, citados.toSet().size, "guia ($lang) repete cartão")
            assertEquals(catalogo.sorted(), citados.sorted(), "guia ($lang): cartões do catálogo e do guia diferem (faltando ou a mais)")
        }
    }

    @Test
    fun cartaoCitadoBateComOCatalogo() {
        val catalogo = ApoioDeDocs.cartoes()
        for (lang in listOf("pt", "en")) {
            val traducao = ApoioDeDocs.traducoes(lang)
            val rotulos = ApoioDeDocs.rotuloDoPapel(lang)
            val erros = mutableListOf<String>()
            for (l in linhasDeCartoes(guia(lang))) {
                val meta = catalogo[l.id] ?: run { erros += "${l.id}: não existe no catálogo"; null } ?: continue
                if (normaliza(l.titulo) != normaliza(traducao.getValue(meta.tituloChave))) erros += "${l.id}: nome \"${l.titulo}\" difere da tela"
                if (normaliza(l.oQueMostra) != normaliza(traducao.getValue(meta.oQueMostraChave))) erros += "${l.id}: \"o que mostra\" difere da tela"
                val esperados = meta.papeis.map { rotulos.getValue(it.name) }.toSet()
                if (l.papeis.toSet() != esperados) erros += "${l.id}: papéis ${l.papeis} difere do catálogo $esperados"
                val drill = if (lang == "pt") (if (meta.temDrill) "sim" else "não") else (if (meta.temDrill) "yes" else "no")
                if (l.abreTasks != drill) erros += "${l.id}: \"abre tasks\" ${l.abreTasks} difere do catálogo ($drill)"
            }
            assertTrue(erros.isEmpty(), "guia ($lang) diverge do catálogo:\n" + erros.joinToString("\n"))
        }
    }

    @Test
    fun cartaoEstaNaSecaoDaSuaTela() {
        val catalogo = ApoioDeDocs.cartoes()
        for (lang in listOf("pt", "en")) {
            val erros = corposDasSecoes(guia(lang)).flatMap { (secao, corpo) ->
                linhasDeCartoes(corpo).filter { ApoioDeDocs.itemDoMenuDe(catalogo.getValue(it.id)) != secao }.map { "${it.id} está em $secao" }
            }
            assertTrue(erros.isEmpty(), "guia ($lang) com cartão na tela errada: $erros")
        }
    }

    @Test
    fun quemVeConfereComOMenu() {
        val menu = ApoioDeDocs.menu().associateBy { it.id }
        for (lang in listOf("pt", "en")) {
            val rotulos = ApoioDeDocs.rotuloDoPapel(lang)
            val rotulo = if (lang == "pt") "Quem vê" else "Who sees it"
            for ((id, corpo) in corposDasSecoes(guia(lang))) {
                val linha = Regex("\\*\\*$rotulo\\.\\*\\* (.*)\\.").find(corpo)?.groupValues?.get(1) ?: error("seção $id ($lang) sem \"$rotulo\"")
                val esperado = ApoioDeDocs.todosOsPapeis.filter { it in menu.getValue(id).papeis }.joinToString(", ") { rotulos.getValue(it) }
                assertEquals(esperado, linha, "seção $id ($lang): papéis diferentes do menu")
            }
        }
    }

    @Test
    fun nomeDaSecaoEIgualAoDoMenu() {
        for (lang in listOf("pt", "en")) {
            val traducao = ApoioDeDocs.traducoes(lang)
            val nomes = Regex("(?m)^### `(\\w+)` — (.*)$").findAll(guia(lang)).associate { it.groupValues[1] to it.groupValues[2].trim() }
            for ((id, nome) in nomes) assertEquals(traducao.getValue("nav.$id"), nome, "nome da seção $id ($lang) difere do menu")
        }
    }

    /** Termos que a tela mostra e o guia repete com o texto exato (arquivos de tradução do frontend). */
    private val chavesDosTermos = listOf("auditoria.kpi.noPrazo", "auditoria.kpi.noPrazoAjustado", "pessoas.semCapacidade", "tempo.unidade.util", "tempo.unidade.corrido")

    @Test
    fun termosDoGuiaIguaisAosDaTela() {
        for (lang in listOf("pt", "en")) {
            val traducao = ApoioDeDocs.traducoes(lang)
            val termos = chavesDosTermos.map { traducao.getValue(it) } + traducao.getValue("auditoria.aviso").substringBefore(":")
            for (texto in listOf(guia(lang), ApoioDeDocs.texto(if (lang == "pt") faqPt else faqEn))) {
                val faltando = termos.filterNot { it in texto }
                assertTrue(faltando.isEmpty(), "documento ($lang) sem os termos exatos da tela: $faltando")
            }
        }
    }

    @Test
    fun todoIdDeCartaoCitadoExiste() {
        val catalogo = ApoioDeDocs.cartoes().keys
        for (arquivo in listOf(guiaPt, guiaEn, faqPt, faqEn)) {
            val inexistentes = ApoioDeDocs.idsDeCartaoCitados(ApoioDeDocs.texto(arquivo)) - catalogo
            assertTrue(inexistentes.isEmpty(), "$arquivo cita cartões que não estão no catálogo: $inexistentes")
        }
    }

    @Test
    fun guiasSeReferenciam() {
        assertTrue("panel-guide.md" in guia("pt"))
        assertTrue("guia-do-painel.md" in guia("en"))
        for ((arquivo, alvos) in mapOf(guiaPt to listOf("perguntas-frequentes.md"), guiaEn to listOf("faq.md"), faqPt to listOf("guia-do-painel.md", "faq.md"), faqEn to listOf("panel-guide.md", "perguntas-frequentes.md"))) {
            for (alvo in alvos) assertTrue(alvo in ApoioDeDocs.texto(arquivo), "$arquivo não aponta para $alvo")
        }
        // Todo link relativo para outro arquivo do repositório aponta para algo que existe.
        for (arquivo in listOf(guiaPt, guiaEn, faqPt, faqEn)) {
            val base = TestPaths.repoDir.resolve(arquivo).parent
            val quebrados = Regex("\\]\\(([^)#]+)(?:#[^)]*)?\\)").findAll(ApoioDeDocs.texto(arquivo)).map { it.groupValues[1] }
                .filterNot { it.startsWith("http") }.filterNot { Files.exists(base.resolve(it)) }.toList()
            assertTrue(quebrados.isEmpty(), "$arquivo tem links quebrados: $quebrados")
        }
    }

    @Test
    fun guiasDizemOEstadoDoJiraEDaTravaDoDpo() {
        val pt = guia("pt")
        for (trecho in listOf("JIRA_LIVE_ENABLED=false", "DASHBOARD_DEMO_DATA=true", "AGUARDANDO_ACEITE_DPO", "Plano 11", "previst", "ainda não")) assertTrue(trecho in pt, "guia pt sem: $trecho")
        val en = guia("en")
        for (trecho in listOf("JIRA_LIVE_ENABLED=false", "DASHBOARD_DEMO_DATA=true", "AGUARDANDO_ACEITE_DPO", "Plan 11", "planned", "not merged yet")) assertTrue(trecho in en, "guia en sem: $trecho")
    }

    @Test
    fun secaoDoPlano11MarcadaELigadaAoProgresso() {
        assertTrue("## A completar depois do Plano 11" in guia("pt"))
        assertTrue("## To complete after Plan 11" in guia("en"))
        val progresso = ApoioDeDocs.texto("docs/superpowers/progresso/2026-10-29-plano-15-guias-e-treinamento.md")
        for (trecho in listOf("Plano 11", "Meus dados", "Reconferir após o Plano 11", guiaPt, guiaEn, faqPt, faqEn, "docs/treinamento/")) assertTrue(trecho in progresso, "progresso sem: $trecho")
    }

    // ---------------------------------------------------------------------------------------------------------------- FAQ

    /** Uma palavra-chave no TÍTULO de cada pergunta mínima, na ordem do plano (Tarefa 7). */
    private val perguntasMinimas = mapOf(
        "pt" to listOf("no prazo ajustado", "indício, não prova", "sem capacidade", "dias úteis", "outra pessoa", "de ontem para hoje", "ranking", "parece errado", "responsável, vencimento e labels"),
        "en" to listOf("adjusted on time", "evidence, not proof", "no capacity", "business days", "someone else", "yesterday", "ranking", "looks wrong", "assignee, due date and labels"),
    )

    private fun perguntas(arquivo: String): List<String> = Regex("(?m)^## (\\d+)\\. (.*)$").findAll(ApoioDeDocs.texto(arquivo)).map { it.groupValues[2] }.toList()

    @Test
    fun faqTemAsPerguntasMinimas() {
        for ((lang, arquivo) in mapOf("pt" to faqPt, "en" to faqEn)) {
            val titulos = perguntas(arquivo)
            val chaves = perguntasMinimas.getValue(lang)
            assertTrue(titulos.size >= chaves.size, "FAQ ($lang) com ${titulos.size} perguntas; o mínimo é ${chaves.size}")
            chaves.forEachIndexed { i, chave -> assertTrue(chave in titulos[i].lowercase(), "FAQ ($lang), pergunta ${i + 1} deveria tratar de \"$chave\": ${titulos[i]}") }
        }
    }

    @Test
    fun faqMesmaOrdemNasDuasLinguas() {
        assertEquals(perguntas(faqPt).size, perguntas(faqEn).size, "FAQ com número de perguntas diferente entre as línguas")
        val numeros = { a: String -> Regex("(?m)^## (\\d+)\\.").findAll(ApoioDeDocs.texto(a)).map { it.groupValues[1].toInt() }.toList() }
        assertEquals((1..perguntas(faqPt).size).toList(), numeros(faqPt))
        assertEquals(numeros(faqPt), numeros(faqEn))
    }

    @Test
    fun faqDizOQueEHojeEOQueDependeDoJira() {
        val pt = ApoioDeDocs.texto(faqPt)
        for (trecho in listOf("10 minutos", "03:00", "flag", "Plano 14")) assertTrue(trecho in pt, "FAQ pt sem: $trecho")
        val en = ApoioDeDocs.texto(faqEn)
        for (trecho in listOf("10 minutes", "03:00", "flag", "Plan 14")) assertTrue(trecho in en, "FAQ en sem: $trecho")
    }

    @Test
    fun faqSemNomesNemHosts() {
        for (arquivo in listOf(guiaPt, guiaEn, faqPt, faqEn)) {
            val texto = ApoioDeDocs.texto(arquivo)
            assertTrue(ApoioDeDocs.enderecosDeRede(texto).isEmpty(), "$arquivo cita endereço de rede: ${ApoioDeDocs.enderecosDeRede(texto)}")
            assertTrue(!Regex("[\\w.+-]+@[\\w-]+\\.[\\w.]+").containsMatchIn(texto), "$arquivo cita e-mail")
        }
    }
}
