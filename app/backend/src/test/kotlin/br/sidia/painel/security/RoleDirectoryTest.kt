package br.sidia.painel.security

import br.sidia.painel.TestPaths
import java.nio.file.Files
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RoleDirectoryTest {
    private fun d(s: String) = LocalDate.parse(s)

    @Test
    fun vigenciaPontasInclusivas() {
        val v = Vigencia(d("2026-03-01"), d("2026-09-30"))
        assertTrue(v.contem(d("2026-03-01")))
        assertTrue(v.contem(d("2026-09-30")))
        assertFalse(v.contem(d("2026-10-01")))
        assertFalse(v.contem(d("2026-02-28")))
        assertTrue(Vigencia(null, null).contem(d("2030-01-01")))
    }

    @Test
    fun vigenciaInvertidaLancaErro() {
        assertFailsWith<IllegalArgumentException> { Vigencia(d("2026-10-02"), d("2026-10-01")) }
    }

    @Test
    fun papelEncerradoOntem() {
        val hoje = d("2026-10-25")
        val dir = RoleDirectory(
            listOf(
                Pessoa("p1", "Pessoa Um", listOf(AtribuicaoPapel(Role.SQUAD_LEADER, Vigencia(null, hoje.minusDays(1)), "A"))),
            ),
        )
        assertFalse(Role.SQUAD_LEADER in dir.rolesOf("p1", hoje))
        assertTrue(Role.SQUAD_LEADER in dir.rolesOf("p1", hoje.minusDays(1)))
        assertEquals(emptySet(), dir.squadsLedBy("p1", hoje))
        assertEquals(setOf("A"), dir.squadsLedBy("p1", hoje.minusDays(1)))
    }

    @Test
    fun membrosDoSquadNaData() {
        val dir = RoleDirectory(
            listOf(
                Pessoa(
                    "p1", "Pessoa Um",
                    listOf(
                        AtribuicaoPapel(Role.USUARIO, Vigencia(null, d("2026-06-30")), "A"),
                        AtribuicaoPapel(Role.USUARIO, Vigencia(d("2026-07-01"), null), "B"),
                    ),
                ),
            ),
        )
        assertEquals(setOf("p1"), dir.membersOf("A", d("2026-06-30")))
        assertEquals(emptySet(), dir.membersOf("A", d("2026-07-01")))
        assertEquals(setOf("p1"), dir.membersOf("B", d("2026-07-01")))
        assertEquals("A", dir.squadOf("p1", d("2026-06-30")))
        assertEquals("B", dir.squadOf("p1", d("2026-07-01")))
    }

    @Test
    fun autorizacaoDaAuditoria() {
        val hoje = d("2026-10-25")
        val dir = RoleDirectory(
            listOf(
                Pessoa("lider-a", "Lider A", listOf(AtribuicaoPapel(Role.SQUAD_LEADER, Vigencia(null, null), "A"))),
                Pessoa("lider-b", "Lider B", listOf(AtribuicaoPapel(Role.SQUAD_LEADER, Vigencia(null, null), "B"))),
                Pessoa("resp", "Responsavel", listOf(AtribuicaoPapel(Role.USUARIO, Vigencia(null, null), "A"))),
                Pessoa("coord", "Coord", listOf(AtribuicaoPapel(Role.COORDENADOR, Vigencia(null, null), null))),
                Pessoa("rep", "Reporter", listOf(AtribuicaoPapel(Role.USUARIO, Vigencia(null, null), "B"))),
            ),
        )
        assertTrue(dir.isReporterAuthorized("rep", "rep", "resp", hoje))
        assertTrue(dir.isReporterAuthorized("rep", "coord", "resp", hoje))
        assertTrue(dir.isReporterAuthorized("rep", "lider-a", "resp", hoje))
        assertFalse(dir.isReporterAuthorized("rep", "lider-b", "resp", hoje))
        assertFalse(dir.isReporterAuthorized("rep", "resp", "resp", hoje))
        assertFalse(dir.isReporterAuthorized("rep", "lider-a", null, hoje))
    }

    @Test
    fun yamlDemoCarrega() {
        val dir = RoleDirectory.fromYaml(TestPaths.configDir.resolve("papeis.exemplo.yaml"))
        assertTrue(Role.GERENTE in dir.rolesOf("ge-001", d("2026-10-25")))
        // pessoa que mudou de squad em 01/07/2026
        assertEquals("Alfa", dir.squadOf("us-001", d("2026-06-30")))
        assertEquals("Beta", dir.squadOf("us-001", d("2026-07-01")))
        // papel encerrado
        assertTrue(Role.SQUAD_LEADER in dir.rolesOf("us-003", d("2026-03-31")))
        assertFalse(Role.SQUAD_LEADER in dir.rolesOf("us-003", d("2026-04-01")))
    }

    @Test
    fun exemploTemIdsNoPadraoPorCargo() {
        val caminho = TestPaths.configDir.resolve("papeis.exemplo.yaml")
        val dir = RoleDirectory.fromYaml(caminho)
        val hoje = d("2026-10-25")
        // ids lidos das linhas "- id: xx-000" (o diretório não expõe a lista de pessoas)
        val ids = Files.readAllLines(caminho)
            .mapNotNull { Regex("""^\s*-\s+id:\s*(\S+)\s*$""").find(it)?.groupValues?.get(1) }
        assertTrue(ids.isNotEmpty())
        assertEquals(ids.size, ids.toSet().size, "ids repetidos")
        val prefixoPorPapel = mapOf(
            Role.GERENTE to "ge",
            Role.COORDENADOR to "co",
            Role.SQUAD_LEADER to "sl",
            Role.USUARIO to "us",
        )
        ids.forEach { id ->
            val m = Regex("""^(ge|co|sl|us)-\d{3}$""").matchEntire(id)
            assertTrue(m != null, "id fora do padrão: $id")
            // cargo principal = o mais abrangente vigente hoje
            val principal = rolesOf(dir, id, hoje).maxByOrNull { it.ordinal }
            assertEquals(prefixoPorPapel[principal], m!!.groupValues[1], "prefixo incoerente em $id")
        }
    }

    private fun rolesOf(dir: RoleDirectory, id: String, data: LocalDate) = dir.rolesOf(id, data)

    @Test
    fun yamlComVigenciaInvertidaLancaErro() {
        val arq = Files.createTempFile("papeis", ".yaml")
        try {
            Files.writeString(
                arq,
                """
                pessoas:
                  - id: x-1
                    nome: Fulano Ficticio
                    papeis:
                      - papel: USUARIO
                        de: 2026-10-02
                        ate: 2026-10-01
                """.trimIndent(),
            )
            assertFailsWith<IllegalArgumentException> { RoleDirectory.fromYaml(arq) }
        } finally {
            Files.deleteIfExists(arq)
        }
    }
}
