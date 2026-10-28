package br.sidia.painel.inicio

import br.sidia.painel.AppContext
import br.sidia.painel.cards.AvisoDto
import br.sidia.painel.cards.INSTANCIA_TRABALHO
import br.sidia.painel.cards.ROTA_SENSIVEL
import br.sidia.painel.cards.ResultadoDeCard
import br.sidia.painel.cards.calcularSeguro
import br.sidia.painel.cards.carga.CardCarga
import br.sidia.painel.cards.carga.CargaDto
import br.sidia.painel.cards.contrato.CatalogoDeCartoes
import br.sidia.painel.cards.contrato.EntradaDeCartao
import br.sidia.painel.cards.executarAuditado
import br.sidia.painel.cards.exigirPapel
import br.sidia.painel.cards.hoje
import br.sidia.painel.cards.montarEntrada
import br.sidia.painel.cards.paraDto
import br.sidia.painel.cards.previsao.CardPrevisao
import br.sidia.painel.cards.previsao.PrevisaoDto
import br.sidia.painel.db.AccessLog
import br.sidia.painel.domain.UnmappedStatusException
import br.sidia.painel.pessoas.Jain
import br.sidia.painel.pessoas.Ocupacao
import br.sidia.painel.security.Principal
import br.sidia.painel.security.Role
import br.sidia.painel.sync.FiltroDeFicha
import br.sidia.painel.sync.SqliteIssueStore
import br.sidia.painel.trabalho.ConsultaDeTrabalho
import br.sidia.painel.trabalho.Fluxo
import br.sidia.painel.trabalho.Situacao
import br.sidia.painel.trabalho.SqliteWorkStore
import io.ktor.http.Parameters
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import java.time.LocalDate

private val ALTA = setOf(Role.COORDENADOR, Role.GERENTE)

/** Dias à frente em que uma ausência conta para o item "ausências" da Central. */
private const val JANELA_DE_AUSENCIAS_DIAS = 14L

/**
 * As fontes da Central em produção: os números vêm dos cartões `VG-nn` do catálogo (que chamam os cartões de destino, com uma
 * memória por requisição para cada card ser calculado uma vez) e o resto, direto dos cards e das configurações. Uma instância
 * vale para uma requisição de um [principal].
 */
internal class FontesDoPainel(
    private val ctx: AppContext,
    private val catalogo: CatalogoDeCartoes,
    private val principal: Principal,
) : FontesDaCentral {
    private val ds = requireNotNull(ctx.dataSource)
    private val memo = mutableMapOf<String, Any>()
    private val entrada = EntradaDeCartao(ctx, SqliteIssueStore(ds), principal, Parameters.Empty, memo)
    private val hoje: LocalDate = hoje(ctx)

    override val metaNoPrazo: Double get() = ctx.regrasDeMetas.metaNoPrazo
    override val faixaAtencaoPp: Double get() = ctx.regrasDeMetas.faixaAtencaoPp

    override suspend fun valoresDe(cartao: String): Map<String, Int> = catalogo.porId(cartao)?.valores(entrada).orEmpty()

    /** Um card calculado para quem pergunta, sem filtros; reaproveita o cálculo que um cartão já tenha feito nesta requisição. */
    @Suppress("UNCHECKED_CAST")
    private fun <D : Any> dtoDe(card: br.sidia.painel.cards.CardDeLeitura<D>): D =
        ((memo[card.id] as? ResultadoDeCard<D>) ?: card.calcularSeguro(
            montarEntrada(ctx, SqliteWorkStore(ds), principal, Parameters.Empty, card.periodoPadraoDias),
        ).also { memo[card.id] = it }).dto

    override suspend fun universoDeEstimativa(): Int? =
        dtoDe(br.sidia.painel.governanca.CardQualidade).linhas.firstOrNull { it.verificacao == "SEM_ESTIMATIVA" }?.universo

    override suspend fun ausentesEm14Dias(): Int =
        ctx.ausencias.entre(hoje, hoje.plusDays(JANELA_DE_AUSENCIAS_DIAS)).map { it.pessoa }.toSet().size

    override suspend fun previsao(): PrevisaoDaCentral? {
        val dto: PrevisaoDto = dtoDe(CardPrevisao)
        if (dto.avisos.any { it.gravidade == AvisoDto.BLOQUEANTE }) return null
        return PrevisaoDaCentral(dto.backlog, dto.termina, dto.confianca)
    }

    override suspend fun squads(): List<CartaoSquadDto> {
        val carga: CargaDto = dtoDe(CardCarga)
        val regras = ctx.regrasPlanejamento
        return carga.linhas.mapNotNull { l -> ctx.roles.squadOf(l.pessoa, hoje)?.let { it to l } }
            .groupBy({ it.first }, { it.second })
            .map { (squad, linhas) ->
                val ocupacoes = linhas.map { l -> l.ocupacao.fracao?.let { Ocupacao.Valor(it) } ?: Ocupacao.SemCapacidade }
                val comCapacidade = ocupacoes.filterIsInstance<Ocupacao.Valor>()
                val media = if (comCapacidade.isEmpty()) Ocupacao.SemCapacidade else Ocupacao.Valor(comCapacidade.map { it.fracao }.average())
                CartaoSquadDto(squad, media.paraDto(regras), Jain.indice(ocupacoes, regras.tetoJain), linhas.size)
            }
            .sortedBy { it.squad }
    }

    override suspend fun avisos(): List<AvisoDto> {
        val ficha = FiltroDeFicha.de(ctx.fichas.getValue("negligenciadas"))
        val abertas = SqliteWorkStore(ds).snapshots(ConsultaDeTrabalho(INSTANCIA_TRABALHO, ficha, Situacao.ABERTAS))
        return try {
            Fluxo.exigirStatusMapeados(ctx.statusMap, abertas)
            emptyList()
        } catch (x: UnmappedStatusException) {
            listOf(
                AvisoDto(
                    AvisoDto.STATUS_SEM_MAPEAMENTO, AvisoDto.BLOQUEANTE, "status sem mapeamento: ${x.status}", mapOf("status" to x.status),
                ),
            )
        }
    }
}

/**
 * `GET /api/central` (COORDENADOR e GERENTE): a Central de atenção. Auditada como qualquer rota de dados (200, 403 e 503 sem
 * dados). Se o item `INDICIO_ALTO` entra (só gerente, com a trava do DPO aberta), a rota é marcada como sensível no registro de
 * acesso. Quem não é coordenação nem gerente recebe 403; o usuário comum e o Squad Leader têm o Meu painel e a Visão do squad.
 */
fun Route.rotasDeInicio(ctx: AppContext, catalogo: CatalogoDeCartoes, auditoria: AccessLog) {
    get("/api/central") {
        call.executarAuditado(auditoria, ctx) { principal ->
            exigirPapel(ctx, principal, ALTA)
            val dto = CentralCalculator(FontesDoPainel(ctx, catalogo, principal)).calcular()
            if (dto.itens.any { it.item == ItemCentral.INDICIO_ALTO.name }) call.attributes.put(ROTA_SENSIVEL, true)
            dto
        }
    }
}
