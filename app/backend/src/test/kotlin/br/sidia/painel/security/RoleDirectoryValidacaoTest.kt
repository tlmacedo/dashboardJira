package br.sidia.painel.security

import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

class RoleDirectoryValidacaoTest {
    @TempDir
    lateinit var pasta: Path

    private fun carregar(yaml: String): RoleDirectory {
        val arq = Files.createTempFile(pasta, "papeis", ".yaml")
        Files.writeString(arq, yaml.trimIndent())
        return RoleDirectory.fromYaml(arq)
    }

    private fun pessoaComId(idLiteralYaml: String) = carregar(
        """
        pessoas:
          - id: $idLiteralYaml
            nome: Fulano Ficticio
            papeis:
              - papel: GERENTE
        """,
    )

    @Test
    fun idForaDaRegraDeUsernameFalha() {
        // Entre aspas no YAML para os casos com espaço, vazio e maiúscula chegarem como texto.
        val invalidos = listOf("\"Dev.Alfa1\"", "\"dev alfa\"", "\"\"", "\"${"a".repeat(65)}\"", "\"-dev\"", "\"dev@x\"")
        invalidos.forEach { id ->
            val e = assertFailsWith<IllegalArgumentException>(id) { pessoaComId(id) }
            val bruto = id.removeSurrounding("\"")
            if (bruto.length <= 64) {
                assertTrue(bruto in e.message.orEmpty(), "a mensagem deve citar o id: ${e.message}")
            }
            assertTrue("username" in e.message.orEmpty(), "a mensagem deve citar a regra violada: ${e.message}")
        }
    }

    @Test
    fun idComSessentaEQuatroCaracteresEhValido() {
        pessoaComId("\"${"a".repeat(64)}\"")
    }

    @Test
    fun idsReservadosFalham() {
        listOf("dev", "system", "admin", "root", "anonymous").forEach { id ->
            val e = assertFailsWith<IllegalArgumentException>(id) { pessoaComId(id) }
            val msg = e.message.orEmpty()
            assertTrue(id in msg, msg)
            assertTrue("reservado" in msg, msg)
        }
    }

    @Test
    fun idsComPrefixoParecidoComReservadoSaoValidos() {
        // Só o valor exato é reservado; "dev.alfa1" é um username normal.
        pessoaComId("dev.alfa1")
        pessoaComId("admin.um")
    }

    @Test
    fun idDuplicadoFalha() {
        val e = assertFailsWith<IllegalArgumentException> {
            carregar(
                """
                pessoas:
                  - id: dev.alfa1
                    nome: Fulano Ficticio
                    papeis:
                      - papel: GERENTE
                  - id: dev.alfa1
                    nome: Outro Ficticio
                    papeis:
                      - papel: GERENTE
                """,
            )
        }
        assertTrue("dev.alfa1" in e.message.orEmpty(), "a mensagem deve citar o id: ${e.message}")
    }

    @Test
    fun idNoPadraoAntigoPorCargoAgoraEhUsernameValido() {
        // O padrão ge/co/sl/us-NNN foi um mal-entendido; "us-001" é só um username como outro qualquer.
        pessoaComId("us-001")
    }

    private fun usuarioComDuasVigencias(primeira: String, segunda: String) = carregar(
        """
        pessoas:
          - id: dev.alfa1
            nome: Fulano Ficticio
            papeis:
              - papel: USUARIO
                squad: Alfa
                $primeira
              - papel: USUARIO
                squad: Beta
                $segunda
        """,
    )

    @Test
    fun vigenciaUsuarioSobrepostaFalha() {
        // Alfa vai até 30/06 e Beta começa em 30/06: o dia 30/06 pertence aos dois squads.
        val e = assertFailsWith<IllegalArgumentException> {
            usuarioComDuasVigencias("ate: 2026-06-30", "de: 2026-06-30")
        }
        val msg = e.message.orEmpty()
        assertTrue("dev.alfa1" in msg, msg)
        assertTrue("2026-06-30" in msg, msg)
        // sem limites em ambas também se sobrepõe
        assertFailsWith<IllegalArgumentException> { usuarioComDuasVigencias("de: 2026-01-01", "de: 2026-03-01") }
    }

    @Test
    fun vigenciaUsuarioAdjacenteOk() {
        val dir = usuarioComDuasVigencias("ate: 2026-06-30", "de: 2026-07-01")
        assertEquals("Alfa", dir.squadOf("dev.alfa1", LocalDate.parse("2026-06-30")))
        assertEquals("Beta", dir.squadOf("dev.alfa1", LocalDate.parse("2026-07-01")))
    }

    @Test
    fun mesmoSquadSobrepostoNaoFalha() {
        carregar(
            """
            pessoas:
              - id: dev.alfa1
                nome: Fulano Ficticio
                papeis:
                  - papel: USUARIO
                    squad: Alfa
                  - papel: USUARIO
                    squad: Alfa
                    de: 2026-01-01
            """,
        )
    }

    @Test
    fun usuarioESquadLeaderSemSquadFalham() {
        listOf("USUARIO", "SQUAD_LEADER").forEach { papel ->
            val e = assertFailsWith<IllegalArgumentException>(papel) {
                carregar(
                    """
                    pessoas:
                      - id: dev.alfa1
                        nome: Fulano Ficticio
                        papeis:
                          - papel: $papel
                    """,
                )
            }
            assertTrue("dev.alfa1" in e.message.orEmpty(), e.message)
        }
    }
}
