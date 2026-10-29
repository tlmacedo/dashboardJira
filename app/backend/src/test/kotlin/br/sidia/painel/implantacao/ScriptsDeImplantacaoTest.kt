package br.sidia.painel.implantacao

import br.sidia.painel.TestPaths
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Plano 13, Tarefa 10: atualização e reversão por troca de pasta de versão. Conferências estáticas (PowerShell e sh) e execuções
 * reais dos scripts `sh` com pacotes de mentira, em pasta temporária, sem serviço (`--sem-servico`).
 */
class ScriptsDeImplantacaoTest {
    private val repo: Path = TestPaths.repoDir
    private fun ler(relativo: String) = Files.readString(repo.resolve(relativo))

    private val ps = listOf("atualizar.ps1", "reverter.ps1", "ensaio-troca.ps1", "comum.ps1").map { "deploy/windows/$it" }
    private val sh = listOf("atualizar.sh", "reverter.sh", "ensaio-troca.sh", "comum.sh").map { "deploy/linux/$it" }

    /** Posição da primeira linha que NÃO é comentário e contém [marca]; falha o teste se não houver. */
    private fun posicao(texto: String, marca: String): Int {
        var acumulado = 0
        for (linha in texto.lines()) {
            if (!linha.trimStart().startsWith("#") && marca in linha) return acumulado
            acumulado += linha.length + 1
        }
        error("o script não chama $marca")
    }

    @Test
    fun atualizarVerificaSha256AntesDeExtrair() {
        val a = ler("deploy/windows/atualizar.ps1")
        assertTrue(posicao(a, "Confirmar-Sha256") < posicao(a, "Expandir-Pacote"))
        val s = ler("deploy/linux/atualizar.sh")
        assertTrue(posicao(s, "confirmar_sha256") < posicao(s, "expandir_pacote"))
    }

    @Test
    fun atualizarParaAntesDeTrocar() {
        val a = ler("deploy/windows/atualizar.ps1")
        val ordemPs = listOf("Parar-Painel", "Fazer-Backup", "Trocar-Atual", "Iniciar-Painel", "Conferir-Fumaca").map { posicao(a, it) }
        assertEquals(ordemPs.sorted(), ordemPs, "ordem no .ps1: parar < backup < trocar atual < iniciar < fumaça")
        val s = ler("deploy/linux/atualizar.sh")
        val ordemSh = listOf("parar_painel", "fazer_backup", "trocar_atual", "iniciar_painel", "conferir_fumaca").map { posicao(s, it) }
        assertEquals(ordemSh.sorted(), ordemSh, "ordem no .sh: parar < backup < trocar atual < iniciar < fumaça")
        // o backup vem do mecanismo do Plano 12
        assertTrue("backup-agora" in ler("deploy/windows/comum.ps1") && "backup-agora" in ler("deploy/linux/comum.sh"))
    }

    @Test
    fun atualizarNaoSobrescrevePastaDeVersao() {
        assertTrue("já existe" in ler("deploy/linux/comum.sh") || "ja existe" in ler("deploy/linux/comum.sh"))
        assertTrue("ja existe" in ler("deploy/windows/comum.ps1"))
        // extrai primeiro numa pasta .tmp e só então renomeia
        assertTrue(".tmp" in ler("deploy/linux/comum.sh") && ".tmp" in ler("deploy/windows/comum.ps1"))
    }

    @Test
    fun atualizarNaoRevertePorConta() {
        for (n in listOf("deploy/windows/atualizar.ps1", "deploy/linux/atualizar.sh")) {
            val texto = ler(n)
            // toda linha que cita o script de reversão só pode IMPRIMI-LO (a reversão é decisão humana, depois de migração)
            for (linha in texto.lines().filter { !it.trimStart().startsWith("#") && Regex("reverter\\.(ps1|sh)").containsMatchIn(it) }) {
                assertTrue(Regex("echo|Write-Host|Console\\]::Error").containsMatchIn(linha), "$n executa a reversão: $linha")
            }
            assertTrue("FALHOU: execute reverter" in texto, "$n imprime o comando exato da reversão")
        }
    }

