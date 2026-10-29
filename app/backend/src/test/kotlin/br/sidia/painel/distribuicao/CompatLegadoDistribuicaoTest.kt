package br.sidia.painel.distribuicao

import br.sidia.painel.pessoas.CargaDaPessoa
import br.sidia.painel.pessoas.Ocupacao
import br.sidia.painel.pessoas.SemanaDeCarga
import br.sidia.painel.trabalho.IssueSnapshot
import br.sidia.painel.trabalho.SnapDeTeste
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * O motor novo contra o legado (`legacy/backend/jiradash/distribution/engine.py`, na tag `legacy-final`; os valores do
 * legado ficam congelados aqui e o teste não precisa da pasta): o que continua IGUAL (pesos, faixas, gatilho,
 * limite de movimentos, elegibilidade e desempate) e o que muda DE PROPÓSITO (cada divergência está em
 * `docs/analise/diferencas-esperadas-checklist-integridade-governanca.md`, seção 7b). Os números do legado foram conferidos
 * no código Python antes de entrarem aqui; nenhuma rede, nenhum Jira.
 */
class CompatLegadoDistribuicaoTest {
    private val regras = RegrasDeDistribuicao.PADRAO
    private val motor = Distribuidor(regras)
    private val semCompetencia: (IssueSnapshot) -> Set<String> = { emptySet() }

    /** O `score()` do legado para um candidato com produtividade relativa [share], por extenso. */
    private fun scoreDoLegado(
        cargaH: Double, capacidadeH: Double, horas: Double, wip: Int, limiteWip: Int, faltantes: Int, afinidade: Boolean, share: Double = 1.0,
    ): Double {
        val capacidadeEfetiva = maxOf(0.1, capacidadeH * share.coerceIn(0.85, 1.15))
        val fLoad = 1.0 * (cargaH + horas) / capacidadeEfetiva
        val fWip = 0.5 * (wip.toDouble() / maxOf(1, limiteWip)) + (if (wip >= limiteWip) 1.0 else 0.0)
        return fLoad + fWip + 0.6 * faltantes + (if (afinidade) -0.15 else 0.0)
    }

    @Test
    fun constantesDoLegadoCongeladasDoCodigoPython() {
        // Constantes copiadas de `legacy/backend/jiradash/distribution/engine.py` (tag `legacy-final`), onde estavam como
        // `W_LOAD = 1.0`, `W_WIP = 0.5`, `W_WIP_OVER = 1.0`, `W_SKILL_MISS = 0.6`, `W_AFFINITY = 0.15`,
        // `CAP_WEIGHT_MIN, CAP_WEIGHT_MAX = 0.85, 1.15`, `high: float = 1.1`, `hysteresis: float = 0.2`,
        // `max_moves: int = 10` e `c.capacity_h > 0.5`. O peso da carga (W_LOAD = 1,0) é o coeficiente fixo de
        // `scoreDoLegado`; os demais são as regras padrão do painel.
        assertEquals(
            listOf(0.5, 1.0, 0.6, 0.15, 0.85, 1.15, 1.1, 10, 0.2, 0.5),
            listOf(
                regras.pesoWip, regras.penalidadeWipCheio, regras.pesoCompetenciaFaltante, regras.bonusEpicoOuComponente,
                regras.produtividadeMin, regras.produtividadeMax, regras.gatilhoRebalanceamento, regras.maxMovimentos,
                regras.histereseRebalanceamentoPp / 100.0, regras.capacidadeMinimaH,
            ),
        )
    }

    @Test
    fun scoreComoOLegado() {
        // Pesos 1,0 / 0,5 / 1,0 / 0,6 / 0,15 e a faixa de produtividade 0,85 a 1,15 do legado.
        assertEquals(listOf(0.5, 1.0, 0.6, 0.15), listOf(regras.pesoWip, regras.penalidadeWipCheio, regras.pesoCompetenciaFaltante, regras.bonusEpicoOuComponente))
        assertEquals(listOf(0.85, 1.15), listOf(regras.produtividadeMin, regras.produtividadeMax))
        assertEquals(0.5, regras.capacidadeMinimaH)
        // Os termos de WIP, de competência e de afinidade são exatamente os do legado; só a carga muda (veja o teste seguinte).
        val horas = 6.0
        val casos = listOf(
            Candidato("a", 16.8, 67.2, 1, 3, 0, false, null, 1.0) to listOf(16.8, 67.2, 1, 3, 0, false),
            Candidato("b", 8.0, 32.0, 3, 3, 0, true, null, 1.0) to listOf(8.0, 32.0, 3, 3, 0, true),
            Candidato("c", 0.0, 44.8, 0, 3, 1, false, null, 1.0) to listOf(0.0, 44.8, 0, 3, 1, false),
            Candidato("d", 30.0, 40.0, 5, 3, 2, true, null, 1.0) to listOf(30.0, 40.0, 5, 3, 2, true),
        )
        val sugestao = motor.distribuir(listOf(SnapDeTeste.snap("TG-1", resp = null) to horas), casos.map { it.first }, semCompetencia).sugestoes.single()
        for ((candidato, p) in casos) {
            val legado = scoreDoLegado(p[0] as Double, p[1] as Double, horas, p[2] as Int, p[3] as Int, p[4] as Int, p[5] as Boolean)
            val novo = sugestao.fatores.first { it.pessoa == candidato.pessoa }.score
            // Divergência 1: o painel NÃO soma as horas do item à carga do candidato (a diferença é exatamente horas ÷ capacidade).
            assertEquals(legado - horas / candidato.capacidadeH, novo, 1e-9, candidato.pessoa)
        }
    }

