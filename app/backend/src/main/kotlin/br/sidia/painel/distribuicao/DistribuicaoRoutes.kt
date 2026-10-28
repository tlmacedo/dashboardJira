package br.sidia.painel.distribuicao

import br.sidia.painel.AppContext
import br.sidia.painel.cards.AvisoDto
import br.sidia.painel.cards.EXTRAS_DE_AUDITORIA
import br.sidia.painel.cards.ErroHttp
import br.sidia.painel.cards.INSTANCIA_TRABALHO
import br.sidia.painel.cards.data
import br.sidia.painel.cards.executarAuditado
import br.sidia.painel.cards.exigirPapel
import br.sidia.painel.cards.hoje
import br.sidia.painel.cards.papeisVigentes
import br.sidia.painel.cards.usernameValido
import br.sidia.painel.db.AccessLog
import br.sidia.painel.domain.UnmappedStatusException
import br.sidia.painel.security.Principal
import br.sidia.painel.security.Role
import br.sidia.painel.trabalho.SqliteWorkStore
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.contentLength
import io.ktor.server.request.receiveText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.time.LocalDate

private val LIDERANCA = setOf(Role.SQUAD_LEADER, Role.COORDENADOR, Role.GERENTE)
private val COORDENACAO = setOf(Role.COORDENADOR, Role.GERENTE)

/** Tamanho máximo do corpo do POST (o motivo tem até 500 caracteres); acima disso o pedido é recusado. */
private const val MAX_CORPO = 8_192
private const val MAX_MOTIVO = 500
private const val PERIODO_PADRAO_DIAS = 30L
private const val PERIODO_MAXIMO_DIAS = 366L

private val FORMATO_DE_CHAVE = Regex("[A-Za-z][A-Za-z0-9_]{0,31}-\\d{1,9}")

/** Códigos estáveis das respostas de erro desta tela (o texto sai do código, no idioma de quem vê). */
object CodigosDeDistribuicao {
    const val JA_DECIDIDA = "JA_DECIDIDA"
    const val SUGESTAO_MUDOU = "SUGESTAO_MUDOU"
    const val DADOS_INDISPONIVEIS = "DADOS_INDISPONIVEIS"
    const val CORPO_INVALIDO = "CORPO_INVALIDO"
    const val MOTIVO_OBRIGATORIO = "MOTIVO_OBRIGATORIO"
    const val MOTIVO_LONGO = "MOTIVO_LONGO"
    const val ESCOLHIDO_INVALIDO = "ESCOLHIDO_INVALIDO"
    const val SEM_SUGESTAO = "SEM_SUGESTAO"
}

/**
 * Rotas da Distribuição e do Rebalanceamento (MODO SOMBRA: sugerem; nada é escrito no Jira) e do registro de decisões:
 *
 * - `GET /api/distribuicao` e `GET /api/rebalanceamento`: liderança (líder de squad, coordenador, gerente). O líder de squad só
 *   tem candidatos, origens e destinos do próprio squad.
 * - `POST /api/distribuicao/decisao`: grava a decisão (aprovar, trocar, rejeitar) na tabela do PAINEL. O único POST de dados.
 * - `GET /api/governanca/decisoes?de=&ate=`: o registro de decisões (AU-01), coordenação e gerente, sem agregar por pessoa.
 *
 * A ordem das travas é sempre: papel (403) → corpo e parâmetros (400) → visão (403) → regra (409). TODA chamada, inclusive a
 * negada ou malformada, vai para o `access_log` ANTES da resposta; o POST guarda ali só `tipo` e `decisao` (nunca o motivo, a
 * task nem as pessoas), e falha ao gravar é 503 sem dados.
 */
