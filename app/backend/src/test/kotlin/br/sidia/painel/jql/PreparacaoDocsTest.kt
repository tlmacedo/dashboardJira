package br.sidia.painel.jql

import br.sidia.painel.TestPaths
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Protege os documentos de preparação da primeira conexão autorizada ao Jira.
 * Os documentos só descrevem chamadas; nenhuma é feita aqui.
 */
class PreparacaoDocsTest {
    /** Raiz do repositório: `app/config` fica dois níveis abaixo dela. */
    private val raiz: Path = TestPaths.configDir.toAbsolutePath().normalize().parent.parent

    /**
     * Frase que abre cada passo do checklist. Escrita sem o nome da flag junto do valor ligado de propósito:
     * os hooks barram gravar a flag em forma de atribuição, e o texto precisa poder ser editado por IA.
     */
    private companion object {
        const val FRASE_DE_CONFIRMACAO = "Confirme que a flag de acesso ao Jira está ligada"
    }

    private fun ler(relativo: String): String {
        val arquivo = raiz.resolve(relativo)
        assertTrue(Files.isRegularFile(arquivo), "Documento ausente: $arquivo")
        return Files.readString(arquivo)
    }

    @Test
    fun checklistETemTodasAsChamadas() {
        val texto = ler("docs/jira/checklist-primeira-conexao.md")
        val chamadas = listOf("/myself", "/field", "/search", "expand=changelog", "/status")
        for (chamada in chamadas) {
            assertTrue(texto.contains(chamada), "Checklist sem a chamada $chamada")
        }
        val ocorrencias = Regex(Regex.escape(FRASE_DE_CONFIRMACAO)).findAll(texto).count()
        assertTrue(ocorrencias >= 5, "Esperadas ao menos 5 menções a '$FRASE_DE_CONFIRMACAO', achadas $ocorrencias")
    }

    @Test
    fun checklistSemRegraAntiga() {
        val texto = ler("docs/jira/checklist-primeira-conexao.md")
        assertTrue(!texto.contains("autorização expressa"), "Checklist ainda cita 'autorização expressa'")
        assertTrue(!texto.contains("por pesquisa"), "Checklist ainda cita 'por pesquisa'")
    }

    /** Seções de passo do checklist: do título `## Passo`/`### Passo` até o próximo título de mesmo nível ou acima. */
    private fun secoesDePasso(texto: String): List<String> {
        val linhas = texto.lines()
        val inicios = linhas.indices.filter { Regex("^#{2,3} Passo \\d").containsMatchIn(linhas[it]) }
        return inicios.map { ini ->
            val nivel = linhas[ini].takeWhile { it == '#' }.length
            val fim = (ini + 1 until linhas.size).firstOrNull { i ->
                val l = linhas[i]
                l.startsWith("#") && l.takeWhile { it == '#' }.length <= nivel
            } ?: linhas.size
            linhas.subList(ini, fim).joinToString("\n")
        }
    }

    @Test
    fun cadaPassoDoChecklistConfirmaAFlag() {
        val secoes = secoesDePasso(ler("docs/jira/checklist-primeira-conexao.md"))
        assertEquals(5, secoes.size, "Esperadas 5 seções de passo")
        for (secao in secoes) {
            assertTrue(
                secao.contains(FRASE_DE_CONFIRMACAO),
                "Seção sem '$FRASE_DE_CONFIRMACAO': ${secao.lines().first()}",
            )
        }
    }

    /** Seções "Passo extra" (fora dos cinco passos): cada uma é uma chamada própria e também confirma a flag. */
    @Test
    fun todoPassoExtraTambemConfirmaAFlag() {
        val texto = ler("docs/jira/checklist-primeira-conexao.md")
        val linhas = texto.lines()
        val inicios = linhas.indices.filter { linhas[it].startsWith("## Passo extra") }
        assertTrue(inicios.size >= 4, "Esperados os passos extras (papéis, vocabulário, worklog, reporter): ${inicios.size}")
        for (ini in inicios) {
            val fim = (ini + 1 until linhas.size).firstOrNull { linhas[it].startsWith("## ") } ?: linhas.size
            val secao = linhas.subList(ini, fim).joinToString("\n")
            assertTrue(secao.contains(FRASE_DE_CONFIRMACAO), "Seção sem '$FRASE_DE_CONFIRMACAO': ${linhas[ini]}")
            assertTrue(secao.contains("Quando parar"), "Seção sem 'Quando parar': ${linhas[ini]}")
            assertTrue(secao.contains("O que conferir"), "Seção sem 'O que conferir': ${linhas[ini]}")
        }
    }

