package br.sidia.painel.cards

import br.sidia.painel.AppContext
import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.auditoria.AuditoriaDto
import br.sidia.painel.cards.contrato.DrillGenericoDto
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
import kotlin.test.assertTrue

/**
 * Consistência entre telas (Plano 5, Tarefa 9): a mesma task cai no mesmo dia, na mesma semana e no mesmo "no prazo"
 * em Auditoria, Fechadas, Metas e Tempo em status. Cenário: TRB-1 mais a TG-120, resolvida às 23h de 15/10 em Manaus
 * (03h de 16/10 em UTC) e vencendo em 15/10: no prazo em Manaus, atrasada se alguém contar pelo dia em UTC.
 */
class ConsistenciaEntreTelasTest {
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

    private fun tg120() = Trb1.issue(
        "TG-120", "Checklist", "bruno", "10-05T12", "Closed", resolution = "Done", resolvida = "10-16T03", vence = "2026-10-15",
        eventos = Trb1.trilha("TG-120", "Open", "10-08T12" to "In Progress", "10-16T03" to "Closed"),
    )

    /** TG-150 cancelada com vencimento e TG-151 Epic concluído: ficam fora do universo pela regra única. */
    private fun reforco() = listOf(
        Trb1.issue(
            "TG-150", "Task", "ana", "10-01T12", "Cancelled", resolution = "Won't Do", resolvida = "10-19T12", vence = "2026-10-20",
            eventos = Trb1.trilha("TG-150", "Open", "10-19T12" to "Cancelled"),
        ),
        Trb1.issue(
            "TG-151", "Epic", "ana", "10-01T12", "Resolved", resolution = "Done", resolvida = "10-18T12", vence = "2026-10-20",
            eventos = Trb1.trilha("TG-151", "Open", "10-02T12" to "In Progress", "10-18T12" to "Resolved"),
        ),
    )

    private fun rodar(extras: List<Trb1.IssueDeTeste>, bloco: suspend (suspend (String?, String) -> RespTrb) -> Unit) {
        val c = novoContextoTrb1(banco, Trb1.issues() + tg120() + extras).also { ctx = it }
        rodarTrb1(c, bloco = bloco)
    }

    private suspend fun drill(get: suspend (String?, String) -> RespTrb, caminho: String): List<String> {
        val r = get("gerente.exemplo", caminho)
        assertEquals(HttpStatusCode.OK, r.status, r.corpo)
        return Json.decodeFromString<DrillGenericoDto>(r.corpo).linhas.map { it.key }
    }

    private suspend fun noPrazoNasTresTelas(get: suspend (String?, String) -> RespTrb) {
        val auditoria = get("gerente.exemplo", "/api/auditoria/sinais?$PERIODO")
        assertEquals(HttpStatusCode.OK, auditoria.status, auditoria.corpo)
        val a = Json.decodeFromString<AuditoriaDto>(auditoria.corpo).noPrazo
        val f = Json.decodeFromString<FechadasDto>(get("gerente.exemplo", "/api/cards/fechadas?$PERIODO").corpo)
        val m = Json.decodeFromString<MetasDto>(get("gerente.exemplo", "/api/cards/metas?$PERIODO").corpo)
        assertEquals(4, a.noPrazo, "Auditoria")
        assertEquals(4, f.noPrazo, "Fechadas")
        assertEquals(4, m.noPrazo, "Metas")
        // Universo e ajustado também coincidem: TG-111, 112, 113 (ajustada), 114 e 120.
        assertEquals(Triple(5, 4, 5), Triple(a.concluidas, a.noPrazo, a.noPrazoAjustado), "Auditoria")
        assertEquals(Triple(5, 4, 5), Triple(f.comVencimento, f.noPrazo, f.noPrazoAjustado), "Fechadas")
        assertEquals(Triple(5, 4, 5), Triple(m.universo, m.noPrazo, m.noPrazoAjustado), "Metas")
        // A TG-120 conta no dia 15/10 de Manaus nas três (e não em 16/10, o dia em UTC).
        assertTrue("TG-120" in drill(get, "/api/cartoes/AU-03/tasks?$PERIODO&segmento=noPrazo"), "Auditoria")
        val linha = f.linhas.single { it.key == "TG-120" }
        assertEquals("2026-10-15", linha.resolvida, "Fechadas")
        val diaMetas = Json.decodeFromString<MetasDto>(get("gerente.exemplo", "/api/cards/metas?$PERIODO&granularidade=dia").corpo)
        val dia15 = diaMetas.porPeriodo.single { it.chave == "2026-10-15" }
        assertEquals(2, dia15.universo, "Metas: TG-112 e TG-120 vencem em 15/10")
        assertEquals(2, dia15.noPrazo, "Metas: as duas foram entregues no dia")
        assertTrue(diaMetas.porPeriodo.none { it.chave == "2026-10-16" && it.noPrazo > 0 && it.universo == 0 })
    }

    @Test
    fun mesmoNoPrazoEmTresTelas() = rodar(emptyList()) { get -> noPrazoNasTresTelas(get) }

    @Test
    fun mesmoNoPrazoComCanceladaEEpicNoReforco() = rodar(reforco()) { get -> noPrazoNasTresTelas(get) }

    @Test
    fun mesmaSemanaEmFechadasETempoEmStatus() = rodar(emptyList()) { get ->
        val semana = "de=2026-10-12&ate=2026-10-18"
        // Fechadas: a TG-112 (fechada em 15/10) está na semana de 12/10.
        val deFechadas = drill(get, "/api/cartoes/FE-07/tasks?$PERIODO&segmento=semana%3A2026-10-12")
        assertTrue("TG-112" in deFechadas, deFechadas.toString())
        assertTrue("TG-120" in deFechadas, "a TG-120 fecha em 15/10 de Manaus: mesma semana, não a de 12/10 por UTC")
        // Tempo em status, com a mesma semana como período: analisa a mesma task.
        val deTempo = drill(get, "/api/cartoes/TS-01/tasks?$semana&segmento=analisadas")
        assertTrue("TG-112" in deTempo, deTempo.toString())
        assertTrue(deTempo.containsAll(deFechadas), "tudo que Fechadas põe na semana, Tempo em status analisa")
    }
}