fun Route.rotasDeDistribuicao(ctx: AppContext, auditoria: AccessLog) {
    val ds = requireNotNull(ctx.dataSource)
    val servico = DistribuicaoServico(ctx, SqliteWorkStore(ds))
    val decisoes = SqliteDecisaoStore(ds)

    get("/api/distribuicao") {
        call.executarAuditado(auditoria, ctx) { principal ->
            exigirPapel(ctx, principal, LIDERANCA)
            val a = analisarOuNulo(servico, ctx, principal)
            if (a == null) {
                DistribuicaoDto(versaoRegras = ctx.regrasDistribuicao.rotulo, aguardando = 0, sugestoesPendentes = 0, jainAntes = null, jainDepois = null,
                    sugestoes = emptyList(), candidatos = emptyList(), avisos = listOf(avisoDeStatus()))
            } else {
                val chaves = ChavesDoDia.de(ctx, a, decisoes)
                montarDistribuicao(a, chaves)
            }
        }
    }

    get("/api/rebalanceamento") {
        call.executarAuditado(auditoria, ctx) { principal ->
            exigirPapel(ctx, principal, LIDERANCA)
            val a = analisarOuNulo(servico, ctx, principal)
            val regras = ctx.regrasDistribuicao
            if (a == null) {
                RebalanceamentoDto(versaoRegras = regras.rotulo, gatilho = regras.gatilhoRebalanceamento, histerese = regras.histereseRebalanceamentoPp,
                    movimentos = emptyList(), movimentosPendentes = 0, candidatos = emptyList(), avisos = listOf(avisoDeStatus()))
            } else {
                montarRebalanceamento(ctx, a, ChavesDoDia.de(ctx, a, decisoes))
            }
        }
    }

    post("/api/distribuicao/decisao") {
        call.executarAuditado(auditoria, ctx) { principal ->
            exigirPapel(ctx, principal, LIDERANCA)
            val req = lerCorpo(call.request.contentLength(), call.receiveText())
            val tipo = req.tipo.takeIf { it in TipoDeSugestao.todos } ?: throw ErroHttp(HttpStatusCode.BadRequest, CodigosDeDistribuicao.CORPO_INVALIDO)
            val decisao = Decisao.entries.firstOrNull { it.name == req.decisao } ?: throw ErroHttp(HttpStatusCode.BadRequest, CodigosDeDistribuicao.CORPO_INVALIDO)
            // Daqui em diante `tipo` e `decisao` são da lista fechada: é só isso que a auditoria guarda deste POST.
            call.attributes.put(EXTRAS_DE_AUDITORIA, mapOf("tipo" to tipo, "decisao" to decisao.name))
            val chaveDaTask = req.issueKey.trim().takeIf { FORMATO_DE_CHAVE.matches(it) }?.uppercase()
                ?: throw ErroHttp(HttpStatusCode.BadRequest, CodigosDeDistribuicao.CORPO_INVALIDO)
            val motivo = sanearMotivo(req.motivo)
            if (decisao == Decisao.TROCADA && motivo == null) throw ErroHttp(HttpStatusCode.BadRequest, CodigosDeDistribuicao.MOTIVO_OBRIGATORIO)
            val sugeridoPedido = req.sugerido?.takeIf { it.isNotBlank() }?.let(::usernameValido)
            val escolhidoPedido = req.escolhido?.takeIf { it.isNotBlank() }?.let(::usernameValido)

            val hoje = hoje(ctx)
            val a = analisarOuNulo(servico, ctx, principal)
                ?: throw ErroHttp(HttpStatusCode.UnprocessableEntity, CodigosDeDistribuicao.DADOS_INDISPONIVEIS)
            // A sugestão que o servidor calcula AGORA, para quem pede: a task precisa estar nela. O líder de squad nunca sabe se a task
            // existe fora da visão (403); coordenação e gerente veem tudo, então o que não está na lista é "não encontrada".
            val (sugeridoDoServidor, origem) = when (tipo) {
                TipoDeSugestao.DISTRIBUICAO -> a.sugestoes[chaveDaTask]?.let { it.sugerido to null }
                else -> a.movimentoPorTask[chaveDaTask]?.let { it.para to it.de }
            } ?: throw ErroHttp(if (papeisVigentes(ctx, principal).any { it in COORDENACAO }) HttpStatusCode.NotFound else HttpStatusCode.Forbidden)

            if (sugeridoPedido != sugeridoDoServidor) throw ErroHttp(HttpStatusCode.Conflict, CodigosDeDistribuicao.SUGESTAO_MUDOU)
            val escolhido: String? = when (decisao) {
                Decisao.APROVADA -> {
                    if (sugeridoDoServidor == null) throw ErroHttp(HttpStatusCode.BadRequest, CodigosDeDistribuicao.SEM_SUGESTAO)
                    if (escolhidoPedido != null && escolhidoPedido != sugeridoDoServidor) throw ErroHttp(HttpStatusCode.BadRequest, CodigosDeDistribuicao.ESCOLHIDO_INVALIDO)
                    sugeridoDoServidor
                }
                Decisao.REJEITADA -> {
                    if (escolhidoPedido != null) throw ErroHttp(HttpStatusCode.BadRequest, CodigosDeDistribuicao.ESCOLHIDO_INVALIDO)
                    null
                }
                Decisao.TROCADA -> {
                    val e = escolhidoPedido ?: throw ErroHttp(HttpStatusCode.BadRequest, CodigosDeDistribuicao.ESCOLHIDO_INVALIDO)
                    // Fora da visão (ou desconhecido): 403, sem dizer qual. Na visão mas sem capacidade, igual à sugerida ou à origem: 400.
                    if (e !in a.visiveis) throw ErroHttp(HttpStatusCode.Forbidden)
                    if (e !in a.candidatos || e == sugeridoDoServidor || e == origem) throw ErroHttp(HttpStatusCode.BadRequest, CodigosDeDistribuicao.ESCOLHIDO_INVALIDO)
                    e
                }
            }

            val chave = ChaveDaSugestao.de(INSTANCIA_TRABALHO, tipo, chaveDaTask, sugeridoDoServidor, a.versaoRegras, hoje)
            val gravou = decisoes.registrar(
                INSTANCIA_TRABALHO,
                RegistroDecisao(tipo, chaveDaTask, sugeridoDoServidor, escolhido, decisao, motivo, principal.userId, ctx.clock.instant(), a.versaoRegras),
                chave,
            )
            if (!gravou) throw ErroHttp(HttpStatusCode.Conflict, CodigosDeDistribuicao.JA_DECIDIDA)
            DecisaoRegistradaDto(tipo, chaveDaTask, decisao.name, escolhido)
        }
    }

    get("/api/governanca/decisoes") {
        call.executarAuditado(auditoria, ctx) { principal ->
            exigirPapel(ctx, principal, COORDENACAO)
            val params = call.request.queryParameters
            val ate = data(params, "ate") ?: hoje(ctx)
            val de = data(params, "de") ?: ate.minusDays(PERIODO_PADRAO_DIAS)
            if (de.isAfter(ate) || de.plusDays(PERIODO_MAXIMO_DIAS - 1).isBefore(ate)) throw ErroHttp(HttpStatusCode.BadRequest)
            val zona = ctx.settings.zone
            val limite = DecisaoStore.LIMITE_PADRAO
            val achadas = decisoes.listar(
                INSTANCIA_TRABALHO, de.atStartOfDay(zona).toInstant(), ate.plusDays(1).atStartOfDay(zona).toInstant(), null, limite + 1,
            )
            DecisoesDto(
                de = de.toString(), ate = ate.toString(), truncado = achadas.size > limite,
                decisoes = achadas.take(limite).map {
                    DecisaoListadaDto(it.tipo, it.issueKey, it.sugerido, it.escolhido, it.decisao.name, it.motivo, it.decididoPor, it.decididoEm.toString(), it.versaoRegras)
                },
            )
        }
    }
}

