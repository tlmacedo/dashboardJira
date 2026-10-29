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
 * Plano 13, Tarefa 6: o pacote vira um zip reprodutível com SHA-256, e a verificação recusa o que não pode ir. Conferências
 * estáticas dos scripts e do Gradle, mais provas negativas reais do `verificar-pacote.sh` com zips forjados em pasta temporária
 * (a montagem completa, com Node e Gradle, é o ensaio manual registrado no progresso).
 */
class PacoteDeImplantacaoTest {
    private val repo: Path = TestPaths.repoDir
    private val scripts: Path = repo.resolve("app/scripts")

    private fun ler(p: Path) = Files.readString(p)
    private fun script(nome: String) = ler(scripts.resolve(nome))
    private val gradle get() = ler(repo.resolve("app/backend/build.gradle.kts"))

    private val montar = listOf("montar-pacote.ps1", "montar-pacote.sh")
    private val verificar = listOf("verificar-pacote.ps1", "verificar-pacote.sh")

    // ---------------------------------------------------------------- Gradle

    @Test
    fun montarPacoteGradleEhZipReprodutivel() {
        assertTrue("""register<Zip>("montarPacote")""" in gradle)
        assertTrue("isPreserveFileTimestamps = false" in gradle)
        assertTrue("isReproducibleFileOrder = true" in gradle)
        assertTrue("-Dfile.encoding=UTF-8" in gradle, "o painel sobe com UTF-8 também no Windows")
    }

    @Test
    fun montarPacoteFalhaSemFrontendOuSemVersao() {
        assertTrue("frontend/dist/index.html" in gradle || "frontend\", \"dist\", \"index.html" in gradle)
        assertTrue(Regex("GradleException|error\\(|require\\(").containsMatchIn(gradle.substringAfter("montarPacote")))
    }

    @Test
    fun montarPacoteSoIncluiOQueOScriptMontou() {
        // O zip sai da pasta app/pacote/painel (já filtrada pelo script: só modelos); o Gradle não lê app/config.
        val bloco = gradle.substringAfter("""register<Zip>("montarPacote")""").substringBefore("\n}\n")
        assertTrue("pacote/painel" in bloco)
        assertFalse("../config" in bloco || "app/config" in bloco, "o zip não pode copiar a configuração direto")
        assertFalse(Regex("(?i)papeis|equipes|ausencias|\\.env\\b").containsMatchIn(bloco))
    }

    // ---------------------------------------------------------------- scripts

    @Test
    fun scriptsCalculamSha256() {
        val ps = script("montar-pacote.ps1")
        assertTrue("Get-FileHash -Algorithm SHA256" in ps)
        assertTrue(Regex("\"\\$\\w+  painel-\\$\\w+\\.zip").containsMatchIn(ps), "formato '<hash>  painel-<v>.zip' no .ps1")
        assertTrue(".sha256" in ps)
        val sh = script("montar-pacote.sh")
        assertTrue("sha256sum" in sh && "shasum -a 256" in sh)
        assertTrue(Regex("\\$\\w+  painel-\\$\\w+\\.zip").containsMatchIn(sh), "formato '<hash>  painel-<v>.zip' no .sh")
        assertTrue(".sha256" in sh)
        for (v in verificar) {
            val t = script(v)
            assertTrue(("Get-FileHash -Algorithm SHA256" in t) || ("sha256sum" in t && "shasum -a 256" in t), v)
        }
    }

    @Test
    fun scriptsValidamOFormatoDaVersao() {
        for (nome in montar) {
            val t = script(nome)
            assertTrue("[0-9]{4}" in t || "\\d{4}" in t, "$nome sem a validação AAAA.MM.N")
            assertTrue("-Pversao" in t && "-Pcommit" in t && "-PconstruidaEm" in t, nome)
            assertTrue("montarPacote" in t, nome)
        }
    }

    @Test
    fun verificarPacoteRecusaConfigReal() {
        val proibidos = listOf(
            "painel.env", ".db-wal", ".db-shm", "*.lock", "papeis.yaml", "equipes.yaml", "ausencias.yaml", "squads.yaml",
            "status_map.yaml", "jira_campos.yaml", "calendario.yaml", "vinculos.yaml", "labels_atraso.yaml", "auditoria.yaml",
            "Filtros_Status_tool.txt", "legacy", "node_modules", ".git",
        )
        for (nome in verificar) {
            val t = script(nome)
            for (p in proibidos) assertTrue(p in t, "$nome não cita $p")
            assertTrue("200" in t, "$nome sem o tamanho máximo de 200 MB")
        }
        // a recusa de *.env e de chaves (Plano 8) continua
        assertTrue("-like '*.env'" in script("verificar-pacote.ps1"))
        assertTrue("-iname '*.env'" in script("verificar-pacote.sh") && "-iname '*.pem'" in script("verificar-pacote.sh"))
    }

    @Test
    fun empacotarSemDocker() {
        for (nome in montar + verificar) {
            assertFalse(Regex("(?i)docker|ghcr|registry\\.").containsMatchIn(script(nome).replace("Dockerfile*", "").replace("docker-compose*", "").replace(".dockerignore", "")), nome)
        }
    }