    /** Nomes dos campos do dicionário cuja finalidade ainda diz "a confirmar" (inclusive "tipo a confirmar"). */
    private fun camposAConfirmar(): List<String> {
        val linhas = ler("app/config/jira_campos.exemplo.yaml").lines()
        val saida = mutableListOf<String>()
        var atual: String? = null
        for (l in linhas) {
            Regex("^\\s*- nome:\\s*(.+?)\\s*$").find(l)?.let { atual = it.groupValues[1].trim('"', '\'') }
            if (Regex("^\\s*finalidade:.*a confirmar").containsMatchIn(l)) atual?.let { saida += it }
        }
        return saida.distinct()
    }

    @Test
    fun checklistCitaCadaCampoAConfirmar() {
        val checklist = ler("docs/jira/checklist-primeira-conexao.md")
        val campos = camposAConfirmar()
        assertTrue(campos.size >= 10, "o dicionário deveria ter ao menos 10 campos 'a confirmar', achou $campos")
        for (esperado in listOf("Story Points", "Epic Link", "Flagged", "cf[12416]", "cf[11412]", "Reporter", "Model Name", "Checklist Type")) {
            assertTrue(esperado in campos, "o dicionário perdeu o campo '$esperado' marcado como a confirmar")
        }
        val faltam = (campos + "OS Version").filter { it !in checklist }
        assertEquals(emptyList(), faltam, "campos 'a confirmar' que o checklist não cita")
    }

    @Test
    fun checklistCobreVocabularioFluxoWorklogEReporter() {
        val texto = ler("docs/jira/checklist-primeira-conexao.md")
        for (
            item in listOf(
                "priority", "labelsCriticidade", "prioridadePadrao", "ordem", "resolucoesNegativas", "/rest/api/2/priority",
                "/rest/api/2/resolution", "worklog", "timeSpentSeconds", "from/to", "assignee", "Reporter", "PL",
            )
        ) {
            assertTrue(texto.contains(item), "Checklist sem '$item'")
        }
    }

    @Test
    fun matrizCitaCadaEndpoint() {
        val matriz = ler("docs/jira/matriz-compatibilidade.md")
        val checklist = ler("docs/jira/checklist-primeira-conexao.md")
        // os endpoints do cliente do Jira e das conferências do checklist: cada um precisa de linha própria na matriz
        val endpoints = listOf(
            "POST /rest/api/2/search", "/rest/api/2/field", "/rest/api/2/myself", "/rest/api/2/status",
            "/rest/api/2/issue/{key}/worklog", "/rest/api/2/priority", "/rest/api/2/resolution", "/rest/api/2/user",
        )
        for (e in endpoints) {
            assertTrue(matriz.contains(e), "Matriz sem o endpoint '$e'")
        }
        // o changelog tem duas formas (expand e rota própria)
        assertTrue(matriz.contains("expand=changelog") && matriz.contains("/issue/{key}/changelog"))
        // e tudo o que a matriz cita como endpoint aparece também no checklist (que é o que o usuário executa)
        for (e in endpoints.drop(1)) {
            assertTrue(checklist.contains(e.replace("{key}", "{chave}")) || checklist.contains(e), "Checklist sem o endpoint '$e'")
        }
    }

    @Test
    fun cadaFichaUsaSoCamposDoDicionario() {
        val dic = br.sidia.painel.jql.FieldDictionary.fromYaml(TestPaths.configDir.resolve("jira_campos.exemplo.yaml"))
        val fichas = Files.list(TestPaths.configDir.resolve("cards")).use { s ->
            s.filter { it.fileName.toString().endsWith(".card.yaml") }.sorted().toList()
        }
        assertTrue(fichas.size >= 20, "Esperadas as fichas de card; achou ${fichas.size}")
        for (f in fichas) {
            // a carga já recusa campo fora do dicionário; aqui se confere de novo, ficha por ficha, com o nome do arquivo
            val spec = br.sidia.painel.jql.CardSpecs.fromYaml(f, dic)
            for (campo in spec.campos + spec.filtrosFixos.keys + spec.filtrosExcluidos.keys) {
                assertTrue(dic.has(campo), "${f.fileName}: campo '$campo' fora do dicionário")
            }
        }
    }

    @Test
    fun documentosDeJiraSemRegraAntiga() {
        for (doc in todosOsDocumentosDeJira()) {
            val texto = Files.readString(doc)
            assertTrue(!texto.contains("autorização expressa"), "${raiz.relativize(doc)} ainda cita 'autorização expressa'")
            assertTrue(!texto.contains("por pesquisa"), "${raiz.relativize(doc)} ainda cita 'por pesquisa'")
        }
    }

