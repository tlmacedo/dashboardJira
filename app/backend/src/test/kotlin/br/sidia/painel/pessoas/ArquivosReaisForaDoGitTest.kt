package br.sidia.painel.pessoas

import br.sidia.painel.TestPaths
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue

/** Os arquivos reais de pessoas (dados pessoais) ficam fora do git; só os modelos `.exemplo` são versionados. */
class ArquivosReaisForaDoGitTest {
    private val linhas = Files.readAllLines(TestPaths.repoDir.resolve(".gitignore")).map { it.trim() }

    @Test
    fun arquivosReaisNoGitignore() {
        for (nome in listOf("equipes", "ausencias", "papeis")) {
            assertTrue(linhas.any { it.endsWith("config/$nome.yaml") && !it.startsWith("#") && !it.startsWith("!") }, "$nome.yaml fora do .gitignore")
        }
    }

    @Test
    fun modelosVersionadosExistem() {
        for (nome in listOf("equipes", "ausencias", "planejamento")) {
            assertTrue(Files.isRegularFile(TestPaths.configDir.resolve("$nome.exemplo.yaml")), "falta o modelo $nome.exemplo.yaml")
        }
        // Os modelos não podem ter a chave de motivo de ausência.
        assertTrue(!Files.readAllLines(TestPaths.configDir.resolve("ausencias.exemplo.yaml")).any { it.trim().startsWith("motivo:") })
    }
}
