package br.sidia.painel.implantacao

import br.sidia.painel.TestPaths
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Plano 13, Tarefa 9: segredos fora do repositório, arquivo de variáveis protegido, gerador do `SESSION_SECRET` que não imprime
 * o valor e verificação de logs. Conferências estáticas dos documentos e scripts, mais execuções reais dos scripts `sh` em pasta
 * temporária (valores fictícios).
 */
class SegredosDeImplantacaoTest {
    private val repo: Path = TestPaths.repoDir
    private fun ler(relativo: String) = Files.readString(repo.resolve(relativo))

    private val envExample get() = ler("app/.env.example")
    private val modelo get() = ler("deploy/modelos/painel.env.exemplo")
    private val segredosMd get() = ler("docs/implantacao/segredos.md")

    private val linhaDeVariavel = Regex("^#?\\s?([A-Z][A-Z0-9_]{2,})=(.*)$")
    private fun variaveis(texto: String): List<Pair<String, String>> =
        texto.lines().mapNotNull { linhaDeVariavel.find(it)?.destructured?.let { (n, v) -> n to v.trim() } }

    /** Só as linhas ativas (sem `#`), como o painel as lê. */
    private fun ativas(texto: String): Map<String, String> =
        texto.lines().mapNotNull { Regex("^([A-Z][A-Z0-9_]*)=(.*)$").find(it.trim())?.destructured?.let { (n, v) -> n to v.trim() } }.toMap()

    private val ehSecreta = Regex("SECRET|PASSWORD|TOKEN")

    @Test
    fun segredosCitaCadaVariavelSecreta() {
        val nomes = (variaveis(envExample) + variaveis(modelo)).map { it.first }.filter { ehSecreta.containsMatchIn(it) }.toSet()
        assertTrue(nomes.containsAll(listOf("SESSION_SECRET", "OIDC_CLIENT_SECRET", "JIRA_SAMSUNG_TOKEN", "JIRA_SIDIA_TOKEN")), "$nomes")
        for (nome in nomes) assertTrue(nome in segredosMd, "segredos.md não cita $nome")
    }

    @Test
    fun envExampleEModeloSemValorDeSegredo() {
        for ((arquivo, texto) in listOf("app/.env.example" to envExample, "painel.env.exemplo" to modelo)) {
            for ((nome, valor) in variaveis(texto)) {
                if (ehSecreta.containsMatchIn(nome)) assertEquals("", valor, "$arquivo: $nome não pode ter valor")
            }
        }
    }

    @Test
    fun modeloDeProducaoEhSeguro() {
        val v = ativas(modelo)
        assertEquals("false", v["DASHBOARD_DEV_MODE"])
        assertEquals("false", v["DASHBOARD_DEMO_DATA"])
        assertEquals("false", v["JIRA_LIVE_ENABLED"], "o modelo nunca liga o Jira")
        assertEquals("127.0.0.1", v["PAINEL_HOST"])
        assertEquals("127.0.0.1/32,::1/128", v["DASHBOARD_PROXY_CONFIAVEL"])
        assertEquals("", v["SESSION_SECRET"])
        assertEquals("", v["OIDC_CLIENT_SECRET"])
        for (m in Regex("https?://([^/\\s:?#]+)").findAll(modelo)) assertTrue(m.groupValues[1].endsWith(".invalid"), "host real no modelo: ${m.value}")
        // caminhos absolutos (a produção recusa relativo)
        for (nome in listOf("DATABASE_PATH", "PAINEL_CONFIG_DIR", "PAINEL_STATIC_DIR")) {
            val valor = v[nome] ?: error("falta $nome")
            assertTrue(Regex("^([A-Za-z]:\\\\|/)").containsMatchIn(valor), "$nome deve ser absoluto: $valor")
        }
    }

