package br.sidia.painel.cards

import br.sidia.painel.AppContext
import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.cards.abertas.AbertasDto
import br.sidia.painel.cards.fechadas.FechadasDto
import br.sidia.painel.cards.metas.MetasDto
import br.sidia.painel.cards.riscos.RiscoCalculator
import br.sidia.painel.previsao.RegrasDePrevisao
import br.sidia.painel.riscos.FatorDeRisco
import br.sidia.painel.riscos.RegrasDeRisco
import br.sidia.painel.trabalho.MonteCarlo
import br.sidia.painel.trabalho.Quando
import br.sidia.painel.trabalho.RespTrb
import br.sidia.painel.trabalho.novoContextoTrb1
import br.sidia.painel.trabalho.rodarTrb1
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterAll
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Compatibilidade com o app Python anterior (`legacy/backend/jiradash`, preservado na tag `legacy-final`), conferida no
 * código antes de aceitar:
 * - ciclo = resolução menos a PRIMEIRA entrada em status ativo, de espera ou bloqueado (`metrics/core.py`,
 *   `cycle_time_s`), em dias corridos: TG-111 4, TG-112 9, TG-113 13, TG-114 1 -> P50 6,5;
 * - faixas de idade `0-7`, `8-14`, `15-30` e `> 30` em dias corridos desde a primeira entrada ativa ou a criação
 *   (`analytics.py`, `open_view`, e `age_s`);
 * - faixas de entrega contra o vencimento (`reports.py`, `bucket_of`): D-3 ou antes, D-2, D-1, D, atraso 1-3 d, > 3 d.
 */
class CompatLegadoTrabalhoTest {
    companion object {
        private val banco = BancoEmArquivo()

        @JvmStatic
        @AfterAll
        fun apagar() = banco.apagar()

        private const val PERIODO = "de=2026-10-01&ate=2026-10-30"
    }

    private var ctx: AppContext? = null

    @AfterTest
    fun encerrar() {
        ctx?.close()
    }

    private fun rodar(bloco: suspend (suspend (String?, String) -> RespTrb) -> Unit) {
        val c = novoContextoTrb1(banco).also { ctx = it }
        rodarTrb1(c, bloco = bloco)
    }

    private suspend inline fun <reified T> ler(get: suspend (String?, String) -> RespTrb, caminho: String): T {
        val r = get("gerente.exemplo", caminho)
        assertEquals(HttpStatusCode.OK, r.status, r.corpo)
        return Json.decodeFromString(r.corpo)
    }

    @Test
    fun cicloCorridoComoOLegado() = rodar { get ->
        val f = ler<FechadasDto>(get, "/api/cards/fechadas?$PERIODO&unit=corrido")
        assertEquals(6.5, f.ciclo?.p50)
        assertEquals(4, f.ciclo?.n)
        assertEquals(listOf(4.0, 9.0, 13.0, 1.0), listOf("TG-111", "TG-112", "TG-113", "TG-114").map { k -> f.linhas.single { it.key == k }.ciclo })
    }

    @Test
    fun faixasDeIdadeComoOLegado() = rodar { get ->
        val a = ler<AbertasDto>(get, "/api/cards/abertas?$PERIODO&unit=corrido")
        // TG-101 29 d (desde In Progress), TG-102 45 d (criação), TG-103 9 d, TG-104 1 d.
        assertEquals(listOf("0-7" to 1, "8-14" to 1, "15-30" to 1, ">30" to 1), a.porIdade.map { it.chave to it.n })
    }

    @Test
    fun metasDeltaComoOLegado() = rodar { get ->
        val m = ler<MetasDto>(get, "/api/cards/metas?$PERIODO")
        // TG-111 e TG-112 no dia (D), TG-114 em D-2, TG-113 com 4 dias de atraso (> 3 d).
        assertEquals(
            listOf("D3_OU_ANTES" to 0, "D2" to 1, "D1" to 0, "D" to 2, "ATRASO_1_3" to 0, "ATRASO_MAIS_3" to 1),
            m.distribuicao.map { it.chave to it.n },
        )
    }

    // ---- Bloco 5b: Previsão e Riscos, conferidos contra legacy/backend/jiradash/metrics (forecast.py e risk.py, na tag legacy-final) ----

