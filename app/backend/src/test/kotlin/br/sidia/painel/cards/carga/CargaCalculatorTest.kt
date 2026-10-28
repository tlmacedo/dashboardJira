package br.sidia.painel.cards.carga

import br.sidia.painel.cards.AvisosDePessoas
import br.sidia.painel.domain.Transition
import br.sidia.painel.pessoas.Cap1
import br.sidia.painel.riscos.RegrasDeRisco
import br.sidia.painel.trabalho.IssueSnapshot
import br.sidia.painel.trabalho.Trb1
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Carga por pessoa sobre o CAP-1: fila, capacidade, ocupação do horizonte e os sinais de cada um. */
class CargaCalculatorTest {
    private val calc = CargaCalculator(Cap1.motor, RegrasDeRisco.PADRAO)
    private val pessoas = listOf("ana", "bruno", "carla")

    private fun rodar(abertas: List<IssueSnapshot> = Cap1.abertas(), historico: List<IssueSnapshot> = emptyList(), quem: List<String> = pessoas) =
        calc.calcular(quem, abertas, historico, Cap1.hoje, Cap1.agora, true, "planejamento v1")

    private fun concluida(key: String, resp: String, inicio: String, fim: String): IssueSnapshot {
        val base = Cap1.snapshot(key, "Checklist", resp, "Closed", Trb1.t(inicio), null, null, null, null)
        return base.copy(
            resolution = "Done", resolved = Trb1.t(fim),
            timeline = base.timeline.copy(
                transitions = listOf(Transition(Trb1.t(inicio), "Open", "In Progress", null), Transition(Trb1.t(fim), "In Progress", "Closed", null)),
                resolved = Trb1.t(fim), resolution = "Done",
            ),
        )
    }

    @Test
    fun linhaDaAna() {
        val l = rodar().dto.linhas.first { it.pessoa == "ana" }
        assertEquals(2, l.abertas)
        assertEquals(1, l.wip)
        assertEquals(16.8, l.filaH, 1e-9)
        assertEquals(67.2, l.capacidade4SemH, 1e-9)
        assertEquals(0.25, l.ocupacao.fracao!!, 1e-9)
        assertEquals("ok", l.ocupacao.estado)
        assertEquals(false, l.ocupacao.semCapacidade)
        assertEquals(2.0, l.ausenciaDias, 1e-9)
        assertEquals(0.0, l.atrasoAcumuladoH)
        assertEquals(false, l.horasAssumidas)
        assertNull(l.horasRegistradas30d)
    }

    @Test
    fun linhaDaCarla() {
        val l = rodar().dto.linhas.first { it.pessoa == "carla" }
        assertEquals(6.0, l.atrasoAcumuladoH, 1e-9)
        assertEquals(true, l.horasAssumidas)
        assertEquals(1, l.vencidas)
        // Ausente de 02 a 06/11, mas 02/11 é feriado: 4 dias úteis de ausência.
        assertEquals(4.0, l.ausenciaDias, 1e-9)
        // A vencida não ocupa semana alguma: a ocupação do horizonte é zero (com capacidade nas outras semanas).
        assertEquals(0.0, l.ocupacao.fracao!!, 1e-9)
        assertTrue(rodar().dto.avisos.any { it.codigo == AvisosDePessoas.HORAS_ASSUMIDAS })
    }

    @Test
    fun linhasNaOrdemRecebidaSemPosicao() {
        assertEquals(pessoas, rodar().dto.linhas.map { it.pessoa })
        assertEquals(listOf("ana", "carla"), rodar(quem = listOf("ana", "carla")).dto.linhas.map { it.pessoa })
    }