    @Test
    fun modeloDeProducaoPassaNasGuardasDeProducao() {
        // O modelo, com os dois segredos preenchidos, tem de passar nas guardas (nenhum valor do modelo é recusado).
        val env = ativas(modelo) + mapOf(
            "SESSION_SECRET" to Base64.getEncoder().encodeToString(ByteArray(48) { it.toByte() }),
            "OIDC_CLIENT_SECRET" to "x",
        )
        val settings = br.sidia.painel.config.Settings.fromEnv(env)
        // Os caminhos do modelo são do Windows; num Linux de teste só a conferência de escuta, proxy, https e sessão vale.
        val codigos = try {
            br.sidia.painel.config.GuardasDeProducao.verificar(settings, env)
            emptyList()
        } catch (e: br.sidia.painel.config.ViolacaoDeProducao) {
            e.codigos
        }
        assertEquals(emptyList(), codigos - "CAMINHO_RELATIVO", "o modelo de produção viola uma guarda: $codigos")
    }

    @Test
    fun nenhumModeloOuExemploDefineOAceiteDoDpoVerdadeiro() {
        val arquivos = listOf(
            "deploy/modelos/painel.env.exemplo", "app/.env.example", "app/scripts/LEIA-ME-pacote.txt", "docs/implantacao.md",
            "docs/implantacao/segredos.md", "docs/implantacao/atualizacao.md", "docs/implantacao/reversao.md",
        )
        for (a in arquivos) {
            assertFalse(Regex("(?i)DASHBOARD_DPO_ACEITE\\s*=\\s*true").containsMatchIn(ler(a)), "$a define o aceite do DPO como verdadeiro")
        }
        assertEquals("false", ativas(modelo)["DASHBOARD_DPO_ACEITE"], "o modelo declara a trava fechada")
        assertEquals("", ativas(modelo)["DASHBOARD_DPO_ACEITE_REF"])
    }

    @Test
    fun segredosApontaRotacao() {
        assertTrue("docs/operacao/rotacao-de-segredos.md" in segredosMd)
        assertTrue(Files.isRegularFile(repo.resolve("docs/operacao/rotacao-de-segredos.md")))
        assertTrue("não existe senha de banco" in segredosMd.lowercase() || "nao existe senha de banco" in segredosMd.lowercase())
    }

    @Test
    fun gitignoreCobrePainelEnv() {
        assertTrue("**/painel.env" in ler(".gitignore").lines().map { it.trim() })
    }

    @Test
    fun aclDoArquivoDeVariaveis() {
        val ps = ler("deploy/windows/comum.ps1")
        assertTrue("*S-1-5-32-544" in ps, "administradores por SID")
        for (sid in listOf("*S-1-1-0", "*S-1-5-32-545", "*S-1-5-11")) assertTrue(sid in ps, "comum.ps1 não cita $sid")
        assertTrue("/inheritance:r" in ps && "icacls" in ps)
        val sh = ler("deploy/linux/comum.sh")
        assertTrue("chmod 640" in sh && "chown root:painel" in sh)
        for (doc in listOf(segredosMd)) {
            assertTrue("chmod 640" in doc && "*S-1-5-32-544" in doc && "icacls" in doc)
            for (sid in listOf("*S-1-1-0", "*S-1-5-32-545", "*S-1-5-11")) assertTrue(sid in doc, "segredos.md não cita $sid")
        }
    }

    private fun linhasComSegredo(arquivo: String, nomes: List<String>): List<String> =
        ler(arquivo).lines().filter { l -> nomes.any { n -> l.contains("\$$n") || l.contains("\${$n}") } }