    @Test
    fun documentosDeJiraSemContainerNemPostgres() {
        val proibidos = Regex("docker|postgres|testcontainers|kubernetes|\\bwsl\\b", RegexOption.IGNORE_CASE)
        for (doc in todosOsDocumentosDeJira()) {
            val achou = proibidos.find(Files.readString(doc))
            assertTrue(achou == null, "${raiz.relativize(doc)} cita '${achou?.value}': o painel roda como pacote, com SQLite (docs/implantacao.md)")
        }
    }

    @Test
    fun testesNaoAcessamOJiraReal() {
        for (arquivo in listOf("docs/jira/checklist-primeira-conexao.md", "docs/jira/runbook-sincronizacao.md")) {
            val texto = ler(arquivo)
            for (item in listOf("MockEngine", "fixtures", "SomenteAFabricaCriaClientesJira", "FlagDesligadaZeroRequisicoesTest")) {
                assertTrue(texto.contains(item), "$arquivo sem '$item'")
            }
            assertTrue(Regex("(?s)testes automáticos.{0,300}mesmo com a flag").containsMatchIn(texto.replace('\n', ' ')), "$arquivo não diz que os testes automáticos nunca acessam o Jira, mesmo com a flag ligada")
        }
        // a prova técnica citada existe de fato
        for (classe in listOf("SomenteAFabricaCriaClientesJira", "FlagDesligadaZeroRequisicoesTest")) {
            val achou = Files.walk(raiz.resolve("app/backend/src/test")).use { s -> s.anyMatch { it.fileName.toString() == "$classe.kt" } }
            assertTrue(achou, "o documento cita $classe, que não existe nos testes")
        }
    }

    private fun todosOsDocumentosDeJira(): List<Path> =
        Files.walk(raiz.resolve("docs/jira")).use { s -> s.filter { it.toString().endsWith(".md") }.toList() }