    @Test
    fun reverterCitaRestaurarBanco() {
        assertTrue("-RestaurarBanco" in ler("deploy/windows/reverter.ps1") && "-Backup" in ler("deploy/windows/reverter.ps1"))
        assertTrue("--restaurar-banco" in ler("deploy/linux/reverter.sh") && "--backup" in ler("deploy/linux/reverter.sh"))
        for (n in listOf("deploy/windows/reverter.ps1", "deploy/linux/reverter.sh")) {
            // a cópia é conferida (verificar-restauracao do Plano 12) antes de tocar no banco atual
            val t = ler(n)
            assertTrue(posicao(t, "verificar-restauracao") < posicao(t, "migrado"), "$n: conferir a cópia antes de guardar o banco atual")
        }
    }

    @Test
    fun reverterGuardaOBancoMigrado() {
        for (n in listOf("deploy/windows/reverter.ps1", "deploy/linux/reverter.sh")) {
            val t = ler(n)
            assertTrue(".migrado-" in t, "$n guarda o banco atual como painel.db.migrado-<data>")
            assertTrue("-wal" in t && "-shm" in t, "$n move também os arquivos -wal e -shm")
            // nunca apaga o banco
            assertFalse(Regex("(?i)Remove-Item[^\\n]*\\.db|rm [^\\n]*\\.db").containsMatchIn(t), "$n apaga arquivo de banco")
        }
    }

    @Test
    fun ensaioUsaSoPastaTemporaria() {
        val p = ler("deploy/windows/ensaio-troca.ps1")
        assertTrue(Regex("GetTempPath|TEMP|New-TemporaryFile").containsMatchIn(p) && "finally" in p && "Remove-Item" in p)
        val s = ler("deploy/linux/ensaio-troca.sh")
        assertTrue("mktemp -d" in s && "trap" in s && "rm -rf" in s)
    }

    @Test
    fun scriptsSemHostFixoESemDocker() {
        for (n in ps + sh) {
            val t = ler(n)
            for (m in Regex("https?://([^/\\s:?#\"')]+)").findAll(t)) {
                assertTrue(m.groupValues[1] in setOf("127.0.0.1", "localhost") || m.groupValues[1].endsWith(".invalid"), "$n: host fixo ${m.value}")
            }
            assertFalse(Regex("(?i)docker|kubectl|psql").containsMatchIn(t), "$n: plataforma proibida")
            assertFalse(Regex("(?i)JIRA_LIVE_ENABLED\\s*=\\s*true").containsMatchIn(t), "$n liga a flag do Jira")
        }
    }

    @Test
    fun scriptsPowerShellSaoAsciiEEstritos() {
        for (n in ps) {
            val bytes = Files.readAllBytes(repo.resolve(n))
            assertTrue(bytes.none { it < 0 }, "$n tem caractere não ASCII")
            val t = String(bytes)
            assertTrue("Set-StrictMode -Version Latest" in t && "\$ErrorActionPreference = 'Stop'" in t, n)
        }
        for (n in sh) {
            val t = ler(n)
            assertTrue(t.startsWith("#!/bin/sh\n") && "\nset -eu\n" in t, n)
        }
    }

    @Test
    fun scriptsShTemSintaxeValida() {
        val shell = shell()
        assumeTrue(shell != null, "sem sh neste ambiente")
        for (n in sh) {
            val (codigo, saida) = rodar(listOf(shell!!, "-n", repo.resolve(n).toString()))
            assertEquals(0, codigo, "$n: $saida")
        }
    }

    // ---------------------------------------------------------------- execuções reais (sh, sem serviço)

    private fun shell(): String? = listOf("/bin/sh", "/usr/bin/sh").firstOrNull { Files.isExecutable(Path.of(it)) }