    @Test
    fun geradorNaoImprimeOSegredo() {
        val ps = ler("deploy/windows/gerar-session-secret.ps1")
        val imprime = Regex("Write-(Host|Output|Verbose|Debug|Warning|Error)|\\becho\\b|\\bprintf\\b|Out-Host|\\|\\s*Out-")
        for (l in linhasComSegredo("deploy/windows/gerar-session-secret.ps1", listOf("novo", "atual", "bytes", "linhaAtual"))) {
            assertFalse(imprime.containsMatchIn(l), "gerar-session-secret.ps1 imprime o segredo: $l")
        }
        assertTrue("RandomNumberGenerator" in ps)
        assertTrue("SESSION_SECRET_GERADO" in ps)
        val sh = ler("deploy/linux/gerar-session-secret.sh")
        for (l in linhasComSegredo("deploy/linux/gerar-session-secret.sh", listOf("SEGREDO", "ATUAL"))) {
            // linha que menciona o valor só pode gravá-lo em arquivo (redirecionamento), nunca na tela
            assertTrue(!Regex("\\becho\\b|\\bprintf\\b").containsMatchIn(l) || ">>" in l || ">" in l && "\"\$TMP\"" in l, "gerar-session-secret.sh imprime o segredo: $l")
        }
        assertTrue("openssl rand -base64 48" in sh)
    }

    @Test
    fun verificarLogsNaoImprimeValor() {
        val ps = ler("deploy/windows/verificar-logs.ps1")
        val sh = ler("deploy/linux/verificar-logs.sh")
        for (t in listOf(ps, sh)) {
            assertTrue("LOG_LIMPO" in t && "LOG_COM_SEGREDO" in t)
            assertTrue("SECRET" in t && "PASSWORD" in t && "TOKEN" in t)
        }
        assertTrue("journalctl -u painel --no-pager" in sh)
        assertTrue("\\logs\\" in ps || "logs" in ps)
        val imprime = Regex("Write-(Host|Output)|Out-Host")
        for (l in ps.lines().filter { "\$valor" in it }) assertFalse(imprime.containsMatchIn(l), "verificar-logs.ps1 imprime o valor: $l")
    }

    @Test
    fun scriptsPowerShellDeSegredosSaoSoAscii() {
        for (n in listOf("comum.ps1", "gerar-session-secret.ps1", "verificar-logs.ps1")) {
            val bytes = Files.readAllBytes(repo.resolve("deploy/windows/$n"))
            assertTrue(bytes.none { it < 0 }, "$n tem caractere não ASCII")
            val t = String(bytes)
            assertTrue("Set-StrictMode -Version Latest" in t && "\$ErrorActionPreference = 'Stop'" in t, n)
        }
        for (n in listOf("comum.sh", "gerar-session-secret.sh", "verificar-logs.sh")) {
            val t = ler("deploy/linux/$n")
            assertTrue(t.startsWith("#!/bin/sh\n") && "\nset -eu\n" in t, n)
        }
    }

    // ---------------------------------------------------------------- execuções reais (sh)

    private fun sh(): String? = listOf("/bin/sh", "/usr/bin/sh").firstOrNull { Files.isExecutable(Path.of(it)) }

    private fun rodar(vararg comando: String): Pair<Int, String> {
        val p = ProcessBuilder(*comando).redirectErrorStream(true).start()
        val saida = p.inputStream.bufferedReader().readText()
        assertTrue(p.waitFor(60, TimeUnit.SECONDS), "travou: ${comando.toList()}")
        return p.exitValue() to saida
    }

    private fun temOpenssl() = listOf("/usr/bin/openssl", "/bin/openssl", "/usr/local/bin/openssl").any { Files.isExecutable(Path.of(it)) }

