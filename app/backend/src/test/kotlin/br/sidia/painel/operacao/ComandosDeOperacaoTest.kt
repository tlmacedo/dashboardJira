package br.sidia.painel.operacao

import br.sidia.painel.TestPaths
import org.junit.jupiter.api.AfterEach
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteRecursively
import kotlin.io.path.listDirectoryEntries
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(kotlin.io.path.ExperimentalPathApi::class)
class ComandosDeOperacaoTest {
    private val temporarias = mutableListOf<Path>()

    @AfterEach
    fun limpar() {
        temporarias.forEach { runCatching { it.deleteRecursively() } }
    }

    private fun nova(): Path = Files.createTempDirectory("painel-cmd-").also { temporarias.add(it) }

    private val dev = mapOf("DASHBOARD_DEV_MODE" to "true")

    private fun executar(args: List<String>, env: Map<String, String> = dev): Pair<Int?, List<String>> {
        val linhas = mutableListOf<String>()
        val codigo = comandoDeOperacao(args, env) { linhas.add(it) }
        return codigo to linhas
    }

    @Test
    fun semSubcomandoNaoInterceptaASubida() {
        assertNull(comandoDeOperacao(emptyList(), dev) { })
    }

    @Test
    fun codigosDeSaida() {
        val (ok, saidaOk) = executar(listOf("validar-config", "--dir", TestPaths.configDir.toString()))
        assertEquals(0, ok)
        assertTrue("VALIDACAO OK" in saidaOk)
        assertTrue(saidaOk.any { it.startsWith("OK ") })

        val vazia = nova()
        val (falha, saidaFalha) = executar(listOf("validar-config", "--dir", vazia.toString()))
        assertEquals(1, falha)
        assertTrue(saidaFalha.any { it.startsWith("ERRO ARQUIVO_AUSENTE ") }, saidaFalha.toString())
        assertTrue(saidaFalha.last().startsWith("VALIDACAO FALHOU "), saidaFalha.toString())

        assertEquals(2, executar(listOf("xpto")).first)
        assertEquals(2, executar(listOf("validar-config", "--dir")).first)
        assertEquals(2, executar(listOf("validar-config", "--inexistente")).first)
    }

    @Test
    fun usaPainelConfigDirQuandoNaoHaDir() {
        val (codigo, _) = executar(listOf("validar-config"), dev + ("PAINEL_CONFIG_DIR" to TestPaths.configDir.toString()))
        assertEquals(0, codigo)
    }

    @Test
    fun erroSemValorNoTexto() {
        val pasta = nova()
        Files.walk(TestPaths.configDir).use { arvore ->
            arvore.filter { Files.isRegularFile(it) }.forEach {
                val alvo = pasta.resolve(TestPaths.configDir.relativize(it).toString())
                Files.createDirectories(alvo.parent)
                Files.copy(it, alvo)
            }
        }
        Files.writeString(
            pasta.resolve("ausencias.yaml"),
            "ausencias:\n  - pessoa: joao.ficticio.x\n    de: 2026-11-02\n    ate: 2026-11-06\n    fracao: 1.0\n",
        )
        val (codigo, saida) = executar(listOf("validar-config", "--dir", pasta.toString()))
        assertEquals(1, codigo)
        assertTrue(saida.any { it == "ERRO PESSOA_DESCONHECIDA ausencias.yaml:2" }, saida.toString())
        assertFalse(saida.joinToString("\n").contains("joao.ficticio.x"))
    }

    @Test
    fun naoAbreBancoNemCriaTrava() {
        val dados = nova()
        val env = dev + ("DATABASE_PATH" to dados.resolve("painel.db").toString())
        val (codigo, _) = executar(listOf("validar-config", "--dir", TestPaths.configDir.toString()), env)
        assertEquals(0, codigo)
        assertEquals(emptyList(), dados.listDirectoryEntries(), "nenhum arquivo de banco nem .lock pode nascer")
    }

    @Test
    fun scriptsDeCliExistemERepassamOsArgumentosSemDocker() {
        val sh = TestPaths.repoDir.resolve("app/scripts/painel-cli.sh")
        val ps1 = TestPaths.repoDir.resolve("app/scripts/painel-cli.ps1")
        assertTrue(Files.isRegularFile(sh) && Files.isRegularFile(ps1))
        val textoSh = Files.readString(sh)
        val textoPs1 = Files.readString(ps1)
        assertTrue("\"\$@\"" in textoSh, "o sh deve repassar \"\$@\"")
        assertTrue("@args" in textoPs1, "o ps1 deve repassar @args")
        assertTrue("bin/painel" in textoSh && "bin\\painel.bat" in textoPs1)
        for (t in listOf(textoSh, textoPs1)) assertFalse(t.contains("docker", ignoreCase = true))
    }
}
