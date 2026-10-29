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
 * Classificação de cada arquivo com cliente de rede (em CÓDIGO: comentários, docstrings e blocos `<# #>` não contam):
 *  - TRAVADO: lê a flag `JIRA_LIVE_ENABLED` em linha de código (nem `echo`/mensagem, nem só a constante) ANTES do
 *    primeiro uso de rede do arquivo; em Python, TODA função/bloco que usa rede precisa da checagem (direta ou por
 *    função do próprio arquivo que a faz) antes do primeiro uso de rede dele, para quem importa a função;
 *  - LOCAL: está em [locais] (com motivo) e só cita endereços de loopback ou `.invalid`;
 *  - sem relação com Jira (não cita jira, atlassian nem `/rest/api`): ignorado;
 *  - qualquer outro: VIOLAÇÃO.
 *
 * Lacunas conhecidas (heurística por texto, não análise de fluxo): rede montada por concatenação de texto
 * (`"cu" + "rl"`, URL sem a palavra jira montada em pedaços), `eval`/`__import__`, rede via binário não listado e
 * ordem da checagem dentro de ramos condicionais. A trava de verdade é a flag + os hooks; este teste só pega o descuido.
 */
class ScriptsNaoAcessamJiraSemTravaTest {
    private val repo: Path = TestPaths.repoDir
    private val flag = "JIRA_LIVE" + "_ENABLED"

    /** Scripts que só falam com o próprio painel em loopback (nunca com o Jira); a trava não se aplica. */
    private val locais = mapOf(
        "app/scripts/verificar-pacote.sh" to "só consulta http://127.0.0.1:8080/api/health do painel recém-montado",
        "app/scripts/verificar-pacote.ps1" to "só consulta http://127.0.0.1:8080/api/health do painel recém-montado",
        "deploy/linux/comum.sh" to "baixar/esperar_saude consultam o /api/health do painel (PAINEL_URL, padrão loopback)",
        "deploy/windows/comum.ps1" to "consulta o /api/health do painel (PAINEL_URL, padrão loopback)",
    )

    private val extensoes = setOf("py", "ps1", "psm1", "sh", "bash", "mjs", "cjs", "js", "ts", "bat", "cmd")
    private val pastasIgnoradas = setOf(".git", "node_modules", "build", ".gradle", "legacy", ".claude", ".clinerules", "pacote", "dist", "tests")

    private val clienteDeRede = Regex(
        "urllib|import requests|from requests|httpx|aiohttp|http\\.client|urlopen|importlib\\.import_module|\\bsocket\\b|" +
            "Invoke-WebRequest|Invoke-RestMethod|\\biwr\\b|\\birm\\b|System\\.Net|WebClient|\\bcurl\\b|\\bwget\\b|\\bnc\\b|" +
            "bitsadmin|certutil[^\\n]*urlcache|\\bfetch\\s*\\(|XMLHttpRequest|node:https?|require\\(['\"]https?['\"]\\)",
        RegexOption.IGNORE_CASE,
    )
    private val citaJira = Regex("jira|atlassian|/rest/api", RegexOption.IGNORE_CASE)
    private val hostLiteral = Regex("https?://([^/\\s:?#\"')\\\\]+)")
    private val linhaDeImport = Regex("^\\s*(import |from \\S+ import |using |Add-Type|(const|let|var) .*require\\()")
    private val linhaDeMensagem = Regex(
        "^\\s*(echo|printf|print|write-host|write-output|write-error|write-warning|console\\.|erro\\b|die\\b|raise\\b|throw\\b|f?[\"'])",
        RegexOption.IGNORE_CASE,
    )

    private fun ehComentario(linha: String): Boolean {
        val t = linha.trimStart()
        return t.startsWith("#") || t.startsWith("//") || t.startsWith("REM ", ignoreCase = true) || t.startsWith("::")
    }