    private fun temFerramentas(): Boolean =
        shell() != null && listOf("/usr/bin/unzip", "/bin/unzip").any { Files.isExecutable(Path.of(it)) } &&
            listOf("/usr/bin/sha256sum", "/bin/sha256sum", "/usr/bin/shasum").any { Files.isExecutable(Path.of(it)) }

    private fun rodar(comando: List<String>, ambiente: Map<String, String> = emptyMap()): Pair<Int, String> {
        val pb = ProcessBuilder(comando).redirectErrorStream(true)
        pb.environment().putAll(ambiente)
        val p = pb.start()
        val saida = p.inputStream.bufferedReader().readText()
        assertTrue(p.waitFor(120, TimeUnit.SECONDS), "travou: $comando")
        return p.exitValue() to saida
    }

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /** Pacote de mentira `painel-<versao>.zip` (+ `.sha256`) com `bin/painel` de marcador, numa pasta. */
    private fun pacoteDeMentira(pasta: Path, versao: String, comBin: Boolean = true, hashErrado: Boolean = false): Path {
        val zip = pasta.resolve("painel-$versao.zip")
        ZipOutputStream(Files.newOutputStream(zip)).use { z ->
            z.putNextEntry(ZipEntry("painel-$versao/VERSAO.txt")); z.write("versao=$versao\n".toByteArray()); z.closeEntry()
            if (comBin) {
                z.putNextEntry(ZipEntry("painel-$versao/bin/painel")); z.write("#!/bin/sh\necho $versao\n".toByteArray()); z.closeEntry()
            }
        }
        val h = if (hashErrado) "0".repeat(64) else sha256(Files.readAllBytes(zip))
        Files.writeString(pasta.resolve("painel-$versao.zip.sha256"), "$h  painel-$versao.zip\n")
        return zip
    }

    private fun ambiente(dados: Path) = mapOf(
        "PAINEL_DADOS" to dados.toString(), "PAINEL_ENV_FILE" to dados.resolve("painel.env").toString(),
    )

    private fun comPasta(bloco: (Path) -> Unit) {
        val pasta = Files.createTempDirectory("painel-atualizar-")
        try {
            bloco(pasta)
        } finally {
            pasta.toFile().deleteRecursively()
        }
    }

    private val atualizar get() = repo.resolve("deploy/linux/atualizar.sh").toString()
    private val reverter get() = repo.resolve("deploy/linux/reverter.sh").toString()

    @Test
    fun ensaioDeTrocaPassa() {
        assumeTrue(temFerramentas(), "sem sh/unzip/sha256sum neste ambiente")
        val (codigo, saida) = rodar(listOf(shell()!!, repo.resolve("deploy/linux/ensaio-troca.sh").toString()))
        assertEquals(0, codigo, saida)
        assertTrue("TROCA_OK" in saida && "REVERSAO_OK" in saida, saida)
    }

    @Test
    fun atualizarTrocaAtualEGuardaAVersaoAnterior() = comPasta { pasta ->
        assumeTrue(temFerramentas(), "sem sh/unzip/sha256sum neste ambiente")
        val raiz = pasta.resolve("app").also { Files.createDirectories(it) }
        val dados = pasta.resolve("dados").also { Files.createDirectories(it) }
        val env = ambiente(dados)
        val recebidos = pasta.resolve("recebidos").also { Files.createDirectories(it) }
        assertEquals(0, rodar(listOf(shell()!!, atualizar, raiz.toString(), pacoteDeMentira(recebidos, "2026.11.1").toString(), "--sem-servico"), env).first)
        assertEquals("2026.11.1", Files.readSymbolicLink(raiz.resolve("atual")).fileName.toString())
        val (c2, s2) = rodar(listOf(shell()!!, atualizar, raiz.toString(), pacoteDeMentira(recebidos, "2026.11.2").toString(), "--sem-servico"), env)
        assertEquals(0, c2, s2)
        assertEquals("2026.11.2", Files.readSymbolicLink(raiz.resolve("atual")).fileName.toString())
        assertTrue("versao=2026.11.1" in Files.readString(dados.resolve("versao-anterior.txt")))
        // a versão anterior continua intacta ao lado
        assertTrue(Files.isRegularFile(raiz.resolve("2026.11.1/bin/painel")))
        val (c3, s3) = rodar(listOf(shell()!!, reverter, raiz.toString(), "--sem-servico"), env)
        assertEquals(0, c3, s3)
        assertEquals("2026.11.1", Files.readSymbolicLink(raiz.resolve("atual")).fileName.toString())
    }

