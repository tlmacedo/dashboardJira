package br.sidia.painel.implantacao

import br.sidia.painel.TestPaths
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regra 1 do CLAUDE.md: com a flag de acesso ao Jira desligada nenhum caminho (nem script) acessa o Jira.
 * Varre os scripts do repositório (fora de `legacy/`, que é só referência) e falha se surgir um com cliente de
 * rede que possa falar com o Jira sem checar a flag em `app/.env`.
 *
 * Classificação de cada arquivo com cliente de rede:
 *  - TRAVADO: cita `JIRA_LIVE_ENABLED` em linha de código (lê a flag do arquivo e recusa);
 *  - LOCAL: está em [LOCAIS] (com motivo) e só cita endereços de loopback ou `.invalid`;
 *  - sem relação com Jira (não cita jira, atlassian nem `/rest/api`): ignorado;
 *  - qualquer outro: VIOLAÇÃO.
 */
class ScriptsNaoAcessamJiraSemTravaTest {
    private val repo: Path = TestPaths.repoDir

    /** Scripts que só falam com o próprio painel em loopback (nunca com o Jira); a trava não se aplica. */
    private val locais = mapOf(
        "app/scripts/verificar-pacote.sh" to "só consulta http://127.0.0.1:8080/api/health do painel recém-montado",
        "app/scripts/verificar-pacote.ps1" to "só consulta http://127.0.0.1:8080/api/health do painel recém-montado",
        "deploy/linux/comum.sh" to "baixar/esperar_saude consultam o /api/health do painel (PAINEL_URL, padrão loopback)",
        "deploy/linux/atualizar.sh" to "usa o baixar de comum.sh só contra o painel (PAINEL_URL, padrão loopback)",
        "deploy/windows/comum.ps1" to "consulta o /api/health do painel (PAINEL_URL, padrão loopback)",
    )

    private val extensoes = setOf("py", "ps1", "sh", "mjs", "cjs", "js", "bat", "cmd")
    private val pastasIgnoradas = setOf(".git", "node_modules", "build", ".gradle", "legacy", ".claude", ".clinerules", "pacote", "dist")

    private val clienteDeRede = Regex(
        "urllib|import requests|from requests|httpx|http\\.client|urlopen|Invoke-WebRequest|Invoke-RestMethod|" +
            "System\\.Net|WebClient|\\bcurl\\b|\\bwget\\b|\\bfetch\\(|XMLHttpRequest|node:https?|require\\(['\"]https?['\"]\\)",
        RegexOption.IGNORE_CASE,
    )
    private val citaJira = Regex("jira|atlassian|/rest/api", RegexOption.IGNORE_CASE)
    private val hostLiteral = Regex("https?://([^/\\s:?#\"')\\\\]+)")

    private fun ehComentario(linha: String): Boolean {
        val t = linha.trimStart()
        return t.startsWith("#") || t.startsWith("//") || t.startsWith("\"\"\"") || t.startsWith("REM ")
    }

    /** Devolve o motivo da violação, ou null se o arquivo está em ordem. */
    internal fun violacao(caminho: String, texto: String): String? {
        if (!clienteDeRede.containsMatchIn(texto)) return null
        val codigo = texto.lines().filterNot(::ehComentario)
        if (codigo.any { "JIRA_LIVE_ENABLED" in it }) return null // TRAVADO
        if (caminho in locais) {
            val hosts = hostLiteral.findAll(texto).map { it.groupValues[1] }.toList()
            val fora = hosts.filterNot { it == "127.0.0.1" || it == "localhost" || it.endsWith(".invalid") }
            return if (fora.isEmpty() && "/rest/api" !in texto) null else "LOCAL com endereço externo: $fora"
        }
        if (!citaJira.containsMatchIn(texto)) return null // cliente de rede genérico, sem relação com Jira
        return "cliente de rede que cita o Jira sem checar JIRA_LIVE_ENABLED"
    }

    private fun scripts(): List<Path> = Files.walk(repo).use { fluxo ->
        fluxo.filter { p ->
            p.isRegularFile() && p.extension.lowercase() in extensoes &&
                repo.relativize(p).none { it.toString() in pastasIgnoradas } &&
                !repo.relativize(p).toString().replace('\\', '/').startsWith("app/frontend/src/") &&
                !repo.relativize(p).toString().replace('\\', '/').startsWith("app/backend/src/")
        }.toList()
    }

    private fun rel(p: Path) = repo.relativize(p).toString().replace('\\', '/')

    @Test
    fun nenhumScriptComClienteDeRedeFalaComOJiraSemTrava() {
        val encontrados = scripts()
        assertTrue(encontrados.size >= 20, "a varredura deveria achar os scripts do repositório, achou ${encontrados.size}")
        val violacoes = encontrados.mapNotNull { p ->
            violacao(rel(p), Files.readString(p))?.let { "${rel(p)}: $it" }
        }
        assertEquals(emptyList(), violacoes, "scripts que podem acessar o Jira sem a trava da flag")
    }

    @Test
    fun scriptsConhecidosComTravaEstaoNaVarredura() {
        val nomes = scripts().map(::rel).toSet()
        for (esperado in listOf("scripts/tempo_em_status.py", "docs/jira/diagnostico-conexao.ps1")) {
            assertTrue(esperado in nomes, "$esperado não entrou na varredura")
            assertTrue(clienteDeRede.containsMatchIn(Files.readString(repo.resolve(esperado))), "$esperado deveria ter cliente de rede")
            assertTrue(
                Files.readString(repo.resolve(esperado)).lines().filterNot(::ehComentario).any { "JIRA_LIVE_ENABLED" in it },
                "$esperado perdeu a trava da flag",
            )
        }
    }

    @Test
    fun listaDeScriptsLocaisNaoTemEntradaObsoleta() {
        for ((caminho, motivo) in locais) {
            val arquivo = repo.resolve(caminho)
            assertTrue(Files.isRegularFile(arquivo), "$caminho ($motivo) não existe mais: tire da lista")
            assertTrue(clienteDeRede.containsMatchIn(Files.readString(arquivo)), "$caminho não tem mais cliente de rede: tire da lista")
        }
    }

    @Test
    fun detectorPegaScriptNovoSemTrava() {
        val py = "import urllib.request\nurllib.request.urlopen(url + '/rest/api/2/search')\n"
        assertTrue(violacao("scripts/novo.py", py) != null)
        assertTrue(violacao("scripts/novo.ps1", "Invoke-RestMethod -Uri \$jira/rest/api/2/myself\n") != null)
        assertTrue(violacao("scripts/novo.sh", "curl -s \"\$JIRA_URL/rest/api/2/issue\"\n") != null)
        // comentário que cita a flag não conta como trava
        assertTrue(violacao("scripts/novo.py", "# JIRA_LIVE_ENABLED\n$py") != null)
        // com a checagem em código, passa
        assertEquals(null, violacao("scripts/novo.py", "x = 'JIRA_LIVE_ENABLED'\n$py"))
        // sem cliente de rede, ou cliente sem relação com Jira, passa
        assertEquals(null, violacao("scripts/a.py", "print('jira')\n"))
        assertEquals(null, violacao("scripts/b.sh", "curl -s https://exemplo.invalid/x\n"))
        // script LOCAL com endereço externo deixa de valer
        assertTrue(violacao("deploy/linux/comum.sh", "curl https://meu-servidor.exemplo.com/x\n") != null)
        assertFalse(violacao("deploy/linux/comum.sh", "curl http://127.0.0.1:8080/api/health\n") != null)
    }
}
