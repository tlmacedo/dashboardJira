package br.sidia.painel.pessoas

import br.sidia.painel.TestPaths
import br.sidia.painel.security.RoleDirectory
import br.sidia.painel.trabalho.Trb1
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** `ausencias.yaml`: somente leitura, sem motivo, datas coerentes e sem sobreposição da mesma pessoa. */
class AusenciasTest {
    private fun arquivo(conteudo: String): Path = Files.createTempFile("ausencias", ".yaml").also {
        Files.writeString(it, conteudo)
        it.toFile().deleteOnExit()
    }

    private fun dia(s: String) = LocalDate.parse(s)

    /** As ausências do CAP-1: `carla` 02 a 06/11, `bruno` 10/11 (meio dia), `ana` 04 e 05/11. */
    private val cap1 = Ausencias(
        listOf(
            Ausencia("carla", dia("2026-11-02"), dia("2026-11-06"), 1.0),
            Ausencia("bruno", dia("2026-11-10"), dia("2026-11-10"), 0.5),
            Ausencia("ana", dia("2026-11-04"), dia("2026-11-05"), 1.0),
        ),
    )

    @Test
    fun carregaOExemploESoDePessoasDoPapeis() {
        val papeis = RoleDirectory.fromYaml(TestPaths.configDir.resolve("papeis.exemplo.yaml"))
        val a = Ausencias.fromYaml(TestPaths.configDir.resolve("ausencias.exemplo.yaml"), papeis)
        assertEquals(3, a.daPessoa("dev.alfa1").size + a.daPessoa("dev.beta1").size + a.daPessoa("dev.alfa2").size)
        // `fracao` ausente vale 1.0
        assertEquals(1.0, a.fracaoEm("dev.beta1", dia("2026-11-18")))
    }

    @Test
    fun ausenciaSemMotivo() {
        val txt = "ausencias:\n  - pessoa: ana\n    de: 2026-11-04\n    ate: 2026-11-05\n    motivo: ferias\n"
        val e = assertFailsWith<IllegalArgumentException> { Ausencias.fromYaml(arquivo(txt), Trb1.papeis) }
        assertTrue("ausências não guardam motivo" in e.message.orEmpty(), e.message)
        // Nem o valor do motivo nem a chave vazia passam.
        assertFailsWith<IllegalArgumentException> {
            Ausencias.fromYaml(arquivo("ausencias:\n  - pessoa: ana\n    de: 2026-11-04\n    ate: 2026-11-05\n    motivo:\n"), Trb1.papeis)
        }
        assertTrue("ferias" !in e.message.orEmpty())
    }

    @Test
    fun ausenciaDePessoaDesconhecidaFalha() {
        val txt = "ausencias:\n  - pessoa: ana\n    de: 2026-11-04\n    ate: 2026-11-05\n  - pessoa: saiu.do.time\n    de: 2026-11-04\n    ate: 2026-11-05\n"
        val e = assertFailsWith<IllegalArgumentException> { Ausencias.fromYaml(arquivo(txt), Trb1.papeis) }
        assertTrue("#2" in e.message.orEmpty() && "saiu.do.time" !in e.message.orEmpty(), e.message)
        // Cita o número da linha da lista para quem edita o arquivo achar o erro.
        assertTrue("#2" in e.message.orEmpty(), e.message)
    }

    @Test
    fun datasInvertidasFalham() {
        val txt = "ausencias:\n  - pessoa: ana\n    de: 2026-11-06\n    ate: 2026-11-04\n"
        val e = assertFailsWith<IllegalArgumentException> { Ausencias.fromYaml(arquivo(txt), Trb1.papeis) }
        assertTrue("#1" in e.message.orEmpty() && "ana" !in e.message.orEmpty().substringAfterLast(": "), e.message)
        assertFailsWith<IllegalArgumentException> {
            Ausencias.fromYaml(arquivo("ausencias:\n  - pessoa: ana\n    de: ontem\n    ate: 2026-11-04\n"), Trb1.papeis)
        }
    }

    @Test
    fun fracaoForaDoLimiteFalha() {
        for (f in listOf("0.0", "1.5", "-0.5")) {
            val txt = "ausencias:\n  - pessoa: ana\n    de: 2026-11-04\n    ate: 2026-11-05\n    fracao: $f\n"
            assertFailsWith<IllegalArgumentException>(f) { Ausencias.fromYaml(arquivo(txt), Trb1.papeis) }
        }
    }

    @Test
    fun sobreposicaoDaMesmaPessoaFalha() {
        val txt = "ausencias:\n  - pessoa: ana\n    de: 2026-11-04\n    ate: 2026-11-06\n  - pessoa: ana\n    de: 2026-11-06\n    ate: 2026-11-09\n"
        val e = assertFailsWith<IllegalArgumentException> { Ausencias.fromYaml(arquivo(txt), Trb1.papeis) }
        assertTrue("sobrep" in e.message.orEmpty(), e.message)
        // Pessoas diferentes no mesmo dia, e a mesma pessoa em períodos que só se tocam sem cruzar, são válidas.
        val ok = "ausencias:\n  - pessoa: ana\n    de: 2026-11-04\n    ate: 2026-11-05\n  - pessoa: ana\n    de: 2026-11-06\n    ate: 2026-11-06\n" +
            "  - pessoa: bruno\n    de: 2026-11-04\n    ate: 2026-11-05\n"
        assertEquals(2, Ausencias.fromYaml(arquivo(ok), Trb1.papeis).daPessoa("ana").size)
    }

    @Test
    fun fracaoEm() {
        assertEquals(0.5, cap1.fracaoEm("bruno", dia("2026-11-10")))
        assertEquals(1.0, cap1.fracaoEm("ana", dia("2026-11-04")))
        assertEquals(1.0, cap1.fracaoEm("ana", dia("2026-11-05")), "ponta final inclusiva")
        assertEquals(0.0, cap1.fracaoEm("ana", dia("2026-11-03")))
        assertEquals(0.0, cap1.fracaoEm("ana", dia("2026-11-06")))
        assertEquals(0.0, cap1.fracaoEm("desconhecido", dia("2026-11-04")))
    }

    @Test
    fun entreDevolveSoAsQueTocamOIntervalo() {
        assertEquals(setOf("ana", "carla"), cap1.entre(dia("2026-11-05"), dia("2026-11-05")).map { it.pessoa }.toSet())
        assertEquals(listOf("bruno"), cap1.entre(dia("2026-11-09"), dia("2026-11-12")).map { it.pessoa })
        assertEquals(emptyList(), cap1.entre(dia("2026-11-11"), dia("2026-11-20")))
        assertEquals(listOf("ana"), cap1.daPessoa("ana").map { it.pessoa })
    }

    @Test
    fun maisNaoAlteraOOriginal() {
        val hipotetica = Ausencia("ana", dia("2026-11-03"), dia("2026-11-03"), 1.0)
        val nova = cap1.mais(hipotetica)
        assertEquals(1.0, nova.fracaoEm("ana", dia("2026-11-03")))
        assertEquals(0.0, cap1.fracaoEm("ana", dia("2026-11-03")))
        assertEquals(1, cap1.daPessoa("ana").size)
        assertEquals(2, nova.daPessoa("ana").size)
        // Uma hipótese que cruza uma ausência real não derruba o simulador: vale a maior fração do dia.
        val cruza = cap1.mais(Ausencia("bruno", dia("2026-11-10"), dia("2026-11-11"), 1.0))
        assertEquals(1.0, cruza.fracaoEm("bruno", dia("2026-11-10")))
    }
}
