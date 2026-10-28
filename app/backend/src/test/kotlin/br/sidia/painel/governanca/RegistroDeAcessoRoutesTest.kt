package br.sidia.painel.governanca

import br.sidia.painel.AppContext
import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.contarAccessLog
import br.sidia.painel.trabalho.RespTrb
import br.sidia.painel.trabalho.novoContextoTrb1
import br.sidia.painel.trabalho.rodarTrb1
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterAll
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Registro de acesso (AU-02): só o gerente, a consulta é auditada, paginação e nada que identifique o achado. */
class RegistroDeAcessoRoutesTest {
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

    private fun rodar(bloco: suspend (suspend (String?, String) -> RespTrb) -> Unit) {
        val c = novoContextoTrb1(banco).also { ctx = it }
        rodarTrb1(c, bloco = bloco)
    }

    private val rota = "/api/governanca/acessos"
    private fun dto(r: RespTrb) = Json.decodeFromString<AcessosDto>(r.corpo)

    @Test
    fun soGerente() = rodar { get ->
        assertEquals(HttpStatusCode.OK, get("gerente.exemplo", rota).status)
        for (u in listOf("coord.um", "lider.alfa", "ana")) assertEquals(HttpStatusCode.Forbidden, get(u, rota).status, u)
        assertEquals(HttpStatusCode.Unauthorized, get(null, rota).status)
    }

    @Test
    fun aPropriaConsultaETambemRegistrada() = rodar { get ->
        val antes = ctx!!.contarAccessLog()
        get("gerente.exemplo", rota)
        get("coord.um", rota)
        assertEquals(antes + 2, ctx!!.contarAccessLog(), "inclusive a negada")
        val d = dto(get("gerente.exemplo", rota))
        val minhas = d.acessos.filter { it.rota == rota }
        assertTrue(minhas.any { it.usuario == "gerente.exemplo" && it.resultado == 200 }, d.toString())
        assertTrue(minhas.any { it.usuario == "coord.um" && it.resultado == 403 }, d.toString())
    }

    @Test
    fun semChaveSemSegmentoSemFiltroCru() = rodar { get ->
        get("gerente.exemplo", "/api/cartoes/TS-03/tasks?segmento=${"label:MYSTERY"}")
        get("gerente.exemplo", "/api/cards/qualidade")
        val r = get("gerente.exemplo", "$rota?usuario=gerente.exemplo")
        assertEquals(HttpStatusCode.OK, r.status, r.corpo)
        assertFalse("TG-101" in r.corpo, r.corpo)
        assertFalse("MYSTERY" in r.corpo, r.corpo)
        val d = dto(r)
        assertTrue(d.acessos.none { "TG-" in it.rota }, d.toString())
        // O filtro desta consulta (usuario) não vira registro, e o que é gravado aparece só como nome.
        assertTrue(d.acessos.all { a -> a.filtros.none { it == "usuario" || it == "rota" || it == "depoisDe" } }, d.toString())
    }

    @Test
    fun linhaAntigaComChaveNaRotaSaiMascarada() {
        assertEquals("/api/tasks/{key}", rotaSemChave("/api/tasks/TG-701"))
        assertEquals("/api/tasks/{key}/historico", rotaSemChave("/api/tasks/tg-701/historico"))
        assertEquals("/api/cartoes/TS-03/tasks", rotaSemChave("/api/cartoes/TS-03/tasks"), "id de cartão não é chave de issue")
        assertEquals("/api/cards/qualidade", rotaSemChave("/api/cards/qualidade"))
    }

    @Test
    fun paginaDeDuzentosComCursor()= rodar { get ->
        repeat(PAGINA_DO_REGISTRO + 5) { get("gerente.exemplo", "/api/cards/qualidade") }
        val p1 = dto(get("gerente.exemplo", rota))
        assertEquals(PAGINA_DO_REGISTRO, p1.acessos.size)
        val cursor = assertNotNull(p1.proximo)
        val p2 = dto(get("gerente.exemplo", "$rota?depoisDe=$cursor"))
        assertTrue(p2.acessos.isNotEmpty())
        assertTrue(p2.acessos.size <= PAGINA_DO_REGISTRO)
        val r = get("gerente.exemplo", "$rota?usuario=gerente.exemplo&depoisDe=1")
        assertNull(dto(r).proximo)
    }

    @Test
    fun periodoEDatasInvalidosSao400() = rodar { get ->
        for (q in listOf("de=lixo", "ate=2026-13-45", "de=1969-12-31&ate=2026-10-30", "de=2026-10-30&ate=2201-01-01", "de=2026-10-30&ate=2026-10-01", "de=2026-01-01&ate=2026-10-30", "depoisDe=0", "depoisDe=abc", "rota=api", "rota=/api/x%0Ay")) {
            assertEquals(HttpStatusCode.BadRequest, get("gerente.exemplo", "$rota?$q").status, q)
        }
        assertEquals(HttpStatusCode.OK, get("gerente.exemplo", "$rota?de=2026-08-02&ate=2026-10-30").status, "90 dias exatos")
    }

    @Test
    fun filtraPorUsuarioEPrefixoDeRota() = rodar { get ->
        get("coord.um", rota)
        val porUsuario = dto(get("gerente.exemplo", "$rota?usuario=coord.um"))
        assertTrue(porUsuario.acessos.isNotEmpty() && porUsuario.acessos.all { it.usuario == "coord.um" })
        val porRota = dto(get("gerente.exemplo", "$rota?rota=/api/governanca"))
        assertTrue(porRota.acessos.isNotEmpty() && porRota.acessos.all { it.rota.startsWith("/api/governanca") })
    }
}
