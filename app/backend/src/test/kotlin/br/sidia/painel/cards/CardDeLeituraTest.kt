package br.sidia.painel.cards

import br.sidia.painel.AppContext
import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.cards.contrato.CartaoMeta
import br.sidia.painel.cards.contrato.CatalogoDeCartoes
import br.sidia.painel.cards.contrato.DrillGenericoDto
import br.sidia.painel.cards.contrato.FonteDoDado
import br.sidia.painel.cards.contrato.GrupoDeCartao
import br.sidia.painel.cards.contrato.Natureza
import br.sidia.painel.cards.contrato.catalogoPadrao
import br.sidia.painel.db.AccessLog
import br.sidia.painel.http.ErroDto
import br.sidia.painel.security.Role
import br.sidia.painel.trabalho.ConsultaDeTrabalho
import br.sidia.painel.trabalho.SqliteWorkStore
import br.sidia.painel.trabalho.Situacao
import br.sidia.painel.trabalho.Trb1
import br.sidia.painel.trabalho.novoContextoTrb1
import br.sidia.painel.trabalho.rodarTrb1
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterAll
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Serializable
data class ContagemTeste(val n: Int, val avisos: List<AvisoDto> = emptyList())

/** Card de teste: conta as abertas do escopo (e prova que o status fora do mapa vira aviso, não 500). */
class CardTesteAbertas : CardDeLeitura<ContagemTeste> {
    override val id = "teste-abertas"
    override val periodoPadraoDias: Int? = null

    override fun calcular(e: EntradaDeCard): ResultadoDeCard<ContagemTeste> {
        val abertas = e.store.snapshots(ConsultaDeTrabalho(Trb1.INSTANCIA, null, Situacao.ABERTAS))
            .filter { e.escopo.permiteAtual(it) && e.filtros.aceita(it) }
        abertas.forEach { e.ctx.statusMap.category(it.status) } // propaga UnmappedStatusException
        return ResultadoDeCard(ContagemTeste(abertas.size)) { segmento ->
            if (segmento == "abertas") abertas.map { linhaDe(it, e.escopo, "aberta") } else null
        }
    }

    override fun vazio(avisos: List<AvisoDto>) = ContagemTeste(0, avisos)
}

/** Fronteiras da rota genérica de card: filtros, escopo pela visibilidade, aviso de status e `access_log`. */
class CardDeLeituraTest {
    companion object {
        private val banco = BancoEmArquivo()

        @JvmStatic
        @AfterAll
        fun apagar() = banco.apagar()

        private val meta = CartaoMeta(
            id = "ZZ-01", grupo = GrupoDeCartao.TRABALHO_EM_CURSO, natureza = Natureza.ESTOQUE, tela = "teste-abertas",
            tituloChave = "card.zz01.titulo", oQueMostraChave = "card.zz01.oQueMostra", comoCalculaChave = "card.zz01.comoCalcula",
            fontes = listOf(FonteDoDado.ISSUE), papeis = Role.entries.toSet(), temDrill = true, exigeChangelog = false, exigeWorklog = false,
        )
        private val card = CardTesteAbertas()
        private val catalogo = CatalogoDeCartoes(catalogoPadrao().cartoes + CartaoDeLeitura(meta, card) { mapOf("abertas" to it.n) })
    }

    private var ctx: AppContext? = null

    @AfterTest
    fun encerrar() {
        ctx?.close()
    }

    private fun rodar(
        issues: List<Trb1.IssueDeTeste> = Trb1.issues(),
        bloco: suspend (suspend (String?, String) -> br.sidia.painel.trabalho.RespTrb) -> Unit,
    ) {
        val c = novoContextoTrb1(banco, issues).also { ctx = it }
        rodarTrb1(
            c, catalogo,
            rotasExtras = {
                routing {
                    val ds = requireNotNull(c.dataSource)
                    rotaDeCard(c, SqliteWorkStore(ds), AccessLog(ds), card)
                }
            },
            bloco = bloco,
        )
    }

    private fun n(corpo: String) = Json.decodeFromString<ContagemTeste>(corpo).n

    @Test
    fun agregadoSoComOVisivel() = rodar { get ->
        assertEquals(1, n(get("ana", "/api/cards/teste-abertas").corpo), "USUARIO: só TG-101")
        assertEquals(2, n(get("lider.alfa", "/api/cards/teste-abertas").corpo), "SQUAD_LEADER: TG-101 e TG-102")
        assertEquals(4, n(get("gerente.exemplo", "/api/cards/teste-abertas").corpo))
        assertEquals(4, n(get("coord.um", "/api/cards/teste-abertas").corpo))
        assertEquals(1, n(get("carla", "/api/cards/teste-abertas").corpo), "carla vê só a TG-103")
    }

    @Test
    fun semResponsavelSoParaCoordenacao() = rodar { get ->
        val doCoord = Json.decodeFromString<DrillGenericoDto>(get("coord.um", "/api/cartoes/ZZ-01/tasks?segmento=abertas").corpo)
        assertTrue("TG-104" in doCoord.linhas.map { it.key })
        for (usuario in listOf("lider.alfa", "ana", "bruno", "carla")) {
            val dto = Json.decodeFromString<DrillGenericoDto>(get(usuario, "/api/cartoes/ZZ-01/tasks?segmento=abertas").corpo)
            assertFalse("TG-104" in dto.linhas.map { it.key }, "$usuario não deveria ver a fila sem dono")
        }
        // Com filtro de squad, nem a coordenação recebe a fila sem dono (o filtro é por pessoa do squad).
        assertEquals(2, n(get("gerente.exemplo", "/api/cards/teste-abertas?squad=Alfa").corpo))
    }