    // O código do legado (tag `legacy-final`) não está mais no repositório: os trechos Python conferidos antes da remoção
    // ficam nos comentários e os valores esperados, congelados nos próprios testes.

    @Test
    fun monteCarloSemChegadaComoOLegado() {
        // forecast.py::monte_carlo_when com vazão constante: 20 itens a 5 por semana = 4 semanas em todos os percentis.
        // Trechos do legado: `reached = cum >= remaining_items`, `max_weeks: int = 104`, `MIN_WEEKS = 8`,
        // `np.percentile(totals, 15)` e `np.percentile(totals, 5)`.
        assertEquals(Quando(4, 4, 4), MonteCarlo(Random(42)).quando(List(8) { 5 }, null, 20, 10_000, 104))
        // O limite de semanas e o piso de confiança são os do legado.
        assertEquals(104, RegrasDePrevisao.PADRAO.limiteSemanas)
        assertEquals(8, RegrasDePrevisao.PADRAO.minSemanasConfianca)
        assertEquals(10_000, RegrasDePrevisao.PADRAO.sorteios)
        // quantos: P85 = 15º percentil e P95 = 5º, como monte_carlo_how_many.
        assertEquals(Triple(20, 20, 20), MonteCarlo(Random(42)).quantos(listOf(5), 4, 10_000))
    }

    @Test
    fun restanteDadaIdadeComoOLegado() {
        // forecast.py::remaining_given_age: ciclos maiores que a idade, mínimo de 5, P50 e P85 com uma casa decimal.
        // Trechos do legado: `min_n: int = 5` e `c > age_days`.
        val ciclos = listOf(2.0, 4.0, 6.0, 8.0, 10.0, 12.0)
        assertEquals(5.0 to 7.8, MonteCarlo(Random(1)).restanteDadaIdade(ciclos, 3.0))
        assertNull(MonteCarlo(Random(1)).restanteDadaIdade(ciclos, 5.0)) // só 4 itens mais longos que 5
    }

    @Test
    fun riscoComPesosDoLegado() {
        // Os pesos, na ordem em que o legado os soma (idade P85 e P50, parada, vencida, previsão, vence em breve, bloqueada,
        // reaberta, trocas, horas, sem atualização, sem estimativa).
        // Congelado do risk.py (a ordem de `add(peso, ...)`), conferido contra o código Python antes da remoção.
        val doLegado = listOf(30, 10, 15, 25, 20, 8, 15, 10, 10, 15, 10, 5)
        val novos = RegrasDeRisco.PADRAO.pesos
        val ordem = listOf(
            FatorDeRisco.IDADE_ACIMA_P85, FatorDeRisco.IDADE_ACIMA_P50, FatorDeRisco.PARADA, FatorDeRisco.VENCIDA,
            FatorDeRisco.PREVISAO_APOS_VENCIMENTO, FatorDeRisco.VENCE_EM_BREVE, FatorDeRisco.BLOQUEADA, FatorDeRisco.REABERTA,
            FatorDeRisco.MUITAS_TROCAS, FatorDeRisco.ESTOURO_DE_HORAS, FatorDeRisco.SEM_ATUALIZACAO, FatorDeRisco.SEM_ESTIMATIVA,
        )
        assertEquals(ordem.map { novos.getValue(it) }, doLegado)
        // Os limiares: níveis 60 e 30, vence em 3 dias, horas acima de 125%, 3 trocas e 7 dias sem atualização.
        // Trechos do legado: `LEVELS = [(60, "alto"), (30, "médio"), (0, "baixo")]`, `(rec.due - today).days <= 3`,
        // `1.25 * rec.original_estimate_s`, `reassign_count(rec) >= 3`, `idle > 7` e `min(100, sum(`.
        val r = RegrasDeRisco.PADRAO
        assertEquals(listOf(60, 30, 3, 1.25, 3, 7), listOf(r.alto, r.medio, r.venceEmDias, r.estouro, r.trocas, r.diasSemAtualizacao))
        assertEquals(listOf("alto", "medio", "baixo"), listOf(60, 30, 29).map(r::nivel))
        assertEquals(100, RiscoCalculator.TETO)
    }
}