    @Test
    fun atualizarComChecksumErradoNaoExtraiNada() = comPasta { pasta ->
        assumeTrue(temFerramentas(), "sem sh/unzip/sha256sum neste ambiente")
        val raiz = pasta.resolve("app").also { Files.createDirectories(it) }
        val dados = pasta.resolve("dados").also { Files.createDirectories(it) }
        val zip = pacoteDeMentira(pasta, "2026.11.1", hashErrado = true)
        val (codigo, saida) = rodar(listOf(shell()!!, atualizar, raiz.toString(), zip.toString(), "--sem-servico"), ambiente(dados))
        assertTrue(codigo != 0, saida)
        assertTrue("SHA-256" in saida, saida)
        assertEquals(emptyList(), Files.list(raiz).use { it.toList() }, "nada foi extraído")
    }

    @Test
    fun atualizarNaoSobrescrevePastaDeVersaoJaExistente() = comPasta { pasta ->
        assumeTrue(temFerramentas(), "sem sh/unzip/sha256sum neste ambiente")
        val raiz = pasta.resolve("app").also { Files.createDirectories(it) }
        val dados = pasta.resolve("dados").also { Files.createDirectories(it) }
        val env = ambiente(dados)
        val zip = pacoteDeMentira(pasta, "2026.11.1")
        assertEquals(0, rodar(listOf(shell()!!, atualizar, raiz.toString(), zip.toString(), "--sem-servico"), env).first)
        val marcador = raiz.resolve("2026.11.1/NAO_TOCAR.txt")
        Files.writeString(marcador, "intacto")
        val (codigo, saida) = rodar(listOf(shell()!!, atualizar, raiz.toString(), zip.toString(), "--sem-servico"), env)
        assertTrue(codigo != 0 && "já existe" in saida, saida)
        assertEquals("intacto", Files.readString(marcador), "a versão em uso não pode ser tocada")
    }

    @Test
    fun pacoteSemBinNaoViraVersao() = comPasta { pasta ->
        assumeTrue(temFerramentas(), "sem sh/unzip/sha256sum neste ambiente")
        val raiz = pasta.resolve("app").also { Files.createDirectories(it) }
        val dados = pasta.resolve("dados").also { Files.createDirectories(it) }
        val zip = pacoteDeMentira(pasta, "2026.11.1", comBin = false)
        val (codigo, saida) = rodar(listOf(shell()!!, atualizar, raiz.toString(), zip.toString(), "--sem-servico"), ambiente(dados))
        assertTrue(codigo != 0, saida)
        assertEquals(emptyList(), Files.list(raiz).use { it.toList() }, "nem a pasta .tmp pode sobrar")
    }

    @Test
    fun reverterSemVersaoAnteriorRecusa() = comPasta { pasta ->
        assumeTrue(temFerramentas(), "sem sh/unzip/sha256sum neste ambiente")
        val raiz = pasta.resolve("app").also { Files.createDirectories(it) }
        val dados = pasta.resolve("dados").also { Files.createDirectories(it) }
        val (codigo, saida) = rodar(listOf(shell()!!, reverter, raiz.toString(), "--sem-servico"), ambiente(dados))
        assertTrue(codigo != 0 && "versao-anterior" in saida, saida)
    }
}