    @Test
    fun pessoaForaDoVisivel403() = rodar { get ->
        assertEquals(HttpStatusCode.Forbidden, get("ana", "/api/cards/teste-abertas?pessoa=bruno").status)
        assertEquals(HttpStatusCode.Forbidden, get("lider.alfa", "/api/cards/teste-abertas?pessoa=carla").status)
        assertEquals(HttpStatusCode.OK, get("lider.alfa", "/api/cards/teste-abertas?pessoa=bruno").status)
        assertEquals(1, n(get("gerente.exemplo", "/api/cards/teste-abertas?pessoa=carla").corpo))
        // Pessoa que não existe na base: coordenação recebe 0 (sem 403), usuário comum recebe 403.
        assertEquals(0, n(get("gerente.exemplo", "/api/cards/teste-abertas?pessoa=ninguem").corpo))
        assertEquals(HttpStatusCode.Forbidden, get("ana", "/api/cards/teste-abertas?pessoa=ninguem").status)
    }

    @Test
    fun squadInexistente400() = rodar { get ->
        assertEquals(HttpStatusCode.BadRequest, get("gerente.exemplo", "/api/cards/teste-abertas?squad=Inexistente").status)
        assertEquals(HttpStatusCode.BadRequest, get("gerente.exemplo", "/api/cards/teste-abertas?de=2026-13-01").status)
        assertEquals(HttpStatusCode.OK, get("gerente.exemplo", "/api/cards/teste-abertas?squad=Beta").status)
    }

    @Test
    fun segmentoDesconhecido400() = rodar { get ->
        val r = get("gerente.exemplo", "/api/cartoes/ZZ-01/tasks?segmento=outra-coisa")
        assertEquals(HttpStatusCode.BadRequest, r.status)
        assertEquals("SEGMENTO_INVALIDO", Json.decodeFromString<ErroDto>(r.corpo).codigo)
        assertEquals(4, Json.decodeFromString<DrillGenericoDto>(get("gerente.exemplo", "/api/cartoes/ZZ-01/tasks?segmento=abertas").corpo).total)
    }

    @Test
    fun statusSemMapaDa200ComAviso() = rodar(Trb1.issues() + Trb1.issue("TG-199", "Checklist", "ana", "10-20T12", "Revisao Exotica")) { get ->
        val r = get("gerente.exemplo", "/api/cards/teste-abertas")
        assertEquals(HttpStatusCode.OK, r.status, r.corpo)
        val dto = Json.decodeFromString<ContagemTeste>(r.corpo)
        assertEquals(0, dto.n)
        val aviso = dto.avisos.single()
        assertEquals(AvisoDto.STATUS_SEM_MAPEAMENTO, aviso.codigo)
        assertEquals(AvisoDto.BLOQUEANTE, aviso.gravidade)
        assertEquals("Revisao Exotica", aviso.params["status"])
        // O drill do mesmo cartão também não derruba: lista vazia, igual ao número (0).
        val drill = get("gerente.exemplo", "/api/cartoes/ZZ-01/tasks?segmento=abertas")
        assertEquals(HttpStatusCode.OK, drill.status)
        assertEquals(0, Json.decodeFromString<DrillGenericoDto>(drill.corpo).total)
    }

    @Test
    fun consultaGravaAccessLog() = rodar { get ->
        assertEquals(HttpStatusCode.OK, get("ana", "/api/cards/teste-abertas?tipo=Checklist").status)
        assertEquals(HttpStatusCode.BadRequest, get("ana", "/api/cards/teste-abertas?de=ruim").status)
        assertEquals(HttpStatusCode.Forbidden, get("ana", "/api/cards/teste-abertas?pessoa=bruno").status)
        val linhas = requireNotNull(ctx!!.dataSource).connection.use { c ->
            c.createStatement().executeQuery("SELECT user_id, route, filters FROM access_log ORDER BY id").use { rs ->
                buildList { while (rs.next()) add(Triple(rs.getString(1), rs.getString(2), rs.getString(3).orEmpty())) }
            }
        }
        assertEquals(3, linhas.size, linhas.toString())
        assertTrue(linhas.all { it.first == "ana" && it.second == "/api/cards/teste-abertas" })
        assertTrue("200" in linhas[0].third && "\"tipo\":\"Checklist\"" in linhas[0].third, linhas[0].third)
        assertTrue("400" in linhas[1].third, linhas[1].third)
        assertTrue("403" in linhas[2].third && "bruno" in linhas[2].third, linhas[2].third)
        // A auditoria guarda rota e filtros, nunca chave de issue.
        assertTrue(linhas.none { Regex("TG-\\d+").containsMatchIn(it.second + it.third) })
    }

    @Test
    fun semLoginRetorna401EPapelVazio403() = rodar { get ->
        assertEquals(HttpStatusCode.Unauthorized, get(null, "/api/cards/teste-abertas").status)
        // Principal autenticado mas fora do diretório de papéis: o controle de acesso nega.
        assertEquals(HttpStatusCode.Forbidden, get("desconhecido", "/api/cards/teste-abertas").status)
    }
}
