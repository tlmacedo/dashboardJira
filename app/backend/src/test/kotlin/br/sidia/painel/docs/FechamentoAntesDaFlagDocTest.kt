package br.sidia.painel.docs

import br.sidia.painel.TestPaths
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Fechamento documental do lado "antes da flag" (roteiro A16, Plano 8 T10): índice dos planos, `HANDOFF.md`, `PRODUCT.md`,
 * `app/README.md` e `CLAUDE.md` dizem só o que o código e os outros documentos sustentam. O roteiro tem o `RoteiroDocTest`.
 * Nada aqui acessa o Jira.
 */
class FechamentoAntesDaFlagDocTest {
    private val indice = "docs/superpowers/plans/2026-10-24-indice-dos-planos.md"

    /** Seção de nível 2 cujo título começa por [inicio]. */
    private fun secao(texto: String, inicio: String): String =
        texto.split(Regex("(?m)^(?=## )")).firstOrNull { it.startsWith("## $inicio") } ?: error("seção '$inicio' ausente")

    /** Células de uma linha de tabela, na ordem. */
    private fun celulas(linha: String): List<String> = linha.trim().trim('|').split(Regex("(?<!\\\\)\\|")).map { it.trim() }

    // ------------------------------------------------------------------------------------------------------ índice

    @Test
    fun indiceDeixaAguardandoSoOQueDependeDoPcDaEmpresaOuDoJira() {
        val linhas = ApoioDeDocs.texto(indice).lines().filter { Regex("^\\| (\\d+[AB]?)( \\(|\\s*\\|)").containsMatchIn(it) }
        assertTrue(linhas.size >= 15, "poucas linhas de fase no índice: ${linhas.size}")
        val aguardam = linhas.filter { celulas(it).last().lowercase().contains("aguardando") }.map { celulas(it)[0] }
        assertEquals(listOf("0", "14 (15 tarefas: Tarefa 0 dos hooks, 14a e 14b)"), aguardam, "só a Fase 0 e a 14 aguardam")
        for (linha in linhas) assertTrue(!celulas(linha).last().contains("PR pendente"), "índice com 'PR pendente': ${celulas(linha)[0]}")
        // As fases mescladas citam o PR.
        for (fase in listOf("4", "5", "6", "7", "8", "10", "11", "12", "13", "15", "17")) {
            val l = linhas.first { celulas(it)[0].startsWith("$fase ") || celulas(it)[0] == fase }
            assertTrue(Regex("""#\d+""").containsMatchIn(celulas(l).last()), "fase $fase sem número de PR no estado")
        }
    }

    @Test
    fun indiceListaTodasAsMigracoes() {
        val texto = ApoioDeDocs.texto(indice)
        val arquivos = Files.list(TestPaths.mainSrcDir.resolve("resources/db/migration")).use { f -> f.map { it.name }.toList() }
        val migracoes = arquivos.filter { Regex("""V\d+__.*\.sql""").matches(it) }
        assertTrue(migracoes.size >= 7)
        for (a in migracoes) assertTrue(a.removeSuffix(".sql") in texto, "índice não cita a migração $a")
    }

    // ------------------------------------------------------------------------------------------------------ handoff

    @Test
    fun handoffEhEnxutoEApontaParaOQueImporta() {
        val texto = ApoioDeDocs.texto("HANDOFF.md")
        assertTrue(texto.lines().size <= 110, "HANDOFF com ${texto.lines().size} linhas: o estado detalhado mora nos arquivos de progresso")
        for (termo in listOf(
            "relatorio-final-antes-da-flag.md", "app/iniciar.sh", "docs/superpowers/progresso/", "Ruling", "V7", "DPO", "Tarefas 4 a 9",
            "docs/jira/checklist-primeira-conexao.md", "roteiro-antes-e-depois-da-flag.md", "JIRA_LIVE_ENABLED",
        )) {
            assertTrue(termo in texto, "HANDOFF sem '$termo'")
        }
        assertTrue("PR pendente" !in texto, "HANDOFF ainda cita PR pendente")
        assertTrue(!Regex("""JIRA_LIVE_ENABLED\s*=\s*true""", RegexOption.IGNORE_CASE).containsMatchIn(texto))
    }

