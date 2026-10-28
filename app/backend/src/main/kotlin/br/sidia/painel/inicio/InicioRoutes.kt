package br.sidia.painel.inicio

import br.sidia.painel.AppContext
import br.sidia.painel.cards.AvisoDto
import br.sidia.painel.cards.ErroHttp
import br.sidia.painel.cards.INSTANCIA_TRABALHO
import br.sidia.painel.cards.capacidade.AusenciaDto
import br.sidia.painel.cards.papeisVigentes
import br.sidia.painel.pessoas.MotorDePessoas
import io.ktor.http.HttpStatusCode
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

/** Um card calculado para [principal] com os [parametros] dados (filtros comuns; 400 e 403 como na rota do próprio card). */
private fun <D : Any> calcularCard(
    ctx: AppContext, principal: Principal, card: br.sidia.painel.cards.CardDeLeitura<D>, parametros: Parameters,
): D = card.calcularSeguro(
    montarEntrada(ctx, SqliteWorkStore(requireNotNull(ctx.dataSource)), principal, parametros, card.periodoPadraoDias),
).dto

private fun parametros(vararg pares: Pair<String, String>): Parameters = Parameters.build { pares.forEach { (k, v) -> append(k, v) } }

/** Aviso bloqueante (status fora do mapa) entre os avisos dos cards; eles já devolvem os números zerados e o aviso. */
private fun bloqueado(avisos: List<AvisoDto>): Boolean = avisos.any { it.gravidade == AvisoDto.BLOQUEANTE }

/** Avisos sem repetição (o mesmo código com os mesmos parâmetros), na ordem em que apareceram. */
private fun List<AvisoDto>.semRepetir(): List<AvisoDto> = distinctBy { it.codigo to it.params }

/**
 * O squad da Visão do squad: o Squad Leader vê o squad que lidera (o único, ou o primeiro em ordem alfabética se lidera mais de
 * um; `squad=` escolhe entre os dele e outro squad é 403); coordenação e gerente veem qualquer squad (sem `squad=`, o primeiro
 * em ordem alfabética; squad desconhecido é 400). USUARIO não tem esta visão (403).
 */
internal fun squadDaVisao(ctx: AppContext, principal: Principal, pedido: String?): String {
    val hoje = hoje(ctx)
    val papeis = papeisVigentes(ctx, principal)
    val nome = pedido?.trim()?.takeIf { it.isNotEmpty() }
    val todos = papeis.any { it == Role.COORDENADOR || it == Role.GERENTE }
    if (!todos && Role.SQUAD_LEADER !in papeis) throw ErroHttp(HttpStatusCode.Forbidden)
    if (todos) {
        val squads = ctx.roles.squadsEm(hoje).sorted()
        if (nome == null) return squads.firstOrNull() ?: throw ErroHttp(HttpStatusCode.NotFound)
        return nome.takeIf { it in squads } ?: throw ErroHttp(HttpStatusCode.BadRequest)
    }
    val seus = ctx.roles.squadsLedBy(principal.userId, hoje).sorted()
    if (nome == null) return seus.firstOrNull() ?: throw ErroHttp(HttpStatusCode.Forbidden)
    return nome.takeIf { it in seus } ?: throw ErroHttp(HttpStatusCode.Forbidden)
}

/**
 * `GET /api/squad?squad=` (SQUAD_LEADER, COORDENADOR e GERENTE): a Visão do squad. Os números vêm dos cards Carga, Vencimentos,
 * Capacidade e Riscos com `squad=<nome>`, pelo escopo de quem pergunta; nada é recalculado aqui. Auditada.
 */
fun Route.rotaDoSquad(ctx: AppContext, auditoria: AccessLog) {
    get("/api/squad") {
        call.executarAuditado(auditoria, ctx) { principal ->
            val squad = squadDaVisao(ctx, principal, call.request.queryParameters["squad"])
            val filtro = parametros("squad" to squad)
            val carga = calcularCard(ctx, principal, CardCarga, filtro)
            val vencimentos = calcularCard(ctx, principal, br.sidia.painel.cards.vencimentos.CardVencimentos, parametros("squad" to squad, "dias" to "7"))
            val capacidade = calcularCard(ctx, principal, br.sidia.painel.cards.capacidade.CardCapacidade, filtro)
            val riscos = calcularCard(ctx, principal, br.sidia.painel.cards.riscos.CardRiscos, filtro)
            SquadDto(
                squad = squad,
                carga = carga.linhas.sortedBy { it.pessoa },
                vencimentos = vencimentos,
                ausencias = capacidade.ausencias,
                riscoAlto = riscos.alto,
                riscoMedio = riscos.medio,
                avisos = (carga.avisos + vencimentos.avisos + capacidade.avisos + riscos.avisos).semRepetir(),
            )
        }
    }
}