    @Test
    fun montagemLevaImplantacaoEDocumentos() {
        for (nome in montar) {
            val t = script(nome)
            assertTrue("implantacao" in t && "docs" in t, nome)
        }
    }

    // ---------------------------------------------------------------- provas negativas reais (sh)

    private fun sh(): String? = listOf("/bin/sh", "/usr/bin/sh").firstOrNull { Files.isExecutable(Path.of(it)) }

    private fun temFerramentas(): Boolean =
        sh() != null && listOf("/usr/bin/unzip", "/bin/unzip").any { Files.isExecutable(Path.of(it)) } &&
            listOf("/usr/bin/sha256sum", "/bin/sha256sum", "/usr/bin/shasum").any { Files.isExecutable(Path.of(it)) }

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /** Forja `painel-<versao>.zip` (+ `.sha256` quando [hash] não é nulo) numa pasta temporária e roda a verificação. */
    private fun verificarZip(
        entradas: Map<String, String>,
        nome: String = "painel-2026.11.1.zip",
        hash: String? = "auto",
    ): Pair<Int, String> {
        val pasta = Files.createTempDirectory("painel-zip-")
        try {
            val zip = pasta.resolve(nome)
            ZipOutputStream(Files.newOutputStream(zip)).use { z ->
                for ((n, conteudo) in entradas) {
                    z.putNextEntry(ZipEntry(n))
                    z.write(conteudo.toByteArray())
                    z.closeEntry()
                }
            }
            if (hash != null) {
                val h = if (hash == "auto") sha256(Files.readAllBytes(zip)) else hash
                Files.writeString(pasta.resolve("$nome.sha256"), "$h  $nome\n")
            }
            val p = ProcessBuilder(sh(), scripts.resolve("verificar-pacote.sh").toString(), zip.toString()).redirectErrorStream(true).start()
            val saida = p.inputStream.bufferedReader().readText()
            assertTrue(p.waitFor(60, TimeUnit.SECONDS), "verificar-pacote travou")
            return p.exitValue() to saida
        } finally {
            pasta.toFile().deleteRecursively()
        }
    }

    private val raiz = "painel-2026.11.1/"

    @Test
    fun provaNegativaConfigRealNoZip() {
        Ferramentas.exigir(temFerramentas(), "sem sh/unzip/sha256sum neste ambiente")
        for (real in listOf("config/papeis.yaml", "papeis.yaml", "dados/equipes.yaml", "Filtros_Status_tool.txt", "legacy/app.py", "node_modules/x/index.js")) {
            val (codigo, saida) = verificarZip(mapOf(raiz + "VERSAO.txt" to "versao=2026.11.1", raiz + real to "x"))
            assertEquals(1, codigo, "$real: $saida")
            val citado = real.substringAfterLast('/').let { if (it == "app.py" || it == "index.js") real.substringBefore('/') else it }
            assertTrue(citado in saida, "$real: a falha deveria citar '$citado': $saida")
        }
    }

    @Test
    fun provaNegativaEnvFalsoNoZip() {
        Ferramentas.exigir(temFerramentas(), "sem sh/unzip/sha256sum neste ambiente")
        for (env in listOf(".env", "painel.env", "config/producao.ENV")) {
            val (codigo, saida) = verificarZip(mapOf(raiz + "VERSAO.txt" to "versao=2026.11.1", raiz + env to "SEGREDO=1"))
            assertEquals(1, codigo, "$env: $saida")
            assertTrue(env.substringAfterLast('/') in saida || "variáveis" in saida, "$env: $saida")
        }
    }

    @Test
    fun checksumErradoOuAusenteRecusa() {
        Ferramentas.exigir(temFerramentas(), "sem sh/unzip/sha256sum neste ambiente")
        val minimo = mapOf(raiz + "VERSAO.txt" to "versao=2026.11.1")
        val (c1, s1) = verificarZip(minimo, hash = "0".repeat(64))
        assertEquals(1, c1)
        assertTrue("SHA-256" in s1, s1)
        val (c2, s2) = verificarZip(minimo, hash = null)
        assertEquals(1, c2)
        assertTrue(".sha256" in s2, s2)
    }

    @Test
    fun nomeDoZipForaDoPadraoRecusa() {
        Ferramentas.exigir(temFerramentas(), "sem sh/unzip/sha256sum neste ambiente")
        val (codigo, saida) = verificarZip(mapOf("painel-2026.13.1/VERSAO.txt" to "x"), nome = "painel-2026.13.1.zip")
        assertEquals(1, codigo)
        assertTrue("nome do pacote" in saida, saida)
    }

    @Test
    fun entradaForaDaPastaRaizRecusa() {
        Ferramentas.exigir(temFerramentas(), "sem sh/unzip/sha256sum neste ambiente")
        val (codigo, saida) = verificarZip(mapOf("outra-pasta/VERSAO.txt" to "x"))
        assertEquals(1, codigo)
        assertTrue("pasta raiz" in saida, saida)
    }
}