    /** Linhas de código: sem comentários de linha, docstrings (""" ou ''') nem blocos `<# #>` e `/* */`. */
    internal fun codigo(texto: String): List<String> {
        val saida = mutableListOf<String>()
        var triplo: String? = null
        var bloco: String? = null
        for (linha in texto.lines()) {
            val t = linha.trim()
            if (triplo != null) {
                if (triplo in linha) triplo = null
                continue
            }
            if (bloco != null) {
                if (linha.contains(bloco)) bloco = null
                continue
            }
            val abre = listOf("\"\"\"", "'''").firstOrNull { t.startsWith(it) }
            if (abre != null) {
                if (t.removePrefix(abre).contains(abre).not()) triplo = abre
                continue
            }
            val ps = t.startsWith("<#")
            val js = t.startsWith("/*")
            if (ps || js) {
                val fecha = if (ps) "#>" else "*/"
                if (!t.drop(2).contains(fecha)) bloco = fecha
                continue
            }
            if (!ehComentario(linha)) saida += linha
        }
        return saida
    }

    private fun usaRede(l: String) = clienteDeRede.containsMatchIn(l) && !linhaDeImport.containsMatchIn(l)

    private fun constantesDaFlag(linhas: List<String>): Set<String> =
        linhas.mapNotNull { Regex("^\\s*(\\w+)\\s*=\\s*[\"']$flag[\"']\\s*$").find(it)?.groupValues?.get(1) }.toSet()

    /** A linha lê a flag: cita o nome (ou a constante dele), sem ser mensagem nem a própria definição da constante. */
    private fun leFlag(l: String, constantes: Set<String>): Boolean {
        if (linhaDeMensagem.containsMatchIn(l)) return false
        if (Regex("^\\s*\\w+\\s*=\\s*[\"']$flag[\"']\\s*$").containsMatchIn(l)) return false
        return flag in l || constantes.any { Regex("\\b$it\\b").containsMatchIn(l) }
    }

    private class Bloco(val nome: String, val cabecalho: List<String>, val corpo: List<String>)

    private fun blocosPython(linhas: List<String>): List<Bloco> {
        val grupos = mutableListOf<MutableList<String>>(mutableListOf())
        for (l in linhas) {
            if (Regex("^(async def|def|class) ").containsMatchIn(l)) grupos += mutableListOf<String>()
            grupos.last() += l
        }
        return grupos.mapIndexed { i, g ->
            if (i == 0) {
                Bloco("<módulo>", emptyList(), g)
            } else {
                val fim = g.indexOfFirst { it.trimEnd().endsWith(":") }.let { if (it < 0) 0 else it }
                val nome = Regex("^(?:async def|def|class) (\\w+)").find(g[0])?.groupValues?.get(1) ?: "?"
                Bloco(nome, g.take(fim + 1), g.drop(fim + 1))
            }
        }
    }

    /** Python: toda função/bloco que usa rede tem de checar a flag (direto ou por função do arquivo) antes do primeiro uso dele. */
    private fun violacaoPython(linhas: List<String>): String? {
        val constantes = constantesDaFlag(linhas)
        val blocos = blocosPython(linhas)
        val guardas = blocos.filter { b -> b.corpo.any { leFlag(it, constantes) } }.map { it.nome }.toMutableSet()
        do {
            val novos = blocos.filter { b -> b.nome !in guardas && guardas.any { g -> b.corpo.any { "$g(" in it } } }.map { it.nome }
            guardas += novos
        } while (novos.isNotEmpty())
        fun guarda(l: String) = leFlag(l, constantes) || guardas.any { "$it(" in l }
        for (b in blocos) {
            if ((b.cabecalho + b.corpo).none(::usaRede)) continue
            val g = b.corpo.indexOfFirst(::guarda)
            val r = b.corpo.indexOfFirst(::usaRede)
            if (g < 0 || (r in 0 until g)) return "'${b.nome}' usa rede sem checar a flag antes (vale também para quem importa a função)"
        }
        return null
    }

    /** Devolve o motivo da violação, ou null se o arquivo está em ordem. */
    internal fun violacao(caminho: String, texto: String): String? {
        val linhas = codigo(texto)
        if (linhas.none { clienteDeRede.containsMatchIn(it) }) return null
        if (flag in linhas.joinToString("\n")) {
            if (caminho.endsWith(".py")) return violacaoPython(linhas)?.let { "$it" }
            val constantes = constantesDaFlag(linhas)
            val g = linhas.indexOfFirst { leFlag(it, constantes) }
            val r = linhas.indexOfFirst(::usaRede)
            return if (g >= 0 && (r < 0 || g <= r)) null else "a flag só é lida DEPOIS do primeiro uso de rede (ou só aparece em mensagem/constante)"
        }
        if (caminho in locais) {
            val hosts = hostLiteral.findAll(texto).map { it.groupValues[1] }.toList()
            val fora = hosts.filterNot { it == "127.0.0.1" || it == "localhost" || it.endsWith(".invalid") }
            return if (fora.isEmpty() && "/rest/api" !in texto) null else "LOCAL com endereço externo: $fora"
        }
        if (!citaJira.containsMatchIn(texto)) return null // cliente de rede genérico, sem relação com Jira
        return "cliente de rede que cita o Jira sem checar $flag"
    }

    private fun ehScript(p: Path): Boolean {
        if (!p.isRegularFile()) return false
        val rel = repo.relativize(p)
        if (rel.any { it.toString() in pastasIgnoradas }) return false
        val r = rel.toString().replace('\\', '/')
        if (r.startsWith("app/frontend/src/") || r.startsWith("app/backend/src/")) return false
        if (p.fileName.toString().startsWith("test")) return false
        if (p.extension.lowercase() in extensoes) return true
        // sem extensão (ou extensão desconhecida): vale se começa com shebang
        return temShebang(p)
    }

    private fun temShebang(p: Path): Boolean =
        runCatching { Files.size(p) < 2_000_000 && Files.newInputStream(p).use { String(it.readNBytes(2)) == "#!" } }.getOrDefault(false)

    private fun scripts(): List<Path> = Files.walk(repo).use { fluxo -> fluxo.filter(::ehScript).toList() }

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
            val texto = Files.readString(repo.resolve(esperado))
            assertTrue(codigo(texto).any(clienteDeRede::containsMatchIn), "$esperado deveria ter cliente de rede")
            assertEquals(null, violacao(esperado, texto), "$esperado perdeu a trava da flag")
        }
    }

    /** M-6: o PowerShell ignora caixa em -match/-eq; a trava do diagnóstico tem de ser tão estrita quanto a do app. */
    @Test
    fun diagnosticoDeConexaoLeAFlagComCaixaExata() {
        val linhas = Files.readString(repo.resolve("docs/jira/diagnostico-conexao.ps1")).lines()
            .filterNot { it.trimStart().startsWith("#") }
        val daFlag = linhas.filter { "JIRA_LIVE_ENABLED" in it }
        assertTrue(daFlag.isNotEmpty(), "o diagnóstico perdeu a leitura da flag")
        val leitura = daFlag.first { "Matches" in it || "-cmatch" in it || "-match" in it }
        assertTrue("-cmatch" in leitura, "o nome da flag deve ser comparado com -cmatch (caixa exata): $leitura")
        assertFalse(Regex("(?<![a-z])-match").containsMatchIn(leitura), "sobrou -match sem c: $leitura")
        assertTrue("-ceq 'true'" in leitura, "o valor deve ser comparado com -ceq (depois de minúsculas, como o app): $leitura")
        assertFalse(Regex("(?<![a-z])-eq ").containsMatchIn(leitura), "sobrou -eq sem c: $leitura")
    }

    @Test
    fun listaDeScriptsLocaisNaoTemEntradaObsoleta() {
        for ((caminho, motivo) in locais) {
            val arquivo = repo.resolve(caminho)
            assertTrue(Files.isRegularFile(arquivo), "$caminho ($motivo) não existe mais: tire da lista")
            assertTrue(codigo(Files.readString(arquivo)).any(clienteDeRede::containsMatchIn), "$caminho não tem mais cliente de rede: tire da lista")
        }
    }

    @Test
    fun detectorPegaScriptNovoSemTrava() {
        val py = "import urllib.request\nurllib.request.urlopen(url + '/rest/api/2/search')\n"
        assertTrue(violacao("scripts/novo.py", py) != null)
        assertTrue(violacao("scripts/novo.ps1", "Invoke-RestMethod -Uri \$jira/rest/api/2/myself\n") != null)
        assertTrue(violacao("scripts/novo.sh", "curl -s \"\$JIRA_URL/rest/api/2/issue\"\n") != null)
        // aliases e outros clientes
        assertTrue(violacao("scripts/a.ps1", "iwr \"\$env:JIRA_URL/rest/api/2/myself\"\n") != null)
        assertTrue(violacao("scripts/a.psm1", "irm \"\$base/rest/api/2/myself\"\n") != null)
        assertTrue(violacao("scripts/a.bat", "bitsadmin /transfer x %JIRA_URL%/rest/api/2/myself out.json\n") != null)
        assertTrue(violacao("scripts/a.bat", "certutil -urlcache -f %JIRA%/rest/api/2/myself out\n") != null)
        assertTrue(violacao("scripts/a.bash", "printf 'GET /rest/api/2/myself' | nc jira.empresa 443\n") != null)
        assertTrue(violacao("scripts/a.py", "import socket\ns = socket.create_connection((jira_host, 443))\n") != null)
        assertTrue(violacao("scripts/a.py", "import importlib\nr = importlib.import_module('requests')\nr.get(jira + '/x')\n") != null)
        assertTrue(violacao("scripts/a.ts", "await fetch (process.env.JIRA_URL + '/rest/api/2/myself')\n") != null)
        // a flag só em comentário, docstring, bloco, echo ou constante NÃO é trava
        assertTrue(violacao("scripts/novo.py", "# $flag\n$py") != null)
        assertTrue(violacao("scripts/novo.py", "\"\"\"Exige $flag.\n\"\"\"\n$py") != null)
        assertTrue(violacao("scripts/novo.ps1", "<#\n exige $flag\n#>\nInvoke-RestMethod \"\$jira/rest/api/2/myself\"\n") != null)
        assertTrue(violacao("scripts/novo.sh", "echo \"ligue $flag antes\"\ncurl \"\$JIRA_URL/rest/api/2/myself\"\n") != null)
        assertTrue(violacao("scripts/novo.py", "NOME = '$flag'\n$py") != null)
        // a leitura da flag vem DEPOIS da rede: não vale
        assertTrue(violacao("scripts/novo.sh", "curl \"\$JIRA_URL/rest/api/2/myself\"\ngrep $flag app/.env\n") != null)
        // com a leitura em código antes da rede, passa
        assertEquals(null, violacao("scripts/novo.sh", "grep $flag app/.env || exit 2\ncurl \"\$JIRA_URL/rest/api/2/myself\"\n"))
        // Python: função pública de rede sem a checagem NO PONTO DE REDE falha, mesmo com main() travado
        val soMain = "NOME = '$flag'\n\n" +
            "def ligada(a):\n    return a.read_text().count(NOME) > 0\n\n" +
            "def buscar(url):\n    return urllib.request.urlopen(url + '/rest/api/2/search')\n\n" +
            "def main():\n    if not ligada(1):\n        return 2\n    buscar('x')\n"
        assertTrue(violacao("scripts/novo.py", "import urllib.request\n$soMain") != null)
        val noPonto = soMain.replace(
            "def buscar(url):\n    return",
            "def buscar(url):\n    if not ligada(1):\n        raise SystemExit(2)\n    return",
        )
        assertEquals(null, violacao("scripts/novo.py", "import urllib.request\n$noPonto"))
        // checagem DEPOIS do uso de rede dentro da função também falha
        val depois = soMain.replace(
            "def buscar(url):\n    return urllib.request.urlopen(url + '/rest/api/2/search')",
            "def buscar(url):\n    r = urllib.request.urlopen(url + '/rest/api/2/search')\n    ligada(1)\n    return r",
        )
        assertTrue(violacao("scripts/novo.py", "import urllib.request\n$depois") != null)
        // sem cliente de rede, ou cliente sem relação com Jira, passa
        assertEquals(null, violacao("scripts/a.py", "print('jira')\n"))
        assertEquals(null, violacao("scripts/b.sh", "curl -s https://exemplo.invalid/x\n"))
        // script LOCAL com endereço externo deixa de valer
        assertTrue(violacao("deploy/linux/comum.sh", "curl https://meu-servidor.exemplo.com/x\n") != null)
        assertFalse(violacao("deploy/linux/comum.sh", "curl http://127.0.0.1:8080/api/health\n") != null)
    }

    @Test
    fun scriptSemExtensaoComShebangEntraNaVarredura() {
        val pasta = Files.createTempDirectory("varredura-")
        try {
            val sem = pasta.resolve("rodar")
            Files.writeString(sem, "#!/bin/sh\ncurl \"\$JIRA_URL/rest/api/2/myself\"\n")
            assertTrue(temShebang(sem))
            val texto = pasta.resolve("leiame")
            Files.writeString(texto, "texto comum\n")
            assertFalse(temShebang(texto))
        } finally {
            pasta.toFile().deleteRecursively()
        }
    }
}