    @Test
    fun handoffListaAsRotasPublicasEAMigracaoDoCodigo() {
        val texto = ApoioDeDocs.texto("HANDOFF.md")
        val publicas = br.sidia.painel.security.ROTAS_PUBLICAS
        assertTrue(publicas.isNotEmpty())
        for (rota in publicas) assertTrue("`$rota`" in texto, "HANDOFF sem a rota pública $rota")
        val ultima = Files.list(TestPaths.mainSrcDir.resolve("resources/db/migration")).use { f ->
            f.map { it.name }.toList().mapNotNull { Regex("""^V(\d+)__""").find(it)?.groupValues?.get(1)?.toInt() }.max()
        }
        assertTrue("V1 a V$ultima" in texto && "próxima livre é a V${ultima + 1}" in texto, "HANDOFF com a migração desatualizada (código: V$ultima)")
        assertTrue(!Regex("""\d[\d.]*\s+testes""").containsMatchIn(texto), "HANDOFF cita contagem de testes, que não é amarrada")
    }

    @Test
    fun handoffTemOLugarDasConfirmacoesDoUsuarioEOAvisoDaRegra8() {
        val handoff = ApoioDeDocs.texto("HANDOFF.md")
        val tabela = handoff.substringAfter("## Confirmações do usuário").substringBefore("\n## ")
        val linhas = tabela.lines().filter { Regex("^\\| \\d+ \\|").containsMatchIn(it) }
        assertEquals((1..10).map { "$it" }, linhas.map { celulas(it)[0] })
        assertTrue(linhas.all { celulas(it).size == 4 }, "cada confirmação precisa de Decisão, Resposta e Data")
        // A tabela do HANDOFF tem as mesmas decisões da primeira tabela da seção (c) do relatório.
        val relatorio = ApoioDeDocs.texto("docs/superpowers/relatorio-final-antes-da-flag.md")
        val noRelatorio = relatorio.substringAfter("### Precisam de confirmação").substringBefore("### Demais").lines().count { Regex("^\\| \\d+ \\|").containsMatchIn(it) }
        assertEquals(noRelatorio, linhas.size)
        for (texto in listOf(handoff.substringBefore("## COMECE AQUI"), relatorio.substringBefore("## (a)"))) {
            assertTrue("Aviso ao usuário (regra 8" in texto && "Opus" in texto && "objetar" in texto, "aviso da regra 8 fora do topo")
        }
        val gradle = ApoioDeDocs.texto("app/backend/gradle.properties")
        assertTrue("-Xmx3g" in gradle && "-Xmx3g" in handoff, "HANDOFF sem o -Xmx3g do Gradle")
    }

    @Test
    fun tetosDaPrimeiraCargaVemAntesDeLigarAFlagEBatemComOCodigo() {
        // Os nomes e os padrões vêm do código: sem os tetos pequenos a primeira carga sai com 50000 itens e 2000 requisições.
        val settings = ApoioDeDocs.texto("app/backend/src/main/kotlin/br/sidia/painel/config/Settings.kt")
        assertTrue("\"SYNC_MAX_ITENS\"" in settings && "\"SYNC_MAX_REQUISICOES\"" in settings)
        val padroes = ApoioDeDocs.texto("app/backend/src/main/kotlin/br/sidia/painel/sync/SyncSettings.kt")
        assertTrue("maxItens: Int = 50_000" in padroes && "maxRequisicoes: Int = 2_000" in padroes)

        val teto = "SYNC_MAX_ITENS=50"
        val reqs = "SYNC_MAX_REQUISICOES=5"
        // HANDOFF: o passo dos tetos vem antes do passo em que o usuário liga a flag.
        val handoff = ApoioDeDocs.texto("HANDOFF.md").substringAfter("## Primeira conexão")
        assertTrue(teto in handoff && reqs in handoff && "50000" in handoff && "2000" in handoff)
        assertTrue(handoff.indexOf(teto) < handoff.indexOf("liga a flag à mão"), "HANDOFF: tetos depois de ligar a flag")
        // Relatório (f): o item dos tetos vem antes do item "mudar a flag".
        val f = ApoioDeDocs.texto("docs/superpowers/relatorio-final-antes-da-flag.md").substringAfter("## (f)")
        assertTrue(f.indexOf(teto) in 0 until f.indexOf("mudar a flag"), "relatório (f): tetos depois de mudar a flag")
        // Roteiro, seção 2: idem.
        val s2 = ApoioDeDocs.texto("docs/superpowers/plans/2026-10-28-roteiro-antes-e-depois-da-flag.md").substringAfter("## 2.").substringBefore("## 3.")
        assertTrue(s2.indexOf(teto) in 0 until s2.indexOf("Escrever a linha da flag"), "roteiro seção 2: tetos depois da flag")
        // Em nenhum documento o teto aparece só depois da flag: o runbook manda defini-los com a flag desligada.
        assertTrue("Ainda com `JIRA_LIVE_ENABLED=false`" in ApoioDeDocs.texto("docs/jira/runbook-sincronizacao.md"))
    }

