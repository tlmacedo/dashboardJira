package br.sidia.painel.ci

import br.sidia.painel.TestPaths
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Pacote gerado por `installDist` (a tarefa `test` depende dele): scripts, jars, nada de configuração real nem banco,
 * e o perfil de produção fixado nos scripts. O teste de processo executa o script sem nenhuma variável.
 */
class PacoteInstalavelTest {
    private val pacote: Path = TestPaths.installDir

    private fun arquivos(): List<Path> = Files.walk(pacote).use { s -> s.filter { Files.isRegularFile(it) }.toList() }

    private fun libs(): List<String> = Files.list(pacote.resolve("lib")).use { s -> s.map { it.name }.toList() }

    @Test
    fun pacoteTemScriptsELibs() {
        assertTrue(Files.isRegularFile(pacote.resolve("bin/painel")))
        assertTrue(Files.isRegularFile(pacote.resolve("bin/painel.bat")))
        val jars = libs()
        assertTrue(jars.any { it.startsWith("painel-") && it.endsWith(".jar") }, "jar do painel")
        assertTrue(jars.any { it.startsWith("sqlite-jdbc") }, "sqlite-jdbc")
        assertTrue(jars.any { it.startsWith("flyway-core") }, "flyway-core")
    }

    @Test
    fun migracoesVaoDentroDoJar() {
        val jar = Files.list(pacote.resolve("lib")).use { s ->
            s.filter { it.name.startsWith("painel-") && it.name.endsWith(".jar") }.findFirst().orElseThrow()
        }
        ZipFile(jar.toFile()).use { zip ->
            val entradas = zip.entries().asSequence().map { it.name }.toSet()
            for (esperada in listOf("db/migration/V1__base.sql", "logback.xml", "demo/manifesto.yaml")) {
                assertTrue(esperada in entradas, "o jar não contém $esperada")
            }
        }
    }

    @Test
    fun pacoteNaoLevaConfigNemBanco() {
        val proibidos = listOf(
            Regex("^\\.env.*"), Regex(".*\\.db.*"),
            Regex("^(papeis|equipes|ausencias|squads|calendario|status_map|jira_campos|vinculos)\\.yaml$"),
        )
        val achados = arquivos().map { it.name }.filter { nome -> proibidos.any { it.matches(nome) } }
        assertEquals(emptyList(), achados)
    }

    @Test
    fun scriptsFixamOPerfilDeProducao() {
        for (script in listOf("bin/painel", "bin/painel.bat")) {
            assertTrue(pacote.resolve(script).readText().contains("-Dpainel.perfil=producao"), script)
        }
    }

    @Test
    fun pacoteSemVariaveisSobeEmModoProducaoEFalhaFechado() {
        val windows = System.getProperty("os.name").lowercase().contains("win")
        val pasta = Files.createTempDirectory("painel-pacote-")
        try {
            val comando = if (windows) {
                listOf("cmd", "/c", pacote.resolve("bin/painel.bat").toString())
            } else {
                listOf("sh", pacote.resolve("bin/painel").toString())
            }
            val pb = ProcessBuilder(comando).directory(pasta.toFile()).redirectErrorStream(true)
            // Ambiente limpo: nada de DASHBOARD_* nem JIRA_* herdado. O script só precisa de JAVA_HOME e PATH.
            val env = pb.environment()
            val path = env["PATH"] ?: env["Path"]
            env.clear()
            if (path != null) env["PATH"] = path
            env["JAVA_HOME"] = System.getProperty("java.home")
            env["PAINEL_ENV_FILE"] = pasta.resolve("nao-existe.env").toString()
            env["DATABASE_PATH"] = pasta.resolve("painel.db").toString()
            val processo = pb.start()
            val saida = StringBuilder()
            val leitor = Thread { processo.inputStream.bufferedReader().forEachLine { saida.appendLine(it) } }
            leitor.start()
            val terminou = processo.waitFor(60, TimeUnit.SECONDS)
            if (!terminou) processo.destroyForcibly()
            leitor.join(5_000)
            val texto = saida.toString()
            assertTrue(terminou, "o processo deveria falhar na subida, mas continuou rodando")
            assertNotEquals(0, processo.exitValue(), texto)
            // Sem LANG no ambiente a JVM escreve em ASCII e os acentos viram "?": a conferência ignora a acentuação.
            assertTrue(
                Regex("obrigat\\S+ em produ\\S+").containsMatchIn(texto),
                "falta a mensagem de configuração obrigatória: $texto",
            )
            assertFalse(texto.contains("MODO DESENVOLVIMENTO"), texto)
            assertFalse(texto.contains("Responding at"), "o servidor não pode ter chegado a escutar")
        } finally {
            pasta.toFile().deleteRecursively()
        }
    }
}