/**
 * `GET /api/meu-painel` (todos os papéis): o painel do próprio usuário. Não há parâmetro `pessoa` para os papéis comuns (é
 * ignorado); o Squad Leader pode pedir uma pessoa do squad dele (403 fora dele). Auditada.
 */
fun Route.rotaDoMeuPainel(ctx: AppContext, auditoria: AccessLog) {
    get("/api/meu-painel") {
        call.executarAuditado(auditoria, ctx) { principal ->
            val alvo = alvoDoMeuPainel(ctx, principal, call.request.queryParameters["pessoa"])
            montarMeuPainel(ctx, principal, alvo)
        }
    }
}

/** Dias à frente das "tasks que vencem" do Meu painel (o mesmo `dias=7` do cartão VE-01). */
private const val DIAS_QUE_VENCEM = "7"

private fun montarMeuPainel(ctx: AppContext, principal: Principal, alvo: String): MeuPainelDto {
    val hoje = hoje(ctx)
    val dePessoa = parametros("pessoa" to alvo)
    val carga = calcularCard(ctx, principal, CardCarga, dePessoa)
    val vencimentos = calcularCard(ctx, principal, br.sidia.painel.cards.vencimentos.CardVencimentos, parametros("pessoa" to alvo, "dias" to DIAS_QUE_VENCEM))
    val riscos = calcularCard(ctx, principal, br.sidia.painel.cards.riscos.CardRiscos, dePessoa)
    val esforco = calcularCard(
        ctx, principal, br.sidia.painel.cards.esforco.CardEsforco,
        parametros("pessoa" to alvo, "de" to hoje.withDayOfMonth(1).toString(), "ate" to hoje.toString()),
    )
    val avisos = (carga.avisos + vencimentos.avisos + riscos.avisos + esforco.avisos).semRepetir()
    val travado = bloqueado(avisos)
    val linha = carga.linhas.firstOrNull { it.pessoa == alvo }

    // A fila segue a agenda (a mesma ordem e o mesmo término do Riscos e da Previsão) e cada task leva o nível de risco do card.
    val fila = if (travado) {
        emptyList()
    } else {
        val ficha = FiltroDeFicha.de(ctx.fichas.getValue("carga"))
        val abertas = SqliteWorkStore(requireNotNull(ctx.dataSource)).snapshots(ConsultaDeTrabalho(INSTANCIA_TRABALHO, ficha, Situacao.ABERTAS))
            .filter { it.assignee == alvo }
        val porChave = abertas.associateBy { it.key }
        val nivel = riscos.linhas.associate { it.key to it.nivel }
        MotorDePessoas(ctx).agenda.agendar(alvo, abertas, hoje).map { a ->
            ItemFilaDto(a.key, porChave.getValue(a.key).summary, a.vencimento?.toString(), a.termino?.toString(), a.atrasa, nivel[a.key] ?: "baixo")
        }
    }
    val meuEsforco = esforco.pessoas.firstOrNull { it.pessoa == alvo }
    return MeuPainelDto(
        pessoa = alvo,
        minhasAbertas = if (travado) null else linha?.abertas ?: 0,
        vencemEm7d = if (travado) null else vencimentos.vencem,
        emRisco = if (travado) null else riscos.sinaisPorPessoa.firstOrNull { it.pessoa == alvo }?.issuesEmRisco ?: 0,
        ocupacao4Sem = linha?.ocupacao,
        ausenciasFuturas = ctx.ausencias.daPessoa(alvo).filter { !it.ate.isBefore(hoje) }.sortedBy { it.de }
            .map { AusenciaDto(it.pessoa, it.de.toString(), it.ate.toString(), it.fracao) },
        fila = fila,
        esforcoDoMes = meuEsforco?.let { EsforcoResumoDto(it.pesoTipo, it.testes, it.horas, it.indice, esforco.versaoRegras) },
        avisos = avisos,
    )
}

/**
 * `GET /api/central` (COORDENADOR e GERENTE): a Central de atenção. Auditada como qualquer rota de dados (200, 403 e 503 sem
 * dados). Se o item `INDICIO_ALTO` entra (só gerente, com a trava do DPO aberta), a rota é marcada como sensível no registro de
 * acesso. Quem não é coordenação nem gerente recebe 403; o usuário comum e o Squad Leader têm o Meu painel e a Visão do squad.
 */
fun Route.rotasDeInicio(ctx: AppContext, catalogo: CatalogoDeCartoes, auditoria: AccessLog) {
    rotaDoSquad(ctx, auditoria)
    rotaDoMeuPainel(ctx, auditoria)
    get("/api/central") {
        call.executarAuditado(auditoria, ctx) { principal ->
            exigirPapel(ctx, principal, ALTA)
            val dto = CentralCalculator(FontesDoPainel(ctx, catalogo, principal)).calcular()
            if (dto.itens.any { it.item == ItemCentral.INDICIO_ALTO.name }) call.attributes.put(ROTA_SENSIVEL, true)
            dto
        }
    }
}
