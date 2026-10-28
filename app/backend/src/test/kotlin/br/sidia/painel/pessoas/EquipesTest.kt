package br.sidia.painel.pessoas

import br.sidia.painel.TestPaths
import br.sidia.painel.security.RoleDirectory
import br.sidia.painel.trabalho.Trb1
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** `equipes.yaml`: leitura estrita, ids que existem em `papeis.yaml` e limites de cada campo. */
class EquipesTest {
    private fun arquivo(conteudo: String): Path = Files.createTempFile("equipes", ".yaml").also {
        Files.writeString(it, conteudo)
        it.toFile().deleteOnExit()
    }

    private val papeisExemplo = RoleDirectory.fromYaml(TestPaths.configDir.resolve("papeis.exemplo.yaml"))

    private fun membro(extra: String = "", id: String = "ana") =
        "membros:\n  - id: $id\n    horasSemanais: 40\n    alocacao: 1.0\n$extra"

    @Test
    fun carregaEquipesDoExemplo() {
        val eq = Equipes.fromYaml(TestPaths.configDir.resolve("equipes.exemplo.yaml"), papeisExemplo)
        assertEquals(6, eq.todos.size)
        val alfa1 = requireNotNull(eq.de("dev.alfa1"))
        assertEquals(40.0, alfa1.horasSemanais)
        assertEquals(0.7, alfa1.foco)
        assertEquals(setOf("android", "rede"), alfa1.competencias)
        // Foco ausente fica nulo (vale o focoPadrao do planejamento).
        assertNull(eq.de("dev.alfa2")?.foco)
        assertNull(eq.de("ninguem"))
    }

    @Test
    fun membroForaDoPapeisFalha() {
        val e = assertFailsWith<IllegalArgumentException> { Equipes.fromYaml(arquivo(membro(id = "fantasma.x")), papeisExemplo) }
        assertTrue("fantasma.x" in e.message.orEmpty(), e.message)
    }

    @Test
    fun limitesDeEquipe() {
        val ruins = listOf(
            "membros:\n  - id: ana\n    horasSemanais: 61\n    alocacao: 1.0\n",
            "membros:\n  - id: ana\n    horasSemanais: -1\n    alocacao: 1.0\n",
            membro("    foco: 0.0\n"),
            membro("    foco: 1.5\n"),
            "membros:\n  - id: ana\n    horasSemanais: 40\n    alocacao: 1.2\n",
            "membros:\n  - id: ana\n    horasSemanais: 40\n    alocacao: -0.1\n",
            membro("    competencias: [Android]\n"),
            membro("    competencias: [\"a b\"]\n"),
            membro("    competencias: [\"rede/x\"]\n"),
        )
        for (r in ruins) assertFailsWith<IllegalArgumentException>(r) { Equipes.fromYaml(arquivo(r), Trb1.papeis) }
    }

    @Test
    fun limitesValidosPassam() {
        val ok = "membros:\n  - id: ana\n    horasSemanais: 0\n    foco: 1.0\n    alocacao: 0\n    competencias: [android-17, ui]\n" +
            "  - id: bruno\n    horasSemanais: 60\n    alocacao: 1\n"
        val eq = Equipes.fromYaml(arquivo(ok), Trb1.papeis)
        assertEquals(listOf("ana", "bruno"), eq.todos.map { it.id })
        assertEquals(setOf("android-17", "ui"), eq.de("ana")?.competencias)
    }

    @Test
    fun chaveDesconhecidaEIdRepetidoFalham() {
        val e = assertFailsWith<IllegalArgumentException> { Equipes.fromYaml(arquivo(membro("    cargo: dev\n")), Trb1.papeis) }
        assertTrue("equipes" in e.message.orEmpty(), e.message)
        assertFailsWith<IllegalArgumentException> {
            Equipes.fromYaml(arquivo(membro() + "  - id: ana\n    horasSemanais: 40\n    alocacao: 1.0\n"), Trb1.papeis)
        }
        assertFailsWith<IllegalArgumentException> { Equipes.fromYaml(arquivo("segredo: 1\n"), Trb1.papeis) }
    }

    @Test
    fun listaVaziaEhValida() {
        assertEquals(0, Equipes.fromYaml(arquivo("membros: []\n"), Trb1.papeis).todos.size)
    }
}