    @Test
    fun produtividadeLimitadaMasComSentidoInvertido() {
        // Legado: produtividade relativa maior AUMENTA a capacidade (divide a carga). Painel: gasto ÷ estimado maior ENCHE mais rápido
        // (multiplica a carga). Os dois limitam a 0,85 a 1,15; o painel só passa a usar o histórico real de horas.
        val alta = Candidato("a", 20.0, 40.0, 0, 3, 0, false, null, 2.0)
        val baixa = Candidato("a", 20.0, 40.0, 0, 3, 0, false, null, 0.1)
        fun carga(c: Candidato) = motor.distribuir(listOf(SnapDeTeste.snap("TG-1", resp = null) to 4.0), listOf(c), semCompetencia).sugestoes.single().fatores.single().carga
        assertEquals(20.0 * 1.15 / 40.0, carga(alta), 1e-9)
        assertEquals(20.0 * 0.85 / 40.0, carga(baixa), 1e-9)
    }

    @Test
    fun elegibilidadeEDesempateComoOLegado() {
        // Capacidade de exatamente 0,5 h não é elegível (`>`, como no legado); o desempate é quem recebeu há mais tempo.
        val limite = Candidato("a", 0.0, 0.5, 0, 3, 0, false, null, 1.0)
        val ok = Candidato("b", 10.0, 40.0, 0, 3, 0, false, null, 1.0)
        assertEquals("b", motor.distribuir(listOf(SnapDeTeste.snap("TG-1", resp = null) to 1.0), listOf(limite, ok), semCompetencia).sugestoes.single().sugerido)
        assertEquals(null, motor.distribuir(listOf(SnapDeTeste.snap("TG-1", resp = null) to 1.0), listOf(limite), semCompetencia).sugestoes.single().sugerido)
    }

    private val segunda = LocalDate.of(2026, 10, 26)
    private val rebalanceador = Rebalanceador(regras)

    private fun carga(pessoa: String, demandaH: Double, capacidadeH: Double): CargaDaPessoa {
        val o = Ocupacao.Valor(demandaH / capacidadeH)
        return CargaDaPessoa(pessoa, listOf(SemanaDeCarga(segunda, demandaH, capacidadeH, o)), 0.0, false, o)
    }

    private fun item(chave: String, horas: Double) = SnapDeTeste.snap(chave, resp = "ana") to horas
    private val todosSabem: (IssueSnapshot) -> Set<String> = { emptySet() }

    @Test
    fun rebalanceamentoComoOLegado() {
        // Gatilho de 110% (estrito), até 10 movimentos e histerese de 20 pontos percentuais.
        assertEquals(listOf(1.10, 10, 20.0), listOf(regras.gatilhoRebalanceamento, regras.maxMovimentos, regras.histereseRebalanceamentoPp))
        val cargas = listOf(carga("ana", 44.0, 40.0), carga("bruno", 8.0, 40.0)).associateBy { it.pessoa }
        val movimentos = rebalanceador.calcular(cargas, mapOf("ana" to listOf(item("TG-1", 4.0))), mapOf("ana" to emptySet(), "bruno" to emptySet()), todosSabem)
        assertTrue(movimentos.isEmpty(), "110% exatos não é origem, como no legado (util > high)")
        // 150% contra 40%: o legado move (1,5 - 0,65 >= 0,2) e o painel também (1,25 - 0,65 >= 0,2).
        val r = rebalanceador.calcular(
            listOf(carga("ana", 60.0, 40.0), carga("bruno", 16.0, 40.0)).associateBy { it.pessoa },
            mapOf("ana" to listOf(item("TG-1", 10.0))), mapOf("ana" to emptySet(), "bruno" to emptySet()), todosSabem,
        )
        assertEquals(listOf("TG-1"), r.map { it.issueKey })
        assertEquals("bruno", r.single().para)
    }

    @Test
    fun histereseDepoisDoMovimentoEhMaisConservadora() {
        // Divergência 2: o legado mede a histerese pela ocupação da origem ANTES (1,15 - 0,75 = 0,40 >= 0,20: move); o painel mede
        // DEPOIS (0,90 - 0,75 = 0,15 < 0,20: não move), para não gerar o vai e volta.
        val pessoas = listOf(carga("ana", 46.0, 40.0), carga("bruno", 20.0, 40.0)).associateBy { it.pessoa }
        val antesDoLegado = 46.0 / 40.0 - (20.0 + 10.0) / 40.0
        assertTrue(antesDoLegado >= 0.2)
        val r = rebalanceador.calcular(pessoas, mapOf("ana" to listOf(item("TG-1", 10.0))), mapOf("ana" to emptySet(), "bruno" to emptySet()), todosSabem)
        assertTrue(r.isEmpty(), "o painel não move quando a origem, depois de mover, fica a menos de 20 pp do destino")
    }
}