private fun analisarOuNulo(servico: DistribuicaoServico, ctx: AppContext, principal: Principal): AnaliseDeDistribuicao? =
    try {
        servico.analisar(principal, hoje(ctx), ctx.clock.instant())
    } catch (e: UnmappedStatusException) {
        null
    }

private fun avisoDeStatus() =
    AvisoDto(AvisoDto.STATUS_SEM_MAPEAMENTO, AvisoDto.BLOQUEANTE, "status sem mapeamento: a distribuição fica indisponível")

/** Lê o corpo do POST: tamanho limitado e JSON estrito (chave desconhecida é 400). Nunca ecoa o que recebeu. */
private fun lerCorpo(tamanhoDeclarado: Long?, texto: String): DecisaoRequest {
    if ((tamanhoDeclarado ?: 0L) > MAX_CORPO || texto.length > MAX_CORPO) throw ErroHttp(HttpStatusCode.PayloadTooLarge, CodigosDeDistribuicao.CORPO_INVALIDO)
    return try {
        Json.decodeFromString(DecisaoRequest.serializer(), texto)
    } catch (e: SerializationException) {
        throw ErroHttp(HttpStatusCode.BadRequest, CodigosDeDistribuicao.CORPO_INVALIDO)
    } catch (e: IllegalArgumentException) {
        throw ErroHttp(HttpStatusCode.BadRequest, CodigosDeDistribuicao.CORPO_INVALIDO)
    }
}

/**
 * Motivo saneado: controle e caracteres de formatação (inclusive os que invertem o sentido do texto) viram espaço, espaços
 * repetidos viram um, as pontas são cortadas. Vazio vira nulo. Mais de 500 caracteres (pontos de código) é 400.
 */
