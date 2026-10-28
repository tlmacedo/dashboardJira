package br.sidia.painel.cards.competencias

import br.sidia.painel.pessoas.Cap1
import br.sidia.painel.pessoas.Equipes
import br.sidia.painel.pessoas.MembroEquipe
import br.sidia.painel.pessoas.MotorDePessoas
import br.sidia.painel.trabalho.IssueSnapshot
import br.sidia.painel.trabalho.Trb1
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Matriz de competências sobre o CAP-1 (`ana`: android e rede; `bruno`: android; `carla`: rede e ui), só da visão de quem pergunta. */
class CompetenciasCalculatorTest {
    private val calc = CompetenciasCalculator(Cap1.motor)
    private val todos = setOf("ana", "bruno", "carla")

    private fun tg204(skill: String = "skill:ios") =
        Cap1.snapshot("TG-204", "Checklist", "bruno", "Open", Trb1.t("10-23T12"), LocalDate.of(2026, 11, 9), 8 * 3600, null, null, labels = listOf(skill))

    private fun rodar(visiveis: Set<String> = todos, abertas: List<IssueSnapshot> = Cap1.abertas()) = calc.calcular(visiveis, abertas)

    private fun IssueSnapshot.comLabels(vararg l: String) = copy(labels = l.toList())

    @Test
    fun matrizDoCenario() {
        val d = rodar().dto
        assertEquals(listOf("android", "rede", "ui"), d.competencias)
        assertEquals(listOf("ana", "bruno", "carla"), d.pessoas)
        // celulas[competência][pessoa]
        assertEquals(listOf(true, true, false), d.celulas[d.competencias.indexOf("android")])
        assertEquals(listOf(true, false, true), d.celulas[d.competencias.indexOf("rede")])
        assertEquals(listOf(false, false, true), d.celulas[d.competencias.indexOf("ui")])
        assertEquals(listOf("ui"), d.pessoaUnica)
        assertEquals(emptyList(), d.semNinguem)
        // As duas tasks de android da ana: 11,2 h + 5,6 h.
        assertEquals(mapOf("android" to 16.8), d.demandaH.mapValues { Math.round(it.value * 10) / 10.0 })
    }

    @Test
    fun skillSemNinguem() {
        val a = rodar(abertas = Cap1.abertas() + tg204())
        assertEquals(listOf("ios"), a.dto.semNinguem)
        assertEquals(listOf("TG-204"), a.semNinguem.map { it.key })
        // A competência pedida sem ninguém também vira linha da matriz (vazia), e a demanda dela aparece.
        val i = a.dto.competencias.indexOf("ios")
        assertTrue(i >= 0)
        assertEquals(listOf(false, false, false), a.dto.celulas[i])
        assertEquals(8.0, a.dto.demandaH.getValue("ios"), 1e-9)
        // `ios` não é "pessoa única": ninguém a tem.
        assertEquals(listOf("ui"), a.dto.pessoaUnica)
    }

    @Test
    fun competenciaSoDoVisivel() {
        // O líder do Alfa não vê a carla: `rede` passa a ter só a ana (pessoa única) e `ui` some da matriz.
        val d = rodar(visiveis = setOf("ana", "bruno")).dto
        assertEquals(listOf("android", "rede"), d.competencias)
        assertEquals(listOf("ana", "bruno"), d.pessoas)
        assertEquals(listOf("rede"), d.pessoaUnica)
        assertTrue("carla" !in d.toString() && "ui" !in d.competencias)
    }

    @Test
    fun usuarioVeSoASiEnaoHaPessoaUnicaComUmaSoPessoa() {
        val d = rodar(visiveis = setOf("ana")).dto
        assertEquals(listOf("ana"), d.pessoas)
        // Sozinha, toda competência seria "única": o indicador só existe com 2 ou mais pessoas na visão.
        assertEquals(emptyList(), d.pessoaUnica)
        assertEquals(listOf("android", "rede"), d.competencias)
    }

    @Test
    fun pessoaVisivelSemCadastroNaEquipeNaoEntra() {
        val d = rodar(visiveis = todos + "lider.alfa").dto
        assertEquals(listOf("ana", "bruno", "carla"), d.pessoas)
    }

    @Test
    fun labelsSkillSemDiferenciarMaiusculasEContandoUmaVez() {
        val t = Cap1.abertas().first { it.key == "TG-201" }.comLabels("skill:Android", "SKILL:android", "skill:", "skillx:y", "outra")
        val a = rodar(abertas = listOf(t))
        // Duas grafias de `skill:android` são uma competência pedida uma vez; "skill:" vazio e prefixos parecidos não valem.
        assertEquals(listOf("android"), a.porCompetencia.keys.toList())
        assertEquals(11.2, a.dto.demandaH.getValue("android"), 1e-9)
    }

    @Test
    fun tasksPorCompetenciaEOrdenacao() {
        val a = rodar(abertas = Cap1.abertas() + tg204())
        assertEquals(listOf("TG-201", "TG-202"), a.porCompetencia.getValue("android").map { it.key })
        assertEquals(listOf("TG-204"), a.porCompetencia.getValue("ios").map { it.key })
        assertEquals(a.dto.pessoas.sorted(), a.dto.pessoas)
        assertEquals(a.dto.competencias.sorted(), a.dto.competencias)
    }

    @Test
    fun equipeSemCompetenciasNaoQuebra() {
        val sem = MotorDePessoas(Equipes(listOf(MembroEquipe("ana", 40.0, 0.7, 1.0, emptySet()))), Cap1.ausencias, Cap1.calendario, Cap1.regras, Cap1.statusMap)
        val d = CompetenciasCalculator(sem).calcular(setOf("ana"), Cap1.abertas()).dto
        assertEquals(listOf("android"), d.competencias) // só a pedida
        assertEquals(listOf("android"), d.semNinguem)
        assertEquals(listOf(listOf(false)), d.celulas)
    }
}
