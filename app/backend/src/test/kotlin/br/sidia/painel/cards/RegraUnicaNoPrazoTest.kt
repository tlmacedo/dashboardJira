package br.sidia.painel.cards

import br.sidia.painel.AppContext
import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.auditoria.AuditoriaDto
import br.sidia.painel.cards.fechadas.FechadasDto
import br.sidia.painel.cards.metas.MetasDto
import br.sidia.painel.trabalho.RespTrb
import br.sidia.painel.trabalho.Trb1
import br.sidia.painel.trabalho.novoContextoTrb1
import br.sidia.painel.trabalho.rodarTrb1
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterAll
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Prova que Auditoria, Fechadas e Metas dão o MESMO "no prazo" (a regra única `NoPrazo.noUniverso` com as candidatas lidas
 * por vencimento), no cenário TRB-1P: o TRB-1 mais uma cancelada com vencimento, um Epic e uma resolvida antes do período.
 */
class RegraUnicaNoPrazoTest {
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

    /** TRB-1P: TG-150 cancelada (Won't Do) vencendo em 20/10, TG-151 Epic concluído, TG-152 resolvida em 28/09 e vencendo em 05/10. */
    private fun trb1p(): List<Trb1.IssueDeTeste> = Trb1.issues() + listOf(
        Trb1.issue(
            "TG-150", "Task", "ana", "10-01T12", "Cancelled", resolution = "Won't Do", resolvida = "10-19T12", vence = "2026-10-20",
            eventos = Trb1.trilha("TG-150", "Open", "10-19T12" to "Cancelled"),
        ),
        Trb1.issue(
            "TG-151", "Epic", "ana", "10-01T12", "Resolved", resolution = "Done", resolvida = "10-18T12", vence = "2026-10-20",
            eventos = Trb1.trilha("TG-151", "Open", "10-02T12" to "In Progress", "10-18T12" to "Resolved"),
        ),
        Trb1.issue(
            "TG-152", "Task", "ana", "09-20T12", "Resolved", resolution = "Done", resolvida = "09-28T12", vence = "2026-10-05",
            eventos = Trb1.trilha("TG-152", "Open", "09-21T12" to "In Progress", "09-28T12" to "Resolved"),
        ),
    )

    private fun rodar(bloco: suspend (suspend (String?, String) -> RespTrb) -> Unit) {
        val c = novoContextoTrb1(banco, trb1p()).also { ctx = it }
        rodarTrb1(c, bloco = bloco)
    }

    @Test
    fun mesmoUniversoComCanceladaEpicEResolvidaAntes() = rodar { get ->
        val auditoria = get("gerente.exemplo", "/api/auditoria/sinais?$PERIODO")
        assertEquals(HttpStatusCode.OK, auditoria.status, auditoria.corpo)
        val a = Json.decodeFromString<AuditoriaDto>(auditoria.corpo).noPrazo
        val f = Json.decodeFromString<FechadasDto>(get("gerente.exemplo", "/api/cards/fechadas?$PERIODO").corpo)
        val m = Json.decodeFromString<MetasDto>(get("gerente.exemplo", "/api/cards/metas?$PERIODO").corpo)
        // (universo, no prazo, no prazo ajustado) iguais nas três telas.
        assertEquals(Triple(5, 4, 5), Triple(a.concluidas, a.noPrazo, a.noPrazoAjustado), "Auditoria")
        assertEquals(Triple(5, 4, 5), Triple(f.comVencimento, f.noPrazo, f.noPrazoAjustado), "Fechadas")
        assertEquals(Triple(5, 4, 5), Triple(m.universo, m.noPrazo, m.noPrazoAjustado), "Metas")
        // A TG-150 (cancelada com vencimento) continua contando em Canceladas de Fechadas (FE-06).
        assertEquals(2, f.canceladas)
        assertEquals(true, f.linhas.none { it.key == "TG-152" }, "a TG-152 saiu antes do período: não é entrega dele")
    }

    @Test
    fun mesmoDiaDeResolucaoNasTresTelas() = rodar { get ->
        val f = Json.decodeFromString<FechadasDto>(get("gerente.exemplo", "/api/cards/fechadas?$PERIODO").corpo)
        val m = Json.decodeFromString<MetasDto>(get("gerente.exemplo", "/api/cards/metas?$PERIODO&granularidade=dia").corpo)
        // As mesmas quatro tasks do TRB-1 contam no mesmo conjunto nas duas telas.
        val chavesFechadas = Json.decodeFromString<br.sidia.painel.cards.contrato.DrillGenericoDto>(
            get("gerente.exemplo", "/api/cartoes/FE-05/tasks?$PERIODO&segmento=noPrazo").corpo,
        ).linhas.map { it.key }
        val chavesMetas = Json.decodeFromString<br.sidia.painel.cards.contrato.DrillGenericoDto>(
            get("gerente.exemplo", "/api/cartoes/MT-01/tasks?$PERIODO&segmento=noPrazo").corpo,
        ).linhas.map { it.key }
        assertEquals(listOf("TG-111", "TG-112", "TG-114", "TG-152"), chavesFechadas)
        assertEquals(chavesFechadas, chavesMetas)
        assertEquals(f.noPrazo, m.noPrazo)
    }
}