    @Test
    fun geradorGravaSemImprimirERecusaSobrescrever() {
        assumeTrue(sh() != null && temOpenssl(), "sem sh/openssl neste ambiente")
        val pasta = Files.createTempDirectory("painel-segredo-")
        try {
            val env = pasta.resolve("painel.env")
            Files.write(env, listOf("DASHBOARD_DEV_MODE=false", "SESSION_SECRET=", "OIDC_CLIENT_ID=painel"))
            val script = repo.resolve("deploy/linux/gerar-session-secret.sh").toString()
            val (c1, s1) = rodar(sh()!!, script, env.toString())
            assertEquals(0, c1, s1)
            assertTrue("SESSION_SECRET_GERADO" in s1)
            val linhas = Files.readAllLines(env)
            val valor = linhas.single { it.startsWith("SESSION_SECRET=") }.removePrefix("SESSION_SECRET=")
            assertTrue(Base64.getDecoder().decode(valor).size >= 32, "mínimo de 32 bytes")
            assertFalse(valor in s1, "o valor não pode sair na tela")
            assertTrue("OIDC_CLIENT_ID=painel" in linhas && "DASHBOARD_DEV_MODE=false" in linhas, "o resto do arquivo fica intacto")
            // segunda vez: já há valor, recusa e não altera
            val (c2, s2) = rodar(sh()!!, script, env.toString())
            assertEquals(3, c2, s2)
            assertFalse(valor in s2)
            assertEquals(valor, Files.readAllLines(env).single { it.startsWith("SESSION_SECRET=") }.removePrefix("SESSION_SECRET="))
            // rotação: o valor atual vai para SESSION_SECRET_ANTERIOR e um novo entra, sem imprimir nada
            val (c3, s3) = rodar(sh()!!, script, env.toString(), "--rotacionar")
            assertEquals(0, c3, s3)
            assertTrue("SESSION_SECRET_ROTACIONADO" in s3)
            val depois = Files.readAllLines(env)
            assertEquals(valor, depois.single { it.startsWith("SESSION_SECRET_ANTERIOR=") }.removePrefix("SESSION_SECRET_ANTERIOR="))
            val novo = depois.single { it.startsWith("SESSION_SECRET=") }.removePrefix("SESSION_SECRET=")
            assertTrue(novo != valor && Base64.getDecoder().decode(novo).size >= 32)
            assertFalse(novo in s3 || valor in s3)
        } finally {
            pasta.toFile().deleteRecursively()
        }
    }

    @Test
    fun verificarLogsAcusaOSegredoSemMostrarOValor() {
        assumeTrue(sh() != null, "sem sh neste ambiente")
        val pasta = Files.createTempDirectory("painel-logs-")
        try {
            val env = pasta.resolve("painel.env")
            val vazado = "valor-ficticio-vazado-0123456789"
            Files.writeString(env, "SESSION_SECRET=$vazado\nOIDC_CLIENT_SECRET=\nDASHBOARD_DEV_MODE=false\n")
            val script = repo.resolve("deploy/linux/verificar-logs.sh").toString()
            val limpo = pasta.resolve("limpo.log").also { Files.writeString(it, "tudo certo\nsem nada secreto\n") }
            val (c1, s1) = rodar(sh()!!, script, env.toString(), limpo.toString())
            assertEquals(0, c1, s1)
            assertTrue("LOG_LIMPO" in s1)
            val sujo = pasta.resolve("sujo.log").also { Files.writeString(it, "linha 1\ncabecalho $vazado fim\n") }
            val (c2, s2) = rodar(sh()!!, script, env.toString(), sujo.toString())
            assertEquals(1, c2, s2)
            assertTrue("LOG_COM_SEGREDO SESSION_SECRET" in s2)
            assertFalse(vazado in s2, "o valor não pode sair na tela")
        } finally {
            pasta.toFile().deleteRecursively()
        }
    }

    @Test
    fun verificacaoDePermissaoDoArquivoDeVariaveis() {
        assumeTrue(sh() != null, "sem sh neste ambiente")
        val pasta = Files.createTempDirectory("painel-perm-")
        try {
            val env = pasta.resolve("painel.env")
            Files.writeString(env, "A=1\n")
            val comum = repo.resolve("deploy/linux/comum.sh").toString()
            fun confere(modo: String): Int {
                rodar("chmod", modo, env.toString())
                return rodar(sh()!!, "-c", ". '$comum'; arquivo_de_variaveis_protegido '${env}'").first
            }
            assertEquals(0, confere("640"))
            assertEquals(0, confere("600"))
            assertEquals(0, confere("400"))
            assertTrue(confere("644") != 0, "legível por todos não é protegido")
            assertTrue(confere("660") != 0, "gravável pelo grupo não é protegido")
            assertTrue(confere("604") != 0, "legível por outros não é protegido")
        } finally {
            pasta.toFile().deleteRecursively()
        }
    }
}