    @Test
    fun contagensNoLugarDeExtremos() {
        val d = rodar().dto
        // Ninguém passa de 110%; as três pessoas estão abaixo dos 50% (folga).
        assertEquals(0, d.acimaDaCapacidade)
        assertEquals(3, d.comFolga)
        assertEquals(4, d.tasksComFolga)
        // 40 h a mais para o bruno vencendo em 04/11: (4 + 40) ÷ 37,8 h = 116%, acima do limite alto.
        val pesada = Cap1.snapshot("TG-400", "Checklist", "bruno", "Open", Trb1.t("10-22T12"), LocalDate.of(2026, 11, 4), 40 * 3600L, null, null)
        val d2 = rodar(Cap1.abertas() + pesada).dto
        assertEquals(1, d2.acimaDaCapacidade)
        assertEquals(2, d2.comFolga)
        assertEquals("problema", d2.linhas.first { it.pessoa == "bruno" }.ocupacao.estado)
        assertEquals(2, d2.tasksAcimaDaCapacidade, "as duas tasks na fila do bruno")
    }

    @Test
    fun jainSoComDuasPessoasComCapacidade() {
        assertTrue(rodar().dto.jain != null)
        assertNull(rodar(quem = listOf("ana")).dto.jain)
    }

    @Test
    fun semCapacidadeNaoViraPorcentagem() {
        // Pessoa fora da equipe: capacidade zero, a ocupação é "sem capacidade" e não há porcentagem.
        val sem = Cap1.snapshot("TG-401", "Checklist", "fantasma", "Open", Trb1.t("10-22T12"), LocalDate.of(2026, 11, 4), 3600L, null, null)
        val l = rodar(Cap1.abertas() + sem, quem = listOf("fantasma")).dto.linhas.single()
        assertEquals(true, l.ocupacao.semCapacidade)
        assertNull(l.ocupacao.fracao)
        assertEquals("problema", l.ocupacao.estado)
        assertEquals(0.0, l.capacidade4SemH)
    }

    @Test
    fun avisaPessoasSemCadastroNaEquipe() {
        val sem = Cap1.snapshot("TG-402", "Checklist", "fantasma", "Open", Trb1.t("10-22T12"), null, 3600L, null, null)
        val aviso = rodar(Cap1.abertas() + sem).dto.avisos.single { it.codigo == AvisosDePessoas.PESSOAS_SEM_CADASTRO_NA_EQUIPE }
        assertEquals("1", aviso.params["n"])
        // O aviso diz quantas, nunca quem.
        assertTrue("fantasma" !in aviso.toString())
    }

    @Test
    fun acimaDoP85SoContaEmAndamento() {
        // Cinco entregas de 1 dia útil formam o P85 do tipo; a TG-201 (em andamento há 4 dias úteis) passa dele, a TG-202 (aberta) não conta.
        val historico = (0 until 5).map { concluida("TG-5$it", "bruno", "10-0${it + 1}T12", "10-0${it + 2}T12") }
        val d = rodar(historico = historico).dto
        assertEquals(1, d.linhas.first { it.pessoa == "ana" }.acimaDoP85)
        assertEquals(0, d.linhas.first { it.pessoa == "bruno" }.acimaDoP85)
        assertEquals(0, rodar().dto.linhas.first { it.pessoa == "ana" }.acimaDoP85, "sem histórico não há P85")
    }

    @Test
    fun riscoAltoVemDoMesmoScoreDoCardRiscos() {
        // TG-205 (carla): vencida 25 + sem atualização/parada não pontuam aqui; sozinha fica abaixo de 60.
        assertEquals(0, rodar().dto.linhas.first { it.pessoa == "carla" }.riscoAlto)
    }

    @Test
    fun segmentosTemOMesmoTamanhoDoNumero() {
        val a = rodar()
        for (l in a.dto.linhas) {
            assertEquals(l.abertas, a.segmentos.getValue("pessoa:${l.pessoa}:fila").size)
            assertEquals(l.wip, a.segmentos.getValue("pessoa:${l.pessoa}:wip").size)
            assertEquals(l.vencidas, a.segmentos.getValue("pessoa:${l.pessoa}:vencidas").size)
            assertEquals(l.acimaDoP85, a.segmentos.getValue("pessoa:${l.pessoa}:acimaDoP85").size)
            assertEquals(l.riscoAlto, a.segmentos.getValue("pessoa:${l.pessoa}:riscoAlto").size)
        }
        assertEquals(a.dto.tasksAcimaDaCapacidade, a.segmentos.getValue("acimaDaCapacidade").size)
        assertEquals(a.dto.tasksComFolga, a.segmentos.getValue("comFolga").size)
    }
}
