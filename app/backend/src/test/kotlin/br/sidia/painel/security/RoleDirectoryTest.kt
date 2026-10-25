package br.sidia.painel.security

import br.sidia.painel.TestPaths
import br.sidia.painel.jql.JqlValues
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
                Pessoa("ex.lider", "Pessoa Um", listOf(AtribuicaoPapel(Role.SQUAD_LEADER, Vigencia(null, hoje.minusDays(1)), "A"))),
            ),
        )
        assertFalse(Role.SQUAD_LEADER in dir.rolesOf("ex.lider", hoje))
        assertTrue(Role.SQUAD_LEADER in dir.rolesOf("ex.lider", hoje.minusDays(1)))
        assertEquals(emptySet(), dir.squadsLedBy("ex.lider", hoje))
        assertEquals(setOf("A"), dir.squadsLedBy("ex.lider", hoje.minusDays(1)))
    }

    @Test
    fun membrosDoSquadNaData() {
        val dir = RoleDirectory(
            listOf(
                Pessoa(
                    "dev.mudou", "Pessoa Um",
                    listOf(
                        AtribuicaoPapel(Role.USUARIO, Vigencia(null, d("2026-06-30")), "A"),
                        AtribuicaoPapel(Role.USUARIO, Vigencia(d("2026-07-01"), null), "B"),
                    ),
                ),
            ),
        )
        assertEquals(setOf("dev.mudou"), dir.membersOf("A", d("2026-06-30")))
        assertEquals(emptySet(), dir.membersOf("A", d("2026-07-01")))
        assertEquals(setOf("dev.mudou"), dir.membersOf("B", d("2026-07-01")))
        assertEquals("A", dir.squadOf("dev.mudou", d("2026-06-30")))
        assertEquals("B", dir.squadOf("dev.mudou", d("2026-07-01")))
    }

    @Test
    fun usuarioEmUmSquadELiderEmOutroPrevaleceOUsuario() {
        // Documenta a precedência de squadOf: o squad de membro (USUARIO) vence o de liderança.
        val dir = RoleDirectory(
            listOf(
                Pessoa(
                    "lider.alfa", "Pessoa Um",
                    listOf(
                        AtribuicaoPapel(Role.USUARIO, Vigencia(null, null), "A"),
                        AtribuicaoPapel(Role.SQUAD_LEADER, Vigencia(null, null), "B"),
                    ),
                ),
            ),
        )
        val hoje = d("2026-10-25")
        assertEquals("A", dir.squadOf("lider.alfa", hoje))
        assertEquals(setOf("B"), dir.squadsLedBy("lider.alfa", hoje))
        assertEquals(setOf("lider.alfa"), dir.membersOf("A", hoje))
        assertEquals(emptySet(), dir.membersOf("B", hoje))
    }

    @Test
    fun autorizacaoDaAuditoria() {
        val hoje = d("2026-10-25")
        val dir = RoleDirectory(
            listOf(
                Pessoa("lider.alfa", "Lider A", listOf(AtribuicaoPapel(Role.SQUAD_LEADER, Vigencia(null, null), "A"))),
                Pessoa("lider.beta", "Lider B", listOf(AtribuicaoPapel(Role.SQUAD_LEADER, Vigencia(null, null), "B"))),
                Pessoa("dev.alfa1", "Responsavel", listOf(AtribuicaoPapel(Role.USUARIO, Vigencia(null, null), "A"))),
                Pessoa("coord.um", "Coord", listOf(AtribuicaoPapel(Role.COORDENADOR, Vigencia(null, null), null))),
                Pessoa("dev.beta1", "Reporter", listOf(AtribuicaoPapel(Role.USUARIO, Vigencia(null, null), "B"))),
            ),
        )
        // Os ids são os usernames do Jira: reporter e assignee chegam das issues com o mesmo valor.
        assertTrue(dir.isReporterAuthorized("dev.beta1", "dev.beta1", "dev.alfa1", hoje))
        assertTrue(dir.isReporterAuthorized("dev.beta1", "coord.um", "dev.alfa1", hoje))
        assertTrue(dir.isReporterAuthorized("dev.beta1", "lider.alfa", "dev.alfa1", hoje))
        assertFalse(dir.isReporterAuthorized("dev.beta1", "lider.beta", "dev.alfa1", hoje))
        assertFalse(dir.isReporterAuthorized("dev.beta1", "dev.alfa1", "dev.alfa1", hoje))
        assertFalse(dir.isReporterAuthorized("dev.beta1", "lider.alfa", null, hoje))
        // assignee fora do diretório: nenhum Squad Leader o cobre (fail-closed).
        assertFalse(dir.isReporterAuthorized("dev.beta1", "lider.alfa", "desconhecido", hoje))
    }

    @Test
    fun yamlDemoCarrega() {
        val dir = RoleDirectory.fromYaml(TestPaths.configDir.resolve("papeis.exemplo.yaml"))
        assertTrue(Role.GERENTE in dir.rolesOf("gerente.exemplo", d("2026-10-25")))
        // pessoa que mudou de squad em 01/07/2026
        assertEquals("Alfa", dir.squadOf("dev.mudou", d("2026-06-30")))
        assertEquals("Beta", dir.squadOf("dev.mudou", d("2026-07-01")))
        // papel encerrado
        assertTrue(Role.SQUAD_LEADER in dir.rolesOf("ex.lider", d("2026-03-31")))
        assertFalse(Role.SQUAD_LEADER in dir.rolesOf("ex.lider", d("2026-04-01")))
    }

    @Test
    fun exemploTemIdsComoUsernamesDoJira() {
        val caminho = TestPaths.configDir.resolve("papeis.exemplo.yaml")
        val dir = RoleDirectory.fromYaml(caminho)
        // ids lidos das linhas "- id: xxx" (o diretório não expõe a lista de pessoas)
        val ids = Files.readAllLines(caminho)
            .mapNotNull { Regex("""^\s*-\s+id:\s*(\S+)\s*$""").find(it)?.groupValues?.get(1) }
        assertTrue(ids.isNotEmpty())
        assertEquals(ids.size, ids.toSet().size, "ids repetidos")
        // Papéis em qualquer data: a pessoa precisa ter ao menos um (ex.lider e dev.mudou têm vigências).
        val datas = listOf(d("2026-03-31"), d("2026-06-30"), d("2026-07-01"), d("2026-10-25"))
        ids.forEach { id ->
            // mesma regra do username do Jira (levanta se inválido)
            assertEquals(id, JqlValues.username(id), "id fora da regra de username: $id")
            // o id é da PESSOA, não do cargo: o padrão antigo por prefixo de cargo não vale mais
            assertFalse(Regex("^(ge|co|sl|us)-").containsMatchIn(id), "id no padrão antigo por cargo: $id")
            assertTrue(datas.any { dir.rolesOf(id, it).isNotEmpty() }, "pessoa sem nenhum papel: $id")
        }
    }

    @Test
    fun yamlComVigenciaInvertidaLancaErro() {
        val arq = Files.createTempFile("papeis", ".yaml")
        try {
            Files.writeString(
                arq,
                """
                pessoas:
                  - id: dev.alfa1
                    nome: Fulano Ficticio
                    papeis:
                      - papel: USUARIO
                        squad: Alfa
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
