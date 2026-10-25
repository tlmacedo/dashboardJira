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

    @Test
    fun idForaDoPadraoFalha() {
        listOf("x-1", "us-1", "us-0001", "xx-001", "US-001", "us_001").forEach { id ->
            val e = assertFailsWith<IllegalArgumentException>(id) {
                carregar(
                    """
                    pessoas:
                      - id: $id
                        nome: Fulano Ficticio
                        papeis:
                          - papel: GERENTE
                    """,
                )
            }
            assertTrue(id in e.message.orEmpty(), "a mensagem deve citar o id: ${e.message}")
        }
    }

    private fun usuarioComDuasVigencias(primeira: String, segunda: String) = carregar(
        """
        pessoas:
          - id: us-001
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
        assertTrue("us-001" in msg, msg)
        assertTrue("2026-06-30" in msg, msg)
        // sem limites em ambas também se sobrepõe
        assertFailsWith<IllegalArgumentException> { usuarioComDuasVigencias("de: 2026-01-01", "de: 2026-03-01") }
    }

    @Test
    fun vigenciaUsuarioAdjacenteOk() {
        val dir = usuarioComDuasVigencias("ate: 2026-06-30", "de: 2026-07-01")
        assertEquals("Alfa", dir.squadOf("us-001", LocalDate.parse("2026-06-30")))
        assertEquals("Beta", dir.squadOf("us-001", LocalDate.parse("2026-07-01")))
    }

    @Test
    fun mesmoSquadSobrepostoNaoFalha() {
        carregar(
            """
            pessoas:
              - id: us-001
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
                      - id: us-001
                        nome: Fulano Ficticio
                        papeis:
                          - papel: $papel
                    """,
                )
            }
            assertTrue("us-001" in e.message.orEmpty(), e.message)
        }
    }
}
