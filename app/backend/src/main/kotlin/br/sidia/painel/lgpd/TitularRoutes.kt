package br.sidia.painel.lgpd

import br.sidia.painel.AppContext
import br.sidia.painel.cards.ErroHttp
import br.sidia.painel.cards.ROTA_SENSIVEL
import br.sidia.painel.cards.executarAuditado
import br.sidia.painel.cards.papeisVigentes
import br.sidia.painel.db.AccessLog
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import java.sql.Connection
import java.time.Duration

/** Código do aviso fixo: correção e eliminação se pedem pelo canal do DPO (os dados vêm do Jira e se corrigem lá). */
internal const val AVISO_CANAL_DO_DPO = "SOLICITACOES_PELO_CANAL_DO_DPO"

/** Janela dos acessos mostrados ao titular. */
private const val DIAS_DE_ACESSOS = 90L

/** No máximo tantos acessos na resposta (os mais recentes). */
private const val MAX_ACESSOS = 200

/** Código de erro quando a consulta traz QUALQUER parâmetro: nenhum parâmetro escolhe outra pessoa. */
internal const val CODIGO_PARAMETRO_NAO_ACEITO = "PARAMETRO_NAO_ACEITO"

/**
 * `GET /api/titular/meus-dados` (todos os papéis): o direito de acesso do titular. A pessoa é SEMPRE o principal da chamada; a rota
 * não aceita parâmetro nenhum (400 `PARAMETRO_NAO_ACEITO`), então ninguém consulta os dados de outra pessoa pela URL. Não passa pela
 * trava do DPO (é direito do titular) e a própria consulta é registrada no `access_log`. Contagens por `COUNT`, sem carregar issues
 * de ninguém; sem o conteúdo de sinais de Auditoria ou Integridade.
 */
fun Route.rotasDoTitular(ctx: AppContext, auditoria: AccessLog) {
    get("/api/titular/meus-dados") {
        // Sensível no registro: nenhum parâmetro vai para o access_log, nem a tentativa de pedir os dados de outra pessoa.
        call.attributes.put(ROTA_SENSIVEL, true)
        call.executarAuditado(auditoria, ctx) { principal ->
            if (papeisVigentes(ctx, principal).isEmpty()) throw ErroHttp(HttpStatusCode.Forbidden)
            if (!call.request.queryParameters.isEmpty()) throw ErroHttp(HttpStatusCode.BadRequest, CODIGO_PARAMETRO_NAO_ACEITO)
            montarMeusDados(ctx, auditoria, principal.userId)
        }
    }
}

private fun montarMeusDados(ctx: AppContext, auditoria: AccessLog, pessoa: String): MeusDadosDto {
    val agora = ctx.clock.instant()
    val ds = requireNotNull(ctx.dataSource)
    val contagens = ds.connection.use { c ->
        object {
            val responsavel = contar(c, "SELECT count(*) FROM issue WHERE assignee_ref = ? AND removida_em IS NULL", pessoa)
            val reporter = contar(c, "SELECT count(*) FROM issue WHERE reporter_ref = ? AND removida_em IS NULL", pessoa)
            val alteracoes = contar(c, "SELECT count(*) FROM change_event WHERE author_ref = ?", pessoa)
            val worklogs = contar(c, "SELECT count(*) FROM worklog WHERE author_ref = ?", pessoa)
            val segundos = contar(c, "SELECT COALESCE(SUM(seconds), 0) FROM worklog WHERE author_ref = ?", pessoa)
            val decisoes = contar(c, "SELECT count(*) FROM decisao_distribuicao WHERE sugerido = ? OR escolhido = ? OR decidido_por = ?", pessoa, pessoa, pessoa)
        }
    }
    val acessos = auditoria.listar(agora.minus(Duration.ofDays(DIAS_DE_ACESSOS)), agora.plusSeconds(1), pessoa, null, MAX_ACESSOS, null)
        .map { (_, l) -> AcessoProprioDto(l.route, l.at.toString()) }
    val retencao = ctx.settings.retencao
    return MeusDadosDto(
        pessoa = pessoa,
        geradoEm = agora.toString(),
        papeis = ctx.roles.atribuicoesDe(pessoa).map { PapelVigenteDto(it.papel.name, it.squad, it.vigencia.de?.toString(), it.vigencia.ate?.toString()) },
        issuesComoResponsavel = contagens.responsavel.toInt(),
        issuesComoReporter = contagens.reporter.toInt(),
        alteracoesComoAutor = contagens.alteracoes.toInt(),
        worklogs = contagens.worklogs.toInt(),
        horasRegistradas = contagens.segundos / SEGUNDOS_POR_HORA,
        ausenciasPlanejadas = ctx.ausencias.daPessoa(pessoa).size,
        decisoesEnvolvendo = contagens.decisoes.toInt(),
        acessos = acessos,
        categorias = CATEGORIAS_TRATADAS,
        retencao = mapOf(
            "historico" to retencao.historicoMeses, "accessLog" to retencao.accessLogMeses,
            "decisoes" to retencao.decisoesMeses, "execucoes" to retencao.execucoesMeses,
        ),
        aviso = AVISO_CANAL_DO_DPO,
    )
}

private const val SEGUNDOS_POR_HORA = 3600.0

private fun contar(c: Connection, sql: String, vararg params: String): Long =
    c.prepareStatement(sql).use { ps ->
        params.forEachIndexed { n, p -> ps.setString(n + 1, p) }
        ps.executeQuery().use { it.next(); it.getLong(1) }
    }

/** As categorias tratadas, na ordem em que o relatório do DPO as descreve. Sem o conteúdo de nenhum sinal. */
private val CATEGORIAS_TRATADAS = listOf(
    CategoriaTratadaDto(
        "TEMPO_EM_STATUS", "Medir o fluxo das tasks: quanto tempo cada uma fica em cada status",
        listOf("USUARIO", "SQUAD_LEADER", "COORDENADOR", "GERENTE"), "RETENTION_HISTORICO_MONTHS",
    ),
    CategoriaTratadaDto(
        "CARDS_DE_TRABALHO", "Acompanhar prazos, riscos, entregas e metas das tasks",
        listOf("USUARIO", "SQUAD_LEADER", "COORDENADOR", "GERENTE"), "RETENTION_HISTORICO_MONTHS",
    ),
    CategoriaTratadaDto(
        "CARGA_E_AUSENCIAS", "Planejar a carga e a capacidade do time, considerando ausências",
        listOf("USUARIO", "SQUAD_LEADER", "COORDENADOR", "GERENTE"), "RETENTION_HISTORICO_MONTHS",
    ),
    CategoriaTratadaDto(
        "AUDITORIA", "Conferir quem alterou prazo, responsável e labels de atraso, como indício para revisão humana",
        listOf("COORDENADOR", "GERENTE"), "RETENTION_HISTORICO_MONTHS",
    ),
    CategoriaTratadaDto(
        "INTEGRIDADE", "Conferir padrões de fechamento de tasks, como indício para revisão humana",
        listOf("GERENTE"), "RETENTION_HISTORICO_MONTHS",
    ),
    CategoriaTratadaDto(
        "DECISOES", "Registrar as decisões humanas sobre sugestões de distribuição e rebalanceamento (as sugestões não são decisão automática)",
        listOf("COORDENADOR", "GERENTE"), "RETENTION_DECISOES_MONTHS",
    ),
)