    // ------------------------------------------------------------------------------------------------------ product e claude.md

    /**
     * Caminhos citados entre crases num trecho: o que tem barra ou termina em extensão de arquivo, sem espaço nem endereço de rede.
     * Nome de YAML sem pasta (`papeis.yaml`) é configuração REAL, que fica fora do git por regra, e não é caminho do repositório.
     */
    private fun caminhosEmCrase(trecho: String): List<String> =
        Regex("`([^`\\s]+)`").findAll(trecho).map { it.groupValues[1] }
            .filter { !it.startsWith("http") && (it.contains('/') || Regex("""\.(md|sh|ps1|example)$""").containsMatchIn(it)) }.toList()

    private fun existeOuCasa(caminho: String): Boolean {
        if ('*' !in caminho) return ApoioDeDocs.existe(caminho)
        // Curinga no último segmento (por exemplo `specs/2026-10-25-spec-fase-*.md`): vale se a pasta existe e ao menos um arquivo casa.
        val dir = TestPaths.repoDir.resolve(caminho.substringBeforeLast('/'))
        val re = Regex("^" + caminho.substringAfterLast('/').split('*').joinToString(".*") { Regex.escape(it) } + "$")
        return Files.isDirectory(dir) && Files.list(dir).use { f -> f.anyMatch { re.matches(it.name) } }
    }

    @Test
    fun claudeMdSemCaminhoInexistente() {
        val texto = ApoioDeDocs.texto("CLAUDE.md")
        val tabela = texto.substringAfter("## Onde está cada coisa").substringBefore("\n## ")
        val caminhos = caminhosEmCrase(tabela)
        assertTrue(caminhos.size >= 15, "poucos caminhos na tabela 'Onde está cada coisa': ${caminhos.size}")
        val inexistentes = caminhos.filterNot { existeOuCasa(it) }
        assertEquals(emptyList(), inexistentes, "caminhos da tabela do CLAUDE.md que não existem")
    }

    @Test
    fun claudeMdTabelaApontaOsDocumentosDoFechamento() {
        val tabela = ApoioDeDocs.texto("CLAUDE.md").substringAfter("## Onde está cada coisa").substringBefore("\n## ")
        for (caminho in listOf(
            "HANDOFF.md", "docs/superpowers/relatorio-final-antes-da-flag.md", "docs/manual/", "docs/operacao/", "docs/implantacao.md",
            "docs/lgpd/", "docs/jira/", "app/config/", "app/scripts/", "deploy/", "docs/analise/equivalencia-por-card.md",
        )) {
            assertTrue("`$caminho`" in tabela, "tabela do CLAUDE.md sem `$caminho`")
        }
    }

