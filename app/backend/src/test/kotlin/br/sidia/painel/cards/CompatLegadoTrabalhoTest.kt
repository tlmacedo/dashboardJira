package br.sidia.painel.cards

import br.sidia.painel.AppContext
import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.cards.abertas.AbertasDto
import br.sidia.painel.cards.fechadas.FechadasDto
import br.sidia.painel.cards.metas.MetasDto
import br.sidia.painel.trabalho.RespTrb
import br.sidia.painel.trabalho.novoContextoTrb1
import br.sidia.painel.trabalho.rodarTrb1
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterAll
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Compatibilidade com o app Python anterior (`legacy/backend/jiradash`), conferida no código antes de aceitar:
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
}
