package br.sidia.painel.ci

import br.sidia.painel.TestPaths
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Barreira contra o retorno de contêiner, servidor de banco e Testcontainers (regra 6 do CLAUDE.md).
 *
 * O escopo é de propósito estreito: arquivos de build, código, workflows e scripts de subida. Os documentos
 * (`docs/`, `CLAUDE.md`, `.clinerules/`) citam o que é proibido e por isso não são varridos; `legacy/` é só
 * referência. Os termos proibidos são montados por concatenação para este arquivo não acusar a si mesmo
 * (e a varredura de código o ignora também pelo nome).
 */
class PlataformaSemDockerTest {
    private val docker = "doc" + "ker"
    private val testcontainers = "test" + "containers"
    private val postgres = "post" + "gres"
    private val h2 = "h" + "2"

    private val pastasIgnoradas = setOf("node_modules", "build", "dist", ".gradle", ".kotlin", ".git", "data")

    /** Arquivos regulares sob [raiz], sem descer nas pastas ignoradas. */
    private fun arquivosSob(raiz: Path): List<Path> {
        if (!Files.isDirectory(raiz)) return emptyList()
        val achados = mutableListOf<Path>()
        Files.walkFileTree(
            raiz,
            object : java.nio.file.SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir: Path, attrs: java.nio.file.attribute.BasicFileAttributes) =
                    if (dir != raiz && dir.name in pastasIgnoradas) {
                        java.nio.file.FileVisitResult.SKIP_SUBTREE
                    } else {
                        java.nio.file.FileVisitResult.CONTINUE
                    }

                override fun visitFile(file: Path, attrs: java.nio.file.attribute.BasicFileAttributes): java.nio.file.FileVisitResult {
                    if (attrs.isRegularFile) achados.add(file)
                    return java.nio.file.FileVisitResult.CONTINUE
                }
            },
        )
        return achados
    }

    private fun relativo(p: Path) = TestPaths.repoDir.relativize(p).joinToString("/")

    /** Nome de arquivo típico de contêiner: Dockerfile, Containerfile, compose, `.dockerignore`. */
    private fun ehArquivoDeContainer(nome: String): Boolean {
        val n = nome.lowercase()
        return n.startsWith("$docker" + "file") || n.startsWith("container" + "file") ||
            n.startsWith("$docker-compose") || n == ".${docker}ignore" ||
            Regex("^compose.*\\.ya?ml$").matches(n)
    }

    /** Linha de script que **executa** o Docker (comentário "sem Docker" não conta). */
    private fun executaDocker(texto: String): Boolean {
        val comando = Regex("(?i)(^|[\\s;&|(\"'`])$docker(\\s+(compose|run|build|pull|exec|ps|info|image|container)\\b|-compose\\b|\\.exe\\b)")
        return texto.lineSequence().map { it.trim() }.filterNot { it.startsWith("#") || it.startsWith("//") }
            .any { comando.containsMatchIn(it) }
    }

    @Test
    fun semArquivosDeContainer() {
        val repo = TestPaths.repoDir
        val raizDireta = Files.list(repo).use { s -> s.filter { it.isRegularFile() }.toList() }
        val candidatos = raizDireta + listOf("app", ".github", "scripts").flatMap { arquivosSob(repo.resolve(it)) }
        val achados = candidatos.filter { ehArquivoDeContainer(it.name) }.map(::relativo)
        assertEquals(emptyList(), achados, "Docker/compose não voltam ao repositório (regra 6 do CLAUDE.md)")
    }

    @Test
    fun buildSemDependenciaDeServidorDeBanco() {
        val arquivos = listOf(
            "app/backend/build.gradle.kts",
            "app/backend/settings.gradle.kts",
            "app/backend/gradle/libs.versions.toml",
            "app/frontend/package.json",
        )
        val proibidos = listOf(Regex(testcontainers), Regex(postgres), Regex("com\\.${h2}database|org\\.$h2|(^|[^a-z0-9])$h2([^a-z0-9]|$)"))
        val achados = arquivos.flatMap { rel ->
            val arq = TestPaths.repoDir.resolve(rel)
            assertTrue(arq.isRegularFile(), "arquivo de build ausente: $rel")
            val texto = Files.readString(arq).lowercase()
            proibidos.filter { it.containsMatchIn(texto) }.map { "$rel: ${it.pattern}" }
        }
        assertEquals(emptyList(), achados)
    }

    @Test
    fun codigoSemDriverNemContainerDeBanco() {
        val termos = listOf(
            "org.$testcontainers", "Postgre" + "SQLContainer", "org.$postgres" + "ql", "jdbc:$postgres" + "ql",
            "org.$h2", "jdbc:$h2",
        )
        val fontes = arquivosSob(TestPaths.repoDir.resolve("app/backend/src"))
            .filter { it.extension == "kt" || it.extension == "sql" }
        assertTrue(fontes.size > 50, "a varredura deveria achar os fontes do backend, achou ${fontes.size}")
        val achados = fontes.filter { it.name != "PlataformaSemDockerTest.kt" }.flatMap { arq ->
            val texto = Files.readString(arq)
            termos.filter { it in texto }.map { "${relativo(arq)}: $it" }
        }
        assertEquals(emptyList(), achados)
    }

    @Test
    fun workflowSemDockerNemServicos() {
        val termos = listOf(docker, testcontainers, postgres, "services:", "container:", "image:")
        val arquivos = arquivosSob(TestPaths.repoDir.resolve(".github"))
        assertTrue(arquivos.any { it.name == "ci.yml" }, "o workflow de CI deveria existir")
        val achados = arquivos.flatMap { arq ->
            val texto = Files.readString(arq).lowercase()
            termos.filter { it in texto }.map { "${relativo(arq)}: $it" }
        }
        assertEquals(emptyList(), achados)
    }

    @Test
    fun scriptsDeSubidaSemDocker() {
        for (rel in listOf("app/iniciar.sh", "app/iniciar.ps1")) {
            val arq = TestPaths.repoDir.resolve(rel)
            assertTrue(arq.isRegularFile(), "script de subida ausente: $rel")
            assertFalse(executaDocker(Files.readString(arq)), "$rel executa o Docker")
        }
    }

    // Poder dos detectores: casos sintéticos, sem depender de criar arquivo no repositório.

    @Test
    fun detectorDeArquivoDeContainerReconheceOsNomes() {
        for (nome in listOf("Dockerfile", "Dockerfile.dev", "Containerfile", "docker-compose.yml", "compose.yaml", "compose.override.yml", ".dockerignore")) {
            assertTrue(ehArquivoDeContainer(nome), nome)
        }
        for (nome in listOf("build.gradle.kts", "composicao.md", "iniciar.sh", "ci.yml")) assertFalse(ehArquivoDeContainer(nome), nome)
    }

    @Test
    fun detectorDeComandoIgnoraComentarioEPegaOComando() {
        assertTrue(executaDocker("echo oi\n$docker compose up -d\n"))
        assertTrue(executaDocker("if ($docker.exe) { }\n"))
        assertTrue(executaDocker("  $docker-compose down\n"))
        assertTrue(executaDocker("cd x && $docker run --rm img\n"))
        assertFalse(executaDocker("# sem Docker: não use $docker compose aqui\n"))
        assertFalse(executaDocker("echo \"Este painel roda sem Docker.\"\n"))
    }
}
