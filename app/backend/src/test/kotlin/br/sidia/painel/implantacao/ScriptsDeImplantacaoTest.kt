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
        Ferramentas.exigir(shell != null, "sem sh neste ambiente")
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
        Ferramentas.exigir(temFerramentas(), "sem sh/unzip/sha256sum neste ambiente")
        val (codigo, saida) = rodar(listOf(shell()!!, repo.resolve("deploy/linux/ensaio-troca.sh").toString()))
        assertEquals(0, codigo, saida)
        assertTrue("TROCA_OK" in saida && "REVERSAO_OK" in saida && "TOCTOU_OK" in saida, saida)
    }

    @Test
    fun atualizarTrocaAtualEGuardaAVersaoAnterior() = comPasta { pasta ->
        Ferramentas.exigir(temFerramentas(), "sem sh/unzip/sha256sum neste ambiente")
        val raiz = pasta.resolve("app").also { Files.createDirectories(it) }
        val dados = pasta.resolve("dados").also { Files.createDirectories(it) }
        val env = ambiente(dados)
        val recebidos = pasta.resolve("recebidos").also { Files.createDirectories(it) }
        assertEquals(0, rodar(listOf(shell()!!, atualizar, raiz.toString(), pacoteDeMentira(recebidos, "2026.11.1").toString(), "--sem-servico"), env).first)
        assertEquals("2026.11.1", Files.readSymbolicLink(raiz.resolve("atual")).fileName.toString())
        val (c2, s2) = rodar(listOf(shell()!!, atualizar, raiz.toString(), pacoteDeMentira(recebidos, "2026.11.2").toString(), "--sem-servico"), env)
        assertEquals(0, c2, s2)
        assertEquals("2026.11.2", Files.readSymbolicLink(raiz.resolve("atual")).fileName.toString())
        assertTrue("versao=2026.11.1" in Files.readString(raiz.resolve("versao-anterior.txt")))
        // a versão anterior continua intacta ao lado
        assertTrue(Files.isRegularFile(raiz.resolve("2026.11.1/bin/painel")))
        val (c3, s3) = rodar(listOf(shell()!!, reverter, raiz.toString(), "--sem-servico"), env)
        assertEquals(0, c3, s3)
        assertEquals("2026.11.1", Files.readSymbolicLink(raiz.resolve("atual")).fileName.toString())
    }

    @Test
    fun atualizarComChecksumErradoNaoExtraiNada() = comPasta { pasta ->
        Ferramentas.exigir(temFerramentas(), "sem sh/unzip/sha256sum neste ambiente")
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
        Ferramentas.exigir(temFerramentas(), "sem sh/unzip/sha256sum neste ambiente")
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
        Ferramentas.exigir(temFerramentas(), "sem sh/unzip/sha256sum neste ambiente")
        val raiz = pasta.resolve("app").also { Files.createDirectories(it) }
        val dados = pasta.resolve("dados").also { Files.createDirectories(it) }
        val zip = pacoteDeMentira(pasta, "2026.11.1", comBin = false)
        val (codigo, saida) = rodar(listOf(shell()!!, atualizar, raiz.toString(), zip.toString(), "--sem-servico"), ambiente(dados))
        assertTrue(codigo != 0, saida)
        assertEquals(emptyList(), Files.list(raiz).use { it.toList() }, "nem a pasta .tmp pode sobrar")
    }

    // ---------------------------------------------------------------- I4: o registro da versao anterior nao decide o que root executa

    /** Pasta com a versão boa (`2026.11.1`), uma pasta FORA da raiz com um `bin/painel` que deixa um marcador, e o painel.env. */
    private class CenarioDeReversao(val raiz: Path, val marcador: Path, val env: Map<String, String>, val pasta: Path)

    private fun montarCenarioDeReversao(pasta: Path, registro: String): CenarioDeReversao {
        val raiz = pasta.resolve("app").also { Files.createDirectories(it) }
        val marcador = pasta.resolve("EXECUTOU")
        fun painelQueMarca(dir: Path) {
            Files.createDirectories(dir.resolve("bin"))
            Files.writeString(dir.resolve("bin/painel"), "#!/bin/sh\necho x > '$marcador'\necho RESTAURACAO OK\n")
            dir.resolve("bin/painel").toFile().setExecutable(true)
        }
        painelQueMarca(raiz.resolve("2026.11.1"))
        painelQueMarca(pasta.resolve("outro"))
        painelQueMarca(raiz.resolve("com-link-alvo"))
        Files.createSymbolicLink(raiz.resolve("2026.11.2"), pasta.resolve("outro"))
        val backups = pasta.resolve("backups").also { Files.createDirectories(it) }
        Files.writeString(backups.resolve("painel-bom.db"), "copia")
        Files.writeString(pasta.resolve("fora.db"), "copia")
        val banco = pasta.resolve("dados/painel.db")
        Files.createDirectories(banco.parent)
        val envFile = pasta.resolve("painel.env")
        Files.write(envFile, listOf("BACKUP_DIR=$backups", "DATABASE_PATH=$banco"))
        Files.writeString(raiz.resolve("versao-anterior.txt"), registro)
        // O reverter roda o painel e a restauração com a identidade do serviço; nos testes é o próprio usuário que os executa.
        return CenarioDeReversao(
            raiz, marcador, mapOf("PAINEL_ENV_FILE" to envFile.toString(), "PAINEL_USUARIO" to System.getProperty("user.name")), pasta,
        )
    }

    private fun reverterComRegistro(registro: String, vararg extras: String): Triple<Int, String, Boolean> {
        val pasta = Files.createTempDirectory("painel-reversao-")
        try {
            val c = montarCenarioDeReversao(pasta, registro)
            val (codigo, saida) = rodar(listOf(shell()!!, reverter, c.raiz.toString(), "--restaurar-banco", "--sem-servico") + extras, c.env)
            return Triple(codigo, saida, Files.exists(c.marcador))
        } finally {
            pasta.toFile().deleteRecursively()
        }
    }

    @Test
    fun registroComVersaoMaliciosaNuncaExecutaNada() {
        Ferramentas.exigir(shell() != null, "sem sh")
        for (versao in listOf("../outro", "../../tmp/x", "/tmp/x", "2026.11.1 x", "-x", "2026.13.1", "2026.11.0", "2026.11.1/../../outro", "2026.11", "", "com-link-alvo")) {
            val (codigo, saida, executou) = reverterComRegistro("versao=$versao\nbackup=painel-bom.db\n")
            assertTrue(codigo != 0, "versao='$versao': $saida")
            assertFalse(executou, "versao='$versao' executou um bin/painel: $saida")
        }
    }

    @Test
    fun versaoComBinQueELinkOuBackupQueELinkNaoEExecutada() {
        Ferramentas.exigir(shell() != null, "sem sh")
        // N1: a pasta é própria, mas "bin" é um link para fora
        run {
            val pasta = Files.createTempDirectory("painel-reversao-")
            try {
                val c = montarCenarioDeReversao(pasta, "versao=2026.11.3\nbackup=painel-bom.db\n")
                val v = c.raiz.resolve("2026.11.3").also { Files.createDirectories(it) }
                Files.createSymbolicLink(v.resolve("bin"), pasta.resolve("outro/bin"))
                val (codigo, saida) = rodar(listOf(shell()!!, reverter, c.raiz.toString(), "--restaurar-banco", "--sem-servico"), c.env)
                assertTrue(codigo != 0, saida)
                assertFalse(Files.exists(c.marcador), "bin que é link não pode ser executado: $saida")
            } finally {
                pasta.toFile().deleteRecursively()
            }
        }
        // N2: a cópia de backup é um link simbólico (mesmo para dentro de BACKUP_DIR)
        run {
            val pasta = Files.createTempDirectory("painel-reversao-")
            try {
                val c = montarCenarioDeReversao(pasta, "versao=2026.11.1\nbackup=painel-link.db\n")
                Files.createSymbolicLink(pasta.resolve("backups/painel-link.db"), pasta.resolve("backups/painel-bom.db"))
                val (codigo, saida) = rodar(listOf(shell()!!, reverter, c.raiz.toString(), "--restaurar-banco", "--sem-servico"), c.env)
                assertTrue(codigo != 0 && "link" in saida, saida)
                assertFalse(Files.exists(c.marcador), "cópia que é link não pode chegar à conferência: $saida")
            } finally {
                pasta.toFile().deleteRecursively()
            }
        }
    }

    @Test
    fun powerShellTambemRecusaBinELinkECopiaLink() {
        val c = ler("deploy/windows/comum.ps1")
        assertTrue(Regex("(?s)function Test-PastaDaVersaoValida.*?bin.*?ReparsePoint").containsMatchIn(c))
        assertTrue("(Join-Path \$pasta 'bin')" in c, "o .ps1 confere o próprio bin")
    }

    @Test
    fun registroApontandoParaPastaComLinkNaoEExecutado() {
        Ferramentas.exigir(shell() != null, "sem sh")
        val (codigo, saida, executou) = reverterComRegistro("versao=2026.11.2\nbackup=painel-bom.db\n")
        assertTrue(codigo != 0, saida)
        assertFalse(executou, saida)
    }

    @Test
    fun registroComBackupMaliciosoNaoExecutaNada() {
        Ferramentas.exigir(shell() != null, "sem sh")
        for (backup in listOf("../fora.db", "/etc/passwd", "-x", "a b.db", "..", "painel-bom.db/../../fora.db", "a\\b.db", "sub/painel-bom.db", "")) {
            val (codigo, saida, executou) = reverterComRegistro("versao=2026.11.1\nbackup=$backup\n")
            assertTrue(codigo != 0, "backup='$backup': $saida")
            assertFalse(executou, "backup='$backup' chegou a executar o painel: $saida")
        }
    }

    @Test
    fun backupInformadoNaLinhaDeComandoFicaDentroDeBackupDir() {
        Ferramentas.exigir(shell() != null, "sem sh")
        for (arg in listOf("-x", "../fora.db", "/tmp/qualquer.db", "backups/../../fora.db")) {
            val (codigo, saida, executou) = reverterComRegistro("versao=2026.11.1\n", "--backup", arg)
            assertTrue(codigo != 0, "--backup '$arg': $saida")
            assertFalse(executou, "--backup '$arg' chegou a executar o painel: $saida")
        }
    }

    @Test
    fun trocaDaCopiaPorLinkNoMeioDaReversaoNaoLevaSegredoParaOBanco() {
        Ferramentas.exigir(shell() != null, "sem sh")
        val pasta = Files.createTempDirectory("painel-toctou-")
        try {
            val c = montarCenarioDeReversao(pasta, "versao=2026.11.1\nbackup=painel-bom.db\n")
            val original = pasta.resolve("backups/painel-bom.db")
            val segredo = pasta.resolve("SEGREDO_DO_ROOT").also { Files.writeString(it, "SEGREDO_DO_ROOT conteudo sensivel") }
            // O painel "da versão anterior" (executado por verificar-restauracao) troca a cópia original por um link para o
            // segredo DEPOIS que o reverter a validou: simula a conta do serviço agindo dentro da janela.
            val painel = c.raiz.resolve("2026.11.1/bin/painel")
            Files.writeString(
                painel,
                "#!/bin/sh\nif [ \"\$1\" = verificar-restauracao ]; then rm -f '$original'; ln -s '$segredo' '$original'; echo RESTAURACAO OK; fi\n",
            )
            painel.toFile().setExecutable(true)
            val (codigo, saida) = rodar(listOf(shell()!!, reverter, c.raiz.toString(), "--restaurar-banco", "--sem-servico"), c.env)
            val banco = pasta.resolve("dados/painel.db")
            val conteudo = if (Files.exists(banco)) Files.readString(banco) else ""
            assertFalse("SEGREDO_DO_ROOT" in conteudo, "o conteúdo do segredo entrou no banco (código $codigo): $saida")
            assertEquals("copia", conteudo.trim(), "o banco deve vir da cópia validada, não do caminho reaberto: $saida")
            assertEquals(0, codigo, saida)
        } finally {
            pasta.toFile().deleteRecursively()
        }
    }

    // ---------------------------------------------------------------- L1: root nunca opera dentro da pasta do servico

    private fun souRootComUsuarioNobody(): Boolean {
        if (System.getProperty("user.name") != "root") return false
        return runCatching { rodar(listOf("id", "-u", "nobody")).first == 0 }.getOrDefault(false)
    }

    @Test
    fun restauracaoEFeitaComAIdentidadeDoServicoEOBancoNasceComODonoCerto() {
        org.junit.jupiter.api.Assumptions.assumeTrue(shell() != null && souRootComUsuarioNobody(), "precisa de root e do usuário nobody")
        val pasta = Files.createTempDirectory("painel-l1-")
        try {
            val c = montarCenarioDeReversao(pasta, "versao=2026.11.1\nbackup=painel-bom.db\n")
            val alvo = pasta.resolve("alvo-so-do-root.txt").also { Files.writeString(it, "ALVO-INTOCAVEL") }
            // o "atacante" já plantou um link no lugar do banco, apontando para um arquivo só do root
            val dados = pasta.resolve("dados")
            Files.createSymbolicLink(dados.resolve("painel.db"), alvo)
            // pastas atravessáveis pelo usuário do serviço; a pasta de dados é DELE (como em produção)
            rodar(listOf("chmod", "-R", "a+rX", pasta.toString()))
            rodar(listOf("chown", "nobody", dados.toString()))
            Files.setPosixFilePermissions(alvo, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"))
            val uidNobody = rodar(listOf("id", "-u", "nobody")).second.trim().toInt()
            val antes = Files.getAttribute(alvo, "unix:uid") as Int to Files.getPosixFilePermissions(alvo)
            val (codigo, saida) = rodar(
                listOf(shell()!!, reverter, c.raiz.toString(), "--restaurar-banco", "--sem-servico"),
                c.env + ("PAINEL_USUARIO" to "nobody"),
            )
            assertEquals(0, codigo, saida)
            val banco = dados.resolve("painel.db")
            assertTrue(Files.isRegularFile(banco, java.nio.file.LinkOption.NOFOLLOW_LINKS), "o banco restaurado é um arquivo comum, não um link")
            assertEquals("copia", Files.readString(banco).trim())
            assertEquals(uidNobody, Files.getAttribute(banco, "unix:uid") as Int, "o banco nasce com o dono do serviço (nenhum chown como root)")
            // o alvo do link plantado NÃO foi tocado: conteúdo, dono e modo
            assertEquals("ALVO-INTOCAVEL", Files.readString(alvo))
            assertEquals(antes, Files.getAttribute(alvo, "unix:uid") as Int to Files.getPosixFilePermissions(alvo))
            // o link antigo foi guardado ao lado (nada apagado)
            val guardados = Files.list(dados).use { s -> s.map { it.fileName.toString() }.filter { it.startsWith("painel.db.migrado-") }.toList() }
            assertEquals(1, guardados.size, guardados.toString())
        } finally {
            pasta.toFile().deleteRecursively()
        }
    }

    @Test
    fun rootNuncaTocaCaminhosDaPastaDoServico() {
        val s = ler("deploy/linux/reverter.sh")
        // nenhuma escrita/chown/chmod/mv do root sobre o banco: tudo vai dentro do script executado com a identidade do serviço
        assertFalse(Regex("(?m)^\\s*(chown|chmod|mv|cp|cat)\\b[^\\n]*\\\$BANCO").containsMatchIn(s), "root operando sobre \$BANCO fora do script do serviço")
        assertTrue("como_servico" in s && "PAINEL_USUARIO" in ler("deploy/linux/comum.sh"))
        // pasta temporária SÓ do root (dentro da raiz), nunca o TMPDIR do ambiente
        assertTrue("mktemp -d -p \"\$RAIZ\"" in s)
        assertTrue("mktemp -d -p \"\$RAIZ\"" in ler("deploy/linux/atualizar.sh"))
        // backup e conferência rodam com a identidade do serviço (a cópia nasce com o dono certo, nenhum -wal/-shm de root)
        val c = ler("deploy/linux/comum.sh")
        assertTrue(Regex("fazer_backup\\(\\) \\{[^}]*como_servico", RegexOption.DOT_MATCHES_ALL).containsMatchIn(c))
        assertTrue(Regex("como_servico[^\\n]*verificar-restauracao").containsMatchIn(s))
    }

    @Test
    fun powerShellNaoSegueLinkNemUsaAclPorCaminho() {
        val p = ler("deploy/windows/reverter.ps1")
        assertTrue("FileMode]::CreateNew" in p, "o banco restaurado é criado com CreateNew (recusa qualquer coisa já existente, inclusive link)")
        assertTrue("SetAccessControl" in p && "Set-Acl -LiteralPath \$banco" !in p, "ACL pelo handle, não pelo caminho")
        assertTrue("[System.IO.File]::Move(" in p, "mover o banco antigo renomeia o próprio item")
        assertFalse(Regex("Move-Item -LiteralPath \"\\\$banco").containsMatchIn(p))
    }

    @Test
    fun reverterCopiaUmaVezParaPastaTemporariaERestauraDeLa() {
        val s = ler("deploy/linux/reverter.sh")
        assertTrue("cp -P --" in s && "mktemp -d" in s && "trap" in s)
        // a conferência e a restauração usam o temporário, nunca o caminho original reaberto
        assertTrue(Regex("verificar-restauracao \"\\\$COPIA_SEGURA\"").containsMatchIn(s))
        assertTrue("< \"\$COPIA_SEGURA\"" in s && "set -C" in s, "restaura do temporário (entrada padrão), com O_EXCL no destino")
        assertFalse(Regex("cp [^\\n]*\"\\\$COPIA\" \"\\\$BANCO\"").containsMatchIn(s), "nunca relê o caminho original para restaurar")
        val p = ler("deploy/windows/reverter.ps1")
        assertTrue("\$copiaSegura" in p && "finally" in p)
        assertTrue(Regex("verificar-restauracao \\\$copiaSegura").containsMatchIn(p))
        assertTrue(Regex("OpenRead\\(\\\$copiaSegura\\)").containsMatchIn(p), ".ps1 restaura do temporário conferido")
        assertFalse(Regex("File\\]::Copy\\(\\\$copia,").containsMatchIn(p), ".ps1 não relê o original para restaurar")
    }

    @Test
    fun controleRegistroValidoChegaAConferirACopia() {
        Ferramentas.exigir(shell() != null, "sem sh")
        // o caso bom passa pelas validações e só então executa o painel da versão registrada (conferência da cópia)
        val (_, saida, executou) = reverterComRegistro("versao=2026.11.1\nbackup=painel-bom.db\n")
        assertTrue(executou, "o caso válido deveria chegar à conferência da cópia: $saida")
    }

    @Test
    fun registroFicaNaRaizEOUsuarioDoServicoNaoOEscreve() {
        // o registro vai para <raiz>, não para a pasta de dados do serviço
        for (n in listOf("deploy/linux/atualizar.sh", "deploy/linux/reverter.sh")) {
            val t = ler(n)
            assertFalse("\$DADOS/versao-anterior" in t, "$n usa a pasta de dados para o registro")
        }
        assertTrue("\$RAIZ/versao-anterior.txt" in ler("deploy/linux/reverter.sh"))
        assertTrue("gravar_registro" in ler("deploy/linux/atualizar.sh"))
    }

    @Test
    fun registroSoDepoisDoBackupEFalhaDeBackupLimpaAPastaExtraida() {
        val s = ler("deploy/linux/atualizar.sh")
        assertTrue(posicao(s, "fazer_backup") < posicao(s, "gravar_registro"), ".sh: registro só depois do backup")
        assertTrue("rm -rf \"\${RAIZ:?}/\$VERSAO\"" in s, ".sh: limpa a pasta extraída se o backup falhar")
        val p = ler("deploy/windows/atualizar.ps1")
        assertTrue(posicao(p, "Fazer-Backup") < posicao(p, "Gravar-Registro"), ".ps1: registro só depois do backup")
        assertTrue("Remove-Item" in p.substringAfter("Fazer-Backup"), ".ps1: limpa a pasta extraída se o backup falhar")
    }

    @Test
    fun reverterPowerShellValidaOQueLeDoRegistro() {
        val p = ler("deploy/windows/reverter.ps1")
        assertTrue("Test-PastaDaVersaoValida" in p && "Test-NomeDeBackupValido" in p && "Test-CaminhoDentroDe" in p)
        assertFalse("\$dados" in p.lines().filter { "versao-anterior" in it }.joinToString("\n"), "o registro não fica na pasta de dados")
        // as validações vêm ANTES de qualquer execução do painel.bat
        assertTrue(posicao(p, "Test-PastaDaVersaoValida") < posicao(p, "verificar-restauracao"))
        val c = ler("deploy/windows/comum.ps1")
        assertTrue("^[0-9]{4}\\.(0[1-9]|1[0-2])\\.[1-9][0-9]*\$" in c)
        assertTrue(ler("deploy/windows/atualizar.ps1").contains("Test-VersaoValida"))
    }

    @Test
    fun reverterSemVersaoAnteriorRecusa() = comPasta { pasta ->
        Ferramentas.exigir(temFerramentas(), "sem sh/unzip/sha256sum neste ambiente")
        val raiz = pasta.resolve("app").also { Files.createDirectories(it) }
        val dados = pasta.resolve("dados").also { Files.createDirectories(it) }
        val (codigo, saida) = rodar(listOf(shell()!!, reverter, raiz.toString(), "--sem-servico"), ambiente(dados))
        assertTrue(codigo != 0 && "versao-anterior" in saida, saida)
    }
}