    @Test
    fun semHostRealNemToken() {
        val hostsPermitidos = setOf("exemplo.invalid", "example.com", "localhost")
        val docs = Files.list(raiz.resolve("docs/jira")).use { s -> s.filter { it.toString().endsWith(".md") }.toList() }
        assertTrue(docs.size >= 2, "Esperados os documentos de docs/jira")
        for (doc in docs) {
            val texto = Files.readString(doc)
            for (m in Regex("https?://([^/\\s:?#)`>\\]]+)", RegexOption.IGNORE_CASE).findAll(texto)) {
                assertTrue(m.groupValues[1].lowercase() in hostsPermitidos, "${doc.fileName}: host real em '${m.value}'")
            }
            assertTrue(!Regex("Bearer\\s+[A-Za-z0-9._~+/=-]{8,}").containsMatchIn(texto), "${doc.fileName}: 'Bearer' seguido de valor")
            assertTrue(
                !Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)+").containsMatchIn(texto),
                "${doc.fileName}: e-mail no documento",
            )
        }
    }

    @Test
    fun runbookAvisaQueAFlagEhAUnicaTrava() {
        val texto = ler("docs/jira/runbook-sincronizacao.md")
        assertTrue(texto.contains("única trava"), "Runbook sem o aviso 'única trava'")
        assertTrue(texto.contains("JIRA_LIVE_ENABLED"), "Runbook sem a flag")
        assertTrue(!texto.contains("autorização expressa"), "Runbook não deve citar a regra antiga")
    }

    @Test
    fun runbookCitaOChecklist() {
        assertTrue(ler("docs/jira/runbook-sincronizacao.md").contains("checklist-primeira-conexao.md"))
    }

    @Test
    fun runbookSoLigaPelaFlag() {
        val texto = ler("docs/jira/runbook-sincronizacao.md")
        assertTrue(texto.contains("JIRA_LIVE_ENABLED"))
        assertTrue(!texto.contains("SYNC_MODE"), "Nenhuma variável SYNC_MODE existe")
        assertTrue(!texto.contains("SYNC_AUTORIZACAO"), "Nenhuma variável SYNC_AUTORIZACAO existe")
    }

    @Test
    fun cadaVariavelDoRunbookTemExemploNoEnv() {
        val runbook = ler("docs/jira/runbook-sincronizacao.md")
        val env = ler("app/.env.example")
        val nomes = (Regex("SYNC_[A-Z_]+").findAll(runbook).map { it.value } + sequenceOf("RETENTION_MONTHS", "ACCESS_LOG_RETENTION_MONTHS")).toSet()
        assertTrue("ACCESS_LOG_RETENTION_MONTHS" in Regex("[A-Z_]+").findAll(runbook).map { it.value }.toSet(), "Runbook sem ACCESS_LOG_RETENTION_MONTHS")
        assertTrue(nomes.size >= 10, "Esperadas as SYNC_* e RETENTION_MONTHS no runbook: $nomes")
        for (nome in nomes) {
            assertTrue(Regex("^#?\\s*$nome=", RegexOption.MULTILINE).containsMatchIn(env), "$nome sem exemplo no .env.example")
        }
        for (nome in listOf("JIRA_SAMSUNG_URL", "JIRA_SAMSUNG_TOKEN", "JIRA_SIDIA_URL", "JIRA_SIDIA_USER", "JIRA_SIDIA_TOKEN", "DATABASE_PATH")) {
            assertTrue(Regex("^#?\\s*$nome=", RegexOption.MULTILINE).containsMatchIn(env), "$nome sem exemplo no .env.example")
        }
    }

    @Test
    fun projetoNaoDependeDeDockerNemPostgres() {
        val proibidos = Regex("docker compose|docker run|postgres|testcontainers", RegexOption.IGNORE_CASE)
        for (doc in listOf("app/.env.example", "app/README.md", "docs/jira/runbook-sincronizacao.md")) {
            assertTrue(!proibidos.containsMatchIn(ler(doc)), "$doc ainda cita Docker/PostgreSQL")
        }
        assertTrue(!Files.exists(raiz.resolve("app/Dockerfile")), "app/Dockerfile deve ser removido")
    }

    @Test
    fun calendarioRealEstaNoGitignoreENoPassoDeCopiaDoRunbook() {
        val gitignore = ler(".gitignore")
        assertTrue(
            Regex("^\\*\\*/config/calendario\\.yaml\\s*$", RegexOption.MULTILINE).containsMatchIn(gitignore),
            ".gitignore sem **/config/calendario.yaml",
        )
        val runbook = ler("docs/jira/runbook-sincronizacao.md")
        assertTrue(runbook.contains("cp app/config/calendario.exemplo.yaml app/config/calendario.yaml"), "Runbook sem a cópia (sh) do calendário")
        assertTrue(
            runbook.contains("Copy-Item app\\config\\calendario.exemplo.yaml app\\config\\calendario.yaml"),
            "Runbook sem a cópia (PowerShell) do calendário",
        )
        assertTrue(Regex("^\\| `calendario.yaml` \\|", RegexOption.MULTILINE).containsMatchIn(runbook), "Runbook sem a linha do calendário na tabela")
    }

    @Test
    fun runbookNaoCitaTarefaInterna() {
        assertTrue(!Regex("Tarefa \\d+").containsMatchIn(ler("docs/jira/runbook-sincronizacao.md")))
    }

    @Test
    fun readmeSemRegraAntiga() {
        for (arquivo in listOf("app/README.md", "app/.env.example")) {
            assertTrue(!ler(arquivo).contains("autorização expressa"), "$arquivo ainda cita a regra antiga")
        }
    }

    @Test
    fun matrizTemALinhaDeFieldsPorId() {
        val texto = ler("docs/jira/matriz-compatibilidade.md")
        val linha = texto.lines().firstOrNull { it.startsWith("|") && it.contains("`fields` por id") }
        assertTrue(linha != null, "Matriz sem a linha de `fields` por id")
        assertTrue(linha.contains("customfield_NNNNN"), "Linha de fields por id sem o formato customfield_NNNNN")
    }

    @Test
    fun matrizTemAsLinhasMinimas() {
        val texto = ler("docs/jira/matriz-compatibilidade.md")
        val itens = listOf(
            "POST /rest/api/2/search",
            "Bearer PAT",
            "Basic",
            "expand=changelog",
            "maxResults=1",
            "filtros salvos",
            "campos por nome",
            "5.000",
            "paginação",
            "dateCompare",
            "taxonomia de labels",
            "tipos de issue",
            "cf[12416]",
            "cf[11412]",
        )
        for (item in itens) {
            // Sem diferenciar maiúsculas: o item pode abrir a frase na tabela.
            assertTrue(texto.contains(item, ignoreCase = true), "Matriz sem o item '$item'")
        }
        for (coluna in listOf("Item", "Validado no bscbi-web", "Neste app", "Como confirmar")) {
            assertTrue(texto.contains(coluna), "Matriz sem a coluna '$coluna'")
        }
    }
}