    @Test
    fun claudeMdMantemAsRegrasEAsDecisoesDeProduto() {
        // O fechamento só mexe na tabela de caminhos: as regras 1 a 8 e as decisões de produto continuam como estavam.
        val texto = ApoioDeDocs.texto("CLAUDE.md")
        val regras = texto.substringAfter("## Regras que valem sempre").substringBefore("## Decisões de produto")
        for (n in 1..8) assertTrue(Regex("(?m)^$n\\. ").containsMatchIn(regras), "regra $n sumiu do CLAUDE.md")
        assertTrue("A única trava de acesso ao Jira é a flag `JIRA_LIVE_ENABLED`" in regras)
        val decisoes = texto.substringAfter("## Decisões de produto").substringBefore("## Onde está cada coisa")
        for (termo in listOf("Fuso", "Concluída", "Atraso justificado", "Sem ranking de pessoas", "Acesso", "Identidade", "Auditoria", "Ausências e feriados")) {
            assertTrue(termo in decisoes, "decisão de produto '$termo' sumiu")
        }
    }

    @Test
    fun productCitaSoCaminhosQueExistemEFalaDoQueExiste() {
        val texto = ApoioDeDocs.texto("PRODUCT.md")
        val inexistentes = caminhosEmCrase(texto).filterNot { existeOuCasa(it) }
        assertEquals(emptyList(), inexistentes, "caminhos do PRODUCT.md que não existem")
        val capacidades = secao(texto, "Capabilities and Constraints")
        for (termo in listOf("Central", "Meus dados", "modo sombra", "trava do DPO", "Mapa de cartões", "cartão")) {
            assertTrue(termo in capacidades, "Capabilities sem '$termo'")
        }
        val evidencias = secao(texto, "Evidence on Hand")
        for (termo in listOf("relatorio-final-antes-da-flag.md", "equivalencia-por-card.md", "guia-do-painel.md", "docs/jira/referencia/")) {
            assertTrue(termo in evidencias, "Evidence on Hand sem '$termo'")
        }
        assertTrue("cards novos aprovados" !in texto.lowercase(), "PRODUCT.md ainda trata os cards novos como aprovados e não feitos")
    }

    // ------------------------------------------------------------------------------------------------------ README

    @Test
    fun readmeDocumentaOsGuardasDeProducaoEAsVariaveisDoPacote() {
        val readme = ApoioDeDocs.texto("app/README.md")
        val fontesDasVariaveis = ApoioDeDocs.texto("app/.env.example") + ApoioDeDocs.texto("deploy/linux/comum.sh")
        for (variavel in listOf(
            "PAINEL_USUARIO", "OIDC_DOMINIOS_PERMITIDOS", "OIDC_USERNAME_ESTRATEGIA", "OIDC_HOSTS_PERMITIDOS", "DASHBOARD_PROXY_CONFIAVEL",
            "DASHBOARD_ORIGEM_PUBLICA", "DASHBOARD_PORT",
        )) {
            assertTrue(variavel in readme, "app/README.md sem $variavel")
            assertTrue(variavel in fontesDasVariaveis, "$variavel não existe mais no modelo de variáveis nem nos scripts")
        }
        for (termo in listOf("SHA-256", "montar-pacote", "atualizar", "reverter", "expurgo", "BANCO_DESCONHECIDO_SEM_SINCRONIZACAO", "BANCO_COM_SINCRONIZACAO_REAL", "guardas de produção")) {
            assertTrue(termo in readme, "app/README.md sem '$termo'")
        }
        assertTrue(!Regex("""JIRA_LIVE_ENABLED\s*=\s*true""", RegexOption.IGNORE_CASE).containsMatchIn(readme))
    }

    @Test
    fun documentosDoFechamentoExistem() {
        val roteiro = "docs/superpowers/plans/2026-10-28-roteiro-antes-e-depois-da-flag.md"
        for (d in listOf("docs/superpowers/relatorio-final-antes-da-flag.md", roteiro, indice, "HANDOFF.md", "PRODUCT.md", "app/README.md")) {
            assertTrue(Files.isRegularFile(Path.of(TestPaths.repoDir.toString(), d)), "documento ausente: $d")
        }
    }
}