internal fun sanearMotivo(bruto: String?): String? {
    if (bruto == null) return null
    val limpo = buildString {
        for (cp in bruto.codePoints().toArray()) {
            val tipo = Character.getType(cp)
            val ruim = Character.isISOControl(cp) || tipo == Character.FORMAT.toInt() || tipo == Character.LINE_SEPARATOR.toInt() ||
                tipo == Character.PARAGRAPH_SEPARATOR.toInt() || tipo == Character.PRIVATE_USE.toInt() || tipo == Character.SURROGATE.toInt()
            appendCodePoint(if (ruim || Character.isWhitespace(cp)) ' '.code else cp)
        }
    }.replace(Regex(" {2,}"), " ").trim()
    if (limpo.isEmpty()) return null
    if (limpo.codePointCount(0, limpo.length) > MAX_MOTIVO) throw ErroHttp(HttpStatusCode.BadRequest, CodigosDeDistribuicao.MOTIVO_LONGO)
    return limpo
}

// ---- Montagem das respostas ----

/**
 * As chaves de sugestão do dia (uma por task e tipo) e o que já foi decidido sobre cada uma. Quem monta uma resposta e quem
 * valida um POST usam a MESMA conta, então a tela e o servidor nunca discordam de qual sugestão é qual.
 */
internal class ChavesDoDia(val distribuicao: Map<String, String>, val rebalanceamento: Map<String, String>, val decididas: Map<String, Decisao>) {
    companion object {
        fun de(ctx: AppContext, a: AnaliseDeDistribuicao, decisoes: DecisaoStore): ChavesDoDia {
            val dia: LocalDate = hoje(ctx)
            val dist = a.resultado.sugestoes.associate { s ->
                s.issueKey to ChaveDaSugestao.de(INSTANCIA_TRABALHO, TipoDeSugestao.DISTRIBUICAO, s.issueKey, s.sugerido, a.versaoRegras, dia)
            }
            val reb = a.movimentos.associate { m ->
                m.issueKey to ChaveDaSugestao.de(INSTANCIA_TRABALHO, TipoDeSugestao.REBALANCEAMENTO, m.issueKey, m.para, a.versaoRegras, dia)
            }
            return ChavesDoDia(dist, reb, decisoes.decididas(INSTANCIA_TRABALHO, dist.values + reb.values))
        }
    }
}

internal fun montarDistribuicao(a: AnaliseDeDistribuicao, chaves: ChavesDoDia): DistribuicaoDto {
    val sugestoes = a.pendentes.map { p ->
        val s = a.sugestoes.getValue(p.key)
        SugestaoDto(
            issueKey = p.key, resumo = p.summary, horas = s.horas, horasAssumidas = p.key in a.horasAssumidas, vence = p.duedate?.toString(),
            comGerente = p.assignee != null, sugerido = s.sugerido,
            fatores = s.fatores.map { FatoresDto(it.pessoa, it.carga, it.wip, it.wipCheio, it.competencias, it.bonus, it.score) },
            decisao = chaves.decididas[chaves.distribuicao.getValue(p.key)]?.name,
        )
    }
    return DistribuicaoDto(
        versaoRegras = a.versaoRegras, aguardando = a.pendentes.size,
        sugestoesPendentes = sugestoes.count { it.sugerido != null && it.decisao == null },
        jainAntes = a.resultado.jainAntes, jainDepois = a.resultado.jainDepois, sugestoes = sugestoes, candidatos = a.candidatos, avisos = a.avisos,
    )
}

internal fun montarRebalanceamento(ctx: AppContext, a: AnaliseDeDistribuicao, chaves: ChavesDoDia): RebalanceamentoDto {
    val regras = ctx.regrasDistribuicao
    val movimentos = a.movimentos.map { m ->
        MovimentoDto(
            m.issueKey, a.tasks.getValue(m.issueKey).summary, m.de, m.para, m.horas, m.ocupacaoOrigemAntes, m.ocupacaoOrigemDepois,
            m.ocupacaoDestinoDepois, chaves.decididas[chaves.rebalanceamento.getValue(m.issueKey)]?.name,
        )
    }
    return RebalanceamentoDto(
        versaoRegras = a.versaoRegras, gatilho = regras.gatilhoRebalanceamento, histerese = regras.histereseRebalanceamentoPp,
        movimentos = movimentos, movimentosPendentes = movimentos.count { it.decisao == null }, candidatos = a.candidatos, avisos = a.avisos,
    )
}
