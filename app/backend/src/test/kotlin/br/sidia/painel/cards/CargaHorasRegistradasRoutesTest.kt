package br.sidia.painel.cards

import br.sidia.painel.AppContext
import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.cards.carga.CargaDto
import br.sidia.painel.config.Settings
import br.sidia.painel.pessoas.Cap1
import br.sidia.painel.sync.SqliteIssueStore
import br.sidia.painel.sync.WorklogRecord
import br.sidia.painel.testContextComBanco
import br.sidia.painel.trabalho.RespTrb
import br.sidia.painel.trabalho.Trb1
import br.sidia.painel.trabalho.rodarTrb1
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterAll
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** `horasRegistradas30d` da Carga pelo HTTP: vem do worklog sincronizado, só da janela de 30 dias e só da visão de quem pergunta. */
class CargaHorasRegistradasRoutesTest {
    companion object {
        private val banco = BancoEmArquivo()

        @JvmStatic
        @AfterAll
        fun apagar() = banco.apagar()
    }

    private var ctx: AppContext? = null

    @AfterTest
    fun encerrar() {
        ctx?.close()
    }

    private fun rodar(comWorklog: Boolean, bloco: suspend (suspend (String?, String) -> RespTrb) -> Unit) {
        val c = testContextComBanco(
            Settings(dashboardDevMode = false), banco, Cap1.papeis, Cap1.relogio, statusMap = Cap1.statusMap, calendar = Cap1.calendario,
            equipes = Cap1.equipes, ausencias = Cap1.ausencias, regrasPlanejamento = Cap1.regras,
        ).also { ctx = it }
        val ds = requireNotNull(c.dataSource)
        Trb1.carregar(ds, Cap1.issuesParaBanco())
        if (comWorklog) {
            val tg201 = Cap1.issuesParaBanco().first { it.json["key"].toString().contains("TG-201") }
            SqliteIssueStore(ds).upsert(
                tg201.json, tg201.eventos, Trb1.INSTANCIA, Trb1.CUSTOM_OS_VERSION, null, Trb1.agora, Trb1.ids,
                worklogs = listOf(
                    WorklogRecord("1", "ana", Instant.parse("2026-10-20T13:00:00Z"), 4 * 3600L),
                    WorklogRecord("2", "ana", Instant.parse("2026-10-28T13:00:00Z"), 6 * 3600L),
                    WorklogRecord("3", "ana", Instant.parse("2026-09-01T13:00:00Z"), 5 * 3600L), // fora dos 30 dias
                    WorklogRecord("4", "bruno", Instant.parse("2026-10-29T13:00:00Z"), 1800L),
                ),
            )
        }
        rodarTrb1(c, bloco = bloco)
    }

    private fun carga(r: RespTrb) = Json.decodeFromString<CargaDto>(r.corpo)

    @Test
    fun horasDoWorklogNaLinhaDaPessoa() = rodar(comWorklog = true) { get ->
        val d = carga(get("gerente.exemplo", "/api/cards/carga"))
        assertEquals(10.0, d.linhas.first { it.pessoa == "ana" }.horasRegistradas30d)
        assertEquals(0.5, d.linhas.first { it.pessoa == "bruno" }.horasRegistradas30d)
        assertEquals(0.0, d.linhas.first { it.pessoa == "carla" }.horasRegistradas30d)
    }

    @Test
    fun usuarioSoVeAsPropriasHoras() = rodar(comWorklog = true) { get ->
        val r = get("ana", "/api/cards/carga")
        assertEquals(listOf("ana"), carga(r).linhas.map { it.pessoa })
        assertEquals(10.0, carga(r).linhas.single().horasRegistradas30d)
    }

    @Test
    fun semWorklogSincronizadoFicaNulo() = rodar(comWorklog = false) { get ->
        carga(get("gerente.exemplo", "/api/cards/carga")).linhas.forEach { assertNull(it.horasRegistradas30d, it.pessoa) }
    }
}
