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
    fun avisoDeMetricasForaDoLoopbackApareceSemMudarOCodigo() {
        val env = dev + mapOf("PAINEL_METRICAS_PORTA" to "9100", "PAINEL_METRICAS_HOST" to "0.0.0.0")
        val (codigo, saida) = executar(listOf("validar-config", "--dir", TestPaths.configDir.toString()), env)
        assertEquals(0, codigo, saida.toString())
        assertTrue(saida.any { it.startsWith("AVISO METRICAS_HOST_NAO_LOOPBACK") }, saida.toString())
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

    private fun bancoComDados(): Path {
        val pasta = nova()
        val arquivo = pasta.resolve("painel.db")
        val ds = br.sidia.painel.db.Database.connect(br.sidia.painel.db.Database.urlDeArquivo(arquivo), poolSize = 1)
        br.sidia.painel.db.Database.migrate(ds)
        ds.connection.use { it.createStatement().execute("INSERT INTO issue(key, instance, project) VALUES ('TG-1', 'samsung', 'TG')") }
        (ds as AutoCloseable).close()
        return arquivo
    }

    @Test
    fun backupAgoraSemDirSaiCom2() {
        val (codigo, saida) = executar(listOf("backup-agora"), dev + ("DATABASE_PATH" to bancoComDados().toString()))
        assertEquals(2, codigo)
        assertTrue(saida.single().contains("BACKUP_DIR"), saida.toString())
    }

    @Test
    fun backupAgoraCopiaEImprimeNomeETamanho() {
        val banco = bancoComDados()
        val copias = nova()
        val (codigo, saida) = executar(listOf("backup-agora"), dev + mapOf("DATABASE_PATH" to banco.toString(), "BACKUP_DIR" to copias.toString()))
        assertEquals(0, codigo, saida.toString())
        val linha = saida.single()
        assertTrue(Regex("^BACKUP OK painel-\\d{4}-\\d{2}-\\d{2}-\\d{6}\\.db \\d+$").matches(linha), linha)
        assertFalse(copias.toString() in linha, "a saída não mostra o caminho")
        assertEquals(1, copias.listDirectoryEntries("*.db").size)
    }

    @Test
    fun backupAgoraComServicoRodando() {
        val banco = bancoComDados()
        val copias = nova()
        // O "serviço" está de pé: a trava do banco é dele. O subcomando não a pede e ainda copia.
        val trava = br.sidia.painel.db.TravaDeProcesso.adquirir(banco)
        try {
            val (codigo, saida) = executar(listOf("backup-agora"), dev + mapOf("DATABASE_PATH" to banco.toString(), "BACKUP_DIR" to copias.toString()))
            assertEquals(0, codigo, saida.toString())
            assertEquals(1, copias.listDirectoryEntries("*.db").size)
        } finally {
            trava.close()
        }
    }

    @Test
    fun verificarRestauracaoUsaACopiaMaisRecenteEListaAsTabelas() {
        val banco = bancoComDados()
        val copias = nova()
        val env = dev + mapOf("DATABASE_PATH" to banco.toString(), "BACKUP_DIR" to copias.toString())
        assertEquals(0, executar(listOf("backup-agora"), env).first)
        val (codigo, saida) = executar(listOf("verificar-restauracao"), env)
        assertEquals(0, codigo, saida.toString())
        assertTrue(Regex("^RESTAURACAO OK \\d+ tabelas$").matches(saida.first()), saida.first())
        assertTrue("issue 1" in saida, saida.toString())
        // só nomes de tabela e números: nenhum dado das linhas nem o caminho
        assertFalse(saida.joinToString("\n").contains("TG-1"))
        assertFalse(saida.joinToString("\n").contains(copias.toString()))
    }

    @Test
    fun verificarRestauracaoFuncionaSemBancoVivo() {
        val banco = bancoComDados()
        val copias = nova()
        val env = dev + mapOf("DATABASE_PATH" to banco.toString(), "BACKUP_DIR" to copias.toString())
        executar(listOf("backup-agora"), env)
        val copia = copias.listDirectoryEntries("*.db").single()
        // recuperação de desastre: o banco vivo foi para a quarentena
        val semBanco = dev + ("DATABASE_PATH" to nova().resolve("nao-existe.db").toString())
        val (codigo, saida) = executar(listOf("verificar-restauracao", copia.toString()), semBanco)
        assertEquals(0, codigo, saida.toString())
        assertTrue(saida.first().startsWith("RESTAURACAO OK"))
    }

    @Test
    fun verificarRestauracaoCopiaInvalidaSaiCom1() {
        val ruim = nova().resolve("painel-2026-10-30-020000.db")
        Files.writeString(ruim, "isto não é um banco SQLite")
        val (codigo, saida) = executar(listOf("verificar-restauracao", ruim.toString()))
        assertEquals(1, codigo)
        assertEquals(listOf("RESTAURACAO FALHOU INTEGRIDADE_FALHOU"), saida)
    }

    @Test
    fun verificarRestauracaoSemArquivoNemDirSaiCom2() {
        val (codigo, saida) = executar(listOf("verificar-restauracao"))
        assertEquals(2, codigo)
        assertTrue(saida.single().contains("BACKUP_DIR"))
        assertEquals(2, executar(listOf("verificar-restauracao", "a", "b")).first)
    }

    @Test
    fun backupAgoraNaoCriaBancoNovo() {
        val pasta = nova()
        val (codigo, saida) = executar(
            listOf("backup-agora"),
            dev + mapOf("DATABASE_PATH" to pasta.resolve("nao-existe.db").toString(), "BACKUP_DIR" to nova().toString()),
        )
        assertEquals(1, codigo)
        assertEquals(listOf("BACKUP FALHOU BANCO_NAO_ENCONTRADO"), saida)
        assertEquals(emptyList(), pasta.listDirectoryEntries())
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
        // O Windows PowerShell 5.1 lê .ps1 sem BOM como ANSI: o script é só ASCII. E Write-Error com 'Stop' sairia com 1, não 2.
        assertTrue(textoPs1.all { it.code < 128 }, "painel-cli.ps1 precisa ser só ASCII (ou ter BOM)")
        assertFalse("Write-Error" in textoPs1 && "exit 2" in textoPs1 && Regex("Write-Error[^\\n]*\\n\\s*exit 2").containsMatchIn(textoPs1))
    }
}
