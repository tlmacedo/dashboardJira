package br.sidia.painel.cards

import br.sidia.painel.AppContext
import br.sidia.painel.cards.contrato.CartaoMeta
import br.sidia.painel.cards.contrato.CartaoRegistrado
import br.sidia.painel.cards.contrato.EntradaDeCartao
import br.sidia.painel.cards.contrato.LinhaDeDrill
import br.sidia.painel.db.AccessLog
import br.sidia.painel.domain.UnmappedStatusException
import br.sidia.painel.security.Principal
import br.sidia.painel.security.Role
import br.sidia.painel.trabalho.IssueSnapshot
import br.sidia.painel.trabalho.SqliteWorkStore
import br.sidia.painel.trabalho.WorkStore
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import java.time.Instant
import java.time.LocalDate

/** Instância lida pelos cards de trabalho e prazos: a mesma do Tempo em status (a ficha de sincronização do time). */
internal const val INSTANCIA_TRABALHO = INSTANCIA_TEMPO_EM_STATUS

/** Tudo que um card precisa para calcular: quem pergunta, os filtros, o escopo já resolvido, o relógio e o banco. */
class EntradaDeCard(
    val principal: Principal,
    val filtros: FiltrosDeCard,
    val escopo: Escopo,
    val agora: Instant,
    val hoje: LocalDate,
    val ctx: AppContext,
    val store: WorkStore,
    /** Os parâmetros brutos da URL, para o card ler os seus próprios (ex.: `granularidade`); sempre validar (400). */
    val parametros: Parameters = Parameters.Empty,
)

/**
 * O que um cálculo entrega: o [dto] da resposta do card e [detalhar], que devolve as tasks de um segmento (o MESMO
 * conjunto que formou o número, com os mesmos filtros) ou `null` quando o segmento não é deste card (400).
 */
class ResultadoDeCard<D : Any>(val dto: D, val detalhar: (String) -> List<DrillRow>?)

/**
 * Card de leitura: um cálculo puro sobre o [WorkStore] filtrado pelo [Escopo]. A rota `GET /api/cards/{id}` serve o
 * [dto][ResultadoDeCard.dto] e as tasks por trás de cada número saem do drill genérico dos cartões registrados
 * ([CartaoDeLeitura]), então número e drill nascem do mesmo cálculo.
 */
interface CardDeLeitura<D : Any> {
    /** Id da ficha (`app/config/cards/<id>.card.yaml`) e da rota `/api/cards/<id>`; também a `tela` dos cartões. */
    val id: String

    /** Período padrão em dias de Manaus (`ate` inclusivo); `null` = card sem período. */
    val periodoPadraoDias: Int?

    fun calcular(e: EntradaDeCard): ResultadoDeCard<D>

    /** Resposta quando o status não está no mapa: números zerados e [avisos] (um deles bloqueante). */
    fun vazio(avisos: List<AvisoDto>): D
}

/** Linha da gaveta: o responsável só aparece se estiver na visão de quem pediu. */
fun linhaDe(s: IssueSnapshot, escopo: Escopo, detalhe: String): DrillRow =
    DrillRow(s.key, s.summary, s.assignee?.takeIf { it in escopo.visiveis }, s.status, detalhe)

/** Calcula; status fora do mapa vira resposta 200 com `STATUS_SEM_MAPEAMENTO` bloqueante e drill vazio (nunca 500). */
internal fun <D : Any> CardDeLeitura<D>.calcularSeguro(e: EntradaDeCard): ResultadoDeCard<D> =
    try {
        calcular(e)
    } catch (x: UnmappedStatusException) {
        val aviso = AvisoDto(
            AvisoDto.STATUS_SEM_MAPEAMENTO, AvisoDto.BLOQUEANTE, "status sem mapeamento: ${x.status}",
            mapOf("status" to x.status),
        )
        ResultadoDeCard(vazio(listOf(aviso))) { emptyList() }
    }

/** Papéis vigentes de quem pediu cruzados com os que veem o card; sem nenhum, 403. */
internal fun exigirPapel(ctx: AppContext, principal: Principal, papeis: Set<Role>) {
    if (papeisVigentes(ctx, principal).none { it in papeis }) throw ErroHttp(HttpStatusCode.Forbidden)
}

/** Monta a entrada de um card: papel, filtros (400), escopo (400 e 403) e relógio do painel. */
internal fun montarEntrada(
    ctx: AppContext, store: WorkStore, principal: Principal, params: Parameters, periodoPadraoDias: Int?,
): EntradaDeCard {
    val hoje = hoje(ctx)
    val filtros = FiltrosDeCard.ler(params, hoje, periodoPadraoDias)
    // A equipe cadastrada (equipes.yaml) também existe: quem não tem task ainda precisa aparecer nos cards de pessoas.
    val escopo = escopoDe(principal, ctx, hoje, filtros, store.pessoas(INSTANCIA_TRABALHO) + ctx.equipes.todos.map { it.id })
    return EntradaDeCard(principal, filtros, escopo, ctx.clock.instant(), hoje, ctx, store, params)
}

/**
 * `GET /api/cards/{id}`: aplica os filtros comuns, o escopo pela visibilidade e o `access_log` (inclusive 400 e 403;
 * sem registro não há resposta com dados). A rota grava o PADRÃO da rota e os filtros, nunca chaves de issue.
 */
internal inline fun <reified D : Any> Route.rotaDeCard(
    ctx: AppContext,
    store: WorkStore,
    auditoria: AccessLog,
    card: CardDeLeitura<D>,
    papeis: Set<Role> = Role.entries.toSet(),
) {
    get("/api/cards/${card.id}") {
        call.executarAuditado(auditoria, ctx) { principal ->
            exigirPapel(ctx, principal, papeis)
            card.calcularSeguro(montarEntrada(ctx, store, principal, call.request.queryParameters, card.periodoPadraoDias)).dto
        }
    }
}

/**
 * Cartão do contrato (17a) sobre um [CardDeLeitura]: [numeros] tira os números por segmento do DTO e o drill usa o
 * `detalhar` do MESMO cálculo, com os mesmos parâmetros, então o número do cartão é o total do drill.
 */
internal class CartaoDeLeitura<D : Any>(
    override val meta: CartaoMeta,
    private val card: CardDeLeitura<D>,
    /** O segmento é deste cartão? (cada cartão só abre os seus números; outro segmento é 400). */
    private val aceita: (String) -> Boolean = { true },
    private val numeros: (D) -> Map<String, Int>,
) : CartaoRegistrado {
    override val instancia: String = INSTANCIA_TRABALHO

    @Suppress("UNCHECKED_CAST")
    private fun calcular(e: EntradaDeCartao): ResultadoDeCard<D> {
        e.memo?.get(card.id)?.let { return it as ResultadoDeCard<D> }
        val store = SqliteWorkStore(requireNotNull(e.ctx.dataSource))
        val resultado = card.calcularSeguro(montarEntrada(e.ctx, store, e.principal, e.parametros, card.periodoPadraoDias))
        e.memo?.put(card.id, resultado)
        return resultado
    }

    override suspend fun valores(e: EntradaDeCartao): Map<String, Int> = numeros(calcular(e).dto)

    override suspend fun drill(e: EntradaDeCartao, segmento: String): List<LinhaDeDrill> {
        if (!aceita(segmento)) throw ErroHttp(HttpStatusCode.BadRequest, CODIGO_SEGMENTO_INVALIDO)
        return calcular(e).detalhar(segmento) ?: throw ErroHttp(HttpStatusCode.BadRequest, CODIGO_SEGMENTO_INVALIDO)
    }
}
