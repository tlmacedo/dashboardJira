package br.sidia.painel.cards

import br.sidia.painel.AppContext
import br.sidia.painel.db.AccessLog
import br.sidia.painel.security.Principal
import br.sidia.painel.security.Role
import br.sidia.painel.security.Visibility
import br.sidia.painel.sync.CamposDoDicionario
import br.sidia.painel.trabalho.ConsultaDeTrabalho
import br.sidia.painel.trabalho.IssueSnapshot
import br.sidia.painel.trabalho.Situacao
import br.sidia.painel.trabalho.SqliteWorkStore
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import java.time.Duration
import java.util.Locale

/** Janela das opções: issues abertas ou resolvidas nos últimos 180 dias. */
private const val JANELA_DIAS = 180L

/** Máximo de valores por lista; o excedente é cortado com o aviso `OPCOES_TRUNCADAS`. */
internal const val LIMITE_DE_OPCOES = 200

/**
 * `GET /api/filtros/opcoes`: squads, pessoas, tipos, prioridades, OS Version e valores dos campos do dicionário, só do que
 * o principal enxerga (o mesmo escopo dos cards). Nada aqui revela pessoa nem valor de fora da visão. Auditada como
 * qualquer rota de dados.
 */
fun Route.rotasDeOpcoesDeFiltro(ctx: AppContext, auditoria: AccessLog) {
    val store = SqliteWorkStore(requireNotNull(ctx.dataSource))
    get("/api/filtros/opcoes") {
        call.executarAuditado(auditoria, ctx) { principal ->
            exigirPapel(ctx, principal, Role.entries.toSet())
            opcoesDe(ctx, store, principal)
        }
    }
}

private fun opcoesDe(ctx: AppContext, store: SqliteWorkStore, principal: Principal): OpcoesDeFiltroDto {
    val hoje = hoje(ctx)
    val agora = ctx.clock.instant()
    val todos = store.pessoas(INSTANCIA_TRABALHO) + ctx.equipes.todos.map { it.id }
    val visiveis = Visibility.visibleUserIds(principal, ctx.roles, hoje, todos)
    val papeis = papeisVigentes(ctx, principal)
    val coordenacao = Role.COORDENADOR in papeis || Role.GERENTE in papeis
    val escopo = Escopo(visiveis, veSemResponsavel = coordenacao)

    val squads: Set<String> = if (coordenacao) {
        ctx.roles.squadsEm(hoje)
    } else {
        buildSet {
            ctx.roles.squadOf(principal.userId, hoje)?.let(::add)
            addAll(ctx.roles.squadsLedBy(principal.userId, hoje))
        }
    }

    val janela = agora.minus(Duration.ofDays(JANELA_DIAS))
    val issues: List<IssueSnapshot> =
        store.snapshots(ConsultaDeTrabalho(INSTANCIA_TRABALHO, null, Situacao.ABERTAS)) +
            store.snapshots(ConsultaDeTrabalho(INSTANCIA_TRABALHO, null, Situacao.RESOLVIDAS, resolvidasDe = janela))
    val noEscopo = issues.filter { escopo.permiteAtual(it) }

    val truncadas = mutableListOf<String>()
    fun lista(nome: String, valores: Sequence<String>): List<String> {
        val unicos = valoresUnicos(valores)
        if (unicos.size > LIMITE_DE_OPCOES) truncadas += nome
        return unicos.take(LIMITE_DE_OPCOES)
    }

    val campos = CamposDoDicionario.deFiltro(ctx.campos, INSTANCIA_TRABALHO).map { c ->
        CampoOpcoesDto(
            c.nome, c.slug,
            lista(c.slug, noEscopo.asSequence().mapNotNull { it.camposDicionario[c.nome] }.flatMap { CamposDoDicionario.partesDe(it) }),
        )
    }
    val avisos = truncadas.map {
        AvisoDto(
            AvisoDto.OPCOES_TRUNCADAS, AvisoDto.INFORMATIVO, "lista de opções cortada em $LIMITE_DE_OPCOES",
            mapOf("lista" to it, "limite" to LIMITE_DE_OPCOES.toString()),
        )
    }
    return OpcoesDeFiltroDto(
        squads = lista("squad", squads.asSequence()),
        pessoas = lista("pessoa", visiveis.asSequence()),
        tipos = lista("tipo", noEscopo.asSequence().map { it.issueType }),
        prioridades = lista("prioridade", noEscopo.asSequence().mapNotNull { it.priority }),
        os = lista("os", noEscopo.asSequence().mapNotNull { it.osVersion }),
        campos = campos,
        avisos = avisos,
        rapidos = FiltrosRapidos.ler(ctx.settings.configDir, campos.map { it.slug }.toSet()),
        prefixosDeChave = lista("prefixo", noEscopo.asSequence().map { it.key.substringBefore('-') }),
    )
}

/** Sem vazios, sem repetir o que só difere na caixa ou nos espaços (fica a primeira grafia em ordem alfabética). */
internal fun valoresUnicos(valores: Sequence<String>): List<String> =
    valores.map { it.trim() }.filter { it.isNotEmpty() }
        .sortedWith(compareBy<String>({ it.lowercase(Locale.ROOT) }, { it }))
        .distinctBy { it.lowercase(Locale.ROOT) }
        .toList()
