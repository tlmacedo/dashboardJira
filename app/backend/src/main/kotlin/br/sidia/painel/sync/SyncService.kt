package br.sidia.painel.sync

import br.sidia.painel.AppContext
import br.sidia.painel.db.Retention
import br.sidia.painel.jira.JiraException
import br.sidia.painel.jira.OrcamentoDeRequisicoes
import br.sidia.painel.jira.OrcamentoEsgotadoException
import br.sidia.painel.jira.TetoDeItensExcedidoException
import br.sidia.painel.jql.AtualizadoDesde
import br.sidia.painel.jql.CardQueryFactory
import br.sidia.painel.jql.JqlQuery
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

/** Resultado de uma tentativa de execução. */
sealed interface ResultadoExecucao {
    /** A execução rodou (com sucesso ou não) e ficou registrada em `sync_run`. */
    data class Executada(
        val runId: Long,
        val status: RunStatus,
        val resumo: RunResumo,
        val codigoErro: String?,
    ) : ResultadoExecucao

    /** O [SyncGate] barrou: nada foi pedido ao Jira e nenhuma linha foi gravada em `sync_run`. */
    data class Negada(val motivo: MotivoNegacao) : ResultadoExecucao

    /** Outra execução da mesma instância detém a trava. */
    data object Ocupada : ResultadoExecucao
}

/**
 * Orquestra a sincronização de uma instância: gate, trava entre réplicas, registro em `sync_run`,
 * cursor em `sync_state` e o [Syncer]. Sem cursor é a carga inicial (desde o limite de retenção);
 * com cursor é o incremental (cursor menos a sobreposição). Só códigos de erro são gravados ou logados,
 * nunca texto de exceção.
 */
class SyncService(
    private val ctx: AppContext,
    private val store: PostgresIssueStore,
    private val estado: SyncStateStore,
    private val gate: SyncGate,
    private val clock: Clock,
) {
    private val cfg get() = ctx.settings.sync

    /** Limite de retenção de hoje (data em Manaus, via fuso das configurações). */
    fun limiteDeRetencao(): Instant {
        val zona = ctx.settings.zone
        return Retention.limite(LocalDate.now(clock.withZone(zona)), ctx.settings.retentionMonths, zona)
    }

    suspend fun incremental(instance: String): ResultadoExecucao {
        val suspensa = io { estado.suspensa(instance) } != null
        val decisao = gate.avaliar(SyncKind.INCREMENTAL, instance, suspensa)
        if (decisao is GateDecision.Negada) return ResultadoExecucao.Negada(decisao.motivo)
        // iniciar() e concluir() ficam dentro da trava: nenhuma outra réplica fecha esta execução no meio.
        return estado.comTrava(instance) { executar(instance) } ?: ResultadoExecucao.Ocupada
    }

    /**
     * Reconciliação diária: compara a lista leve do Jira (chave, id e `updated` desde o corte de retenção) com as
     * issues ativas da base no mesmo universo. Renomeia as movidas (mesmo `jira_id`), marca como removidas as que
     * sumiram do Jira e rebusca, com changelog, as divergentes ou ausentes da base (isso também recupera issues que
     * a paginação por `startAt` pulou numa carga longa). Não mexe no cursor do incremental.
     * Se a remoção passaria de [SyncSettings.maxFracaoRemocao] da base local, ou o Jira vier vazio com base local,
     * aborta com `RECONCILIACAO_SUSPEITA` sem remover, renomear nem rebuscar nada.
     */
    suspend fun reconciliar(instance: String): ResultadoExecucao {
        val suspensa = io { estado.suspensa(instance) } != null
        val decisao = gate.avaliar(SyncKind.RECONCILIACAO, instance, suspensa)
        if (decisao is GateDecision.Negada) return ResultadoExecucao.Negada(decisao.motivo)
        return estado.comTrava(instance) {
            executar(instance, SyncKind.RECONCILIACAO) { orcamento, aoGravar -> reconciliarDados(instance, orcamento, aoGravar) }
        } ?: ResultadoExecucao.Ocupada
    }

    private suspend fun executar(instance: String): ResultadoExecucao =
        executar(instance, SyncKind.INCREMENTAL) { orcamento, aoGravar -> sincronizar(instance, orcamento, aoGravar) }

    private suspend fun executar(
        instance: String,
        kind: SyncKind,
        trabalho: suspend (OrcamentoDeRequisicoes, () -> Unit) -> RunResumo,
    ): ResultadoExecucao {
        val runId = io { estado.iniciar(instance, kind, clock.instant()) }
        val orcamento = OrcamentoDeRequisicoes(cfg.maxRequisicoes)
        var gravadas = 0
        try {
            val resumo = trabalho(orcamento) { gravadas++ }
            return fechar(runId, instance, kind, RunStatus.OK, resumo, null, null)
        } catch (e: CancellationException) {
            // Um timeout do próprio serviço (chamador ainda ativo) é falha; qualquer outro cancelamento é CANCELADA.
            if (e is TimeoutCancellationException && currentCoroutineContext().isActive) {
                return fechar(runId, instance, kind, RunStatus.FALHA, parcial(gravadas, orcamento), "TEMPO_ESGOTADO", null)
            }
            withContext(NonCancellable) {
                estado.concluir(runId, RunStatus.CANCELADA, parcial(gravadas, orcamento), "CANCELADA", null, clock.instant())
            }
            throw e
        } catch (e: Exception) {
            val (codigo, http) = classificar(e)
            return fechar(runId, instance, kind, RunStatus.FALHA, parcial(gravadas, orcamento), codigo, http)
        }
    }

    /** Fala com o Jira só pelo cliente da fábrica (que consulta a flag); o corte de retenção limita o universo. */
    private suspend fun reconciliarDados(
        instance: String,
        orcamento: OrcamentoDeRequisicoes,
        aoGravar: () -> Unit,
    ): RunResumo {
        val corte = limiteDeRetencao()
        val ficha = requireNotNull(ctx.fichas["sincronizacao"]) { "ficha 'sincronizacao' ausente" }
        val fabrica = CardQueryFactory(ctx.campos)
        val desde = AtualizadoDesde(corte, cfg.fusoDoServidor)
        return withTimeout(cfg.tempoMaximo) {
            withContext(orcamento) {
                val noJira = listaLeve(instance, fabrica.buildLeve(ficha, mapOf("desde" to desde)))
                val local = io { store.indice(instance, corte) }
                val plano = PlanoDeReconciliacao.montar(local, noJira)
                val limite = cfg.maxFracaoRemocao * local.size
                if (local.isNotEmpty() && (noJira.isEmpty() || plano.remover.size.toDouble() > limite)) {
                    throw ReconciliacaoSuspeitaException()
                }
                io {
                    plano.renomear.forEach { (jiraId, chave) -> store.renomear(instance, jiraId, chave) }
                }
                val removidas = io { store.marcarRemovidas(instance, plano.remover.map { it.id }.toSet(), clock.instant()) }
                var resumo = RunResumo(removidas = removidas)
                if (plano.rebuscar.isNotEmpty()) {
                    val syncer = criarSyncer(instance)
                    for (lote in plano.rebuscar.chunked(TAMANHO_DO_LOTE)) {
                        val query = fabrica.build(ficha, mapOf("desde" to desde, "chaves" to lote))
                        val r = syncer.run(query) { aoGravar() }
                        resumo = resumo.copy(
                            issues = resumo.issues + r.issues,
                            eventos = resumo.eventos + r.events,
                            incompletas = resumo.incompletas + r.incompletas,
                            usuariosNaoResolvidos = resumo.usuariosNaoResolvidos + r.usuariosNaoResolvidos,
                        )
                    }
                }
                io { estado.marcarReconciliacao(instance, clock.instant()) }
                resumo.copy(requisicoes = orcamento.usadas)
            }
        }
    }

    /** Percorre a consulta leve (só `key` e `updated`; o `id` vem no topo da issue). */
    private suspend fun listaLeve(instance: String, query: JqlQuery): List<ItemLeve> {
        val itens = ArrayList<ItemLeve>()
        ctx.jira.create(instance).searchAll(query, maxItens = cfg.maxItens).collect { issue ->
            val chave = (issue["key"] as? JsonPrimitive)?.content ?: throw DadoMalformadoException("Item da lista leve sem chave")
            val id = (issue["id"] as? JsonPrimitive)?.content?.toLongOrNull()
            val updated = try {
                ((issue["fields"] as? JsonObject)?.get("updated") as? JsonPrimitive)?.content?.let(ChangelogParser::parseInstant)
            } catch (e: IllegalArgumentException) {
                throw DadoMalformadoException("Issue $chave com updated malformado na lista leve")
            } catch (e: java.time.DateTimeException) {
                throw DadoMalformadoException("Issue $chave com updated malformado na lista leve")
            }
            itens += ItemLeve(chave, id, updated)
        }
        return itens
    }

    private fun criarSyncer(instance: String) = Syncer(
        client = ctx.jira.create(instance),
        store = store,
        instance = instance,
        retentionMonths = ctx.settings.retentionMonths,
        clock = clock,
        zone = ctx.settings.zone,
        campos = ctx.campos,
        chaves = estado,
        maxItens = cfg.maxItens,
    )

    /** Monta a consulta, roda o [Syncer] com orçamento e tempo máximo e avança o cursor a cada issue gravada. */
    private suspend fun sincronizar(
        instance: String,
        orcamento: OrcamentoDeRequisicoes,
        aoGravarUma: () -> Unit,
    ): RunResumo {
        val limite = limiteDeRetencao()
        val cursor = io { estado.cursor(instance) }
        val desde = maxOf((cursor ?: limite).minusMillis(cfg.sobreposicao.inWholeMilliseconds), limite)
        val ficha = requireNotNull(ctx.fichas["sincronizacao"]) { "ficha 'sincronizacao' ausente" }
        val query = CardQueryFactory(ctx.campos).build(ficha, mapOf("desde" to AtualizadoDesde(desde, cfg.fusoDoServidor)))
        val syncer = criarSyncer(instance)
        val resumo = withTimeout(cfg.tempoMaximo) {
            withContext(orcamento) {
                syncer.run(query) { g ->
                    // Só depois do commit da issue; o cursor nunca recua (GREATEST no banco).
                    g.updated?.let { io { estado.avancarCursor(instance, it) } }
                    aoGravarUma()
                }
            }
        }
        return RunResumo(
            issues = resumo.issues,
            eventos = resumo.events,
            requisicoes = orcamento.usadas,
            incompletas = resumo.incompletas,
            usuariosNaoResolvidos = resumo.usuariosNaoResolvidos,
        )
    }

    private fun parcial(gravadas: Int, orcamento: OrcamentoDeRequisicoes) =
        RunResumo(issues = gravadas, requisicoes = orcamento.usadas)

    /**
     * Registra o desfecho. Se `concluir` devolver false (a linha já foi fechada por outro caminho), não conta
     * sucesso nem falha e não suspende nada. Falha 401/403/429 suspende a instância; 3 falhas seguidas do
     * incremental também (falhas da reconciliação não suspendem: ela não pode travar o incremental).
     */
    private suspend fun fechar(
        runId: Long,
        instance: String,
        kind: SyncKind,
        status: RunStatus,
        resumo: RunResumo,
        codigo: String?,
        http: Int?,
    ): ResultadoExecucao {
        val gravada = io { estado.concluir(runId, status, resumo, codigo, http, clock.instant()) }
        if (!gravada) {
            log.warn("Execução {} da sincronização já estava fechada; desfecho descartado", runId)
            return ResultadoExecucao.Executada(runId, RunStatus.FALHA, resumo, "INTERROMPIDA")
        }
        if (status == RunStatus.FALHA && codigo != null) {
            log.warn("Sincronização falhou: código {}", codigo)
            val motivoDeSuspensao = when {
                http in STATUS_QUE_SUSPENDEM -> codigo
                kind == SyncKind.INCREMENTAL &&
                    io { estado.falhasSeguidas(instance, kind) } >= LIMITE_DE_FALHAS_SEGUIDAS -> "FALHAS_SEGUIDAS"
                else -> null
            }
            if (motivoDeSuspensao != null) {
                io { estado.suspender(instance, motivoDeSuspensao, clock.instant()) }
                log.warn("Instância suspensa até o reinício do app: código {}", motivoDeSuspensao)
            }
        }
        return ResultadoExecucao.Executada(runId, status, resumo, codigo)
    }

    /** Código de erro e status HTTP (se houver) de uma falha; nunca o texto da exceção. */
    private fun classificar(e: Throwable): Pair<String, Int?> = when (e) {
        is OrcamentoEsgotadoException -> "ORCAMENTO_REQUISICOES" to null
        is TetoDeItensExcedidoException -> "TETO_ITENS" to null
        is DadoMalformadoException -> "DADO_MALFORMADO" to null
        is ReconciliacaoSuspeitaException -> "RECONCILIACAO_SUSPEITA" to null
        // Violação de unicidade no banco (SQLState 23505): só o código, nunca o texto da exceção.
        is java.sql.SQLException -> (if (e.sqlState == "23505") "CONFLITO_DE_UNICIDADE" else "ERRO_INTERNO") to null
        is JiraException -> {
            val s = e.status
            val codigo = when {
                s == 0 -> "REDE"
                s in 200..299 -> "RESPOSTA_INVALIDA"
                s in 500..599 -> "HTTP_5XX"
                s in 100..599 -> "HTTP_$s"
                else -> "ERRO_INTERNO"
            }
            codigo to s.takeIf { it > 0 }
        }
        else -> "ERRO_INTERNO" to null
    }

    private suspend fun <T> io(bloco: () -> T): T = withContext(Dispatchers.IO) { bloco() }

    private companion object {
        val log = LoggerFactory.getLogger(SyncService::class.java)
        val STATUS_QUE_SUSPENDEM = setOf(401, 403, 429)
        const val LIMITE_DE_FALHAS_SEGUIDAS = 3

        /** Chaves por requisição na rebusca (`key in (...)`). */
        const val TAMANHO_DO_LOTE = 100
    }
}

/** A remoção passaria do limite da base local (ou o Jira veio vazio): nada é removido, renomeado nem rebuscado. */
internal class ReconciliacaoSuspeitaException : IllegalStateException("Reconciliação suspeita; nada foi alterado")

/** Item da lista leve do Jira. */
internal data class ItemLeve(val key: String, val jiraId: Long?, val updated: Instant?)

/** O que a reconciliação vai fazer, calculado antes de qualquer escrita. */
internal class PlanoDeReconciliacao(
    /** Pares (`jira_id`, nova chave) das issues movidas ou renomeadas. */
    val renomear: List<Pair<Long, String>>,
    /** Ativas locais que sumiram do Jira. */
    val remover: List<IndiceIssue>,
    /** Chaves a rebuscar com changelog: divergentes ou ausentes da base ativa. */
    val rebuscar: List<String>,
) {
    companion object {
        fun montar(local: List<IndiceIssue>, noJira: List<ItemLeve>): PlanoDeReconciliacao {
            val chavesDoJira = noJira.mapTo(HashSet()) { it.key }
            val chavePorIdDoJira = noJira.mapNotNull { i -> i.jiraId?.let { it to i.key } }.toMap()
            val sumidas = local.filter { it.key !in chavesDoJira }
            val renomear = sumidas.mapNotNull { l -> l.jiraId?.let { id -> chavePorIdDoJira[id]?.let { id to it } } }
            val renomeadas = renomear.map { it.first }.toSet()
            val remover = sumidas.filter { it.jiraId == null || it.jiraId !in renomeadas }
            // Updated local por chave depois das renomeações (a issue renomeada mantém o updated que já tinha).
            val updatedLocal = HashMap<String, Instant?>()
            local.filter { it.key in chavesDoJira }.forEach { updatedLocal[it.key] = it.updated }
            sumidas.forEach { l -> l.jiraId?.let { chavePorIdDoJira[it] }?.let { updatedLocal[it] = l.updated } }
            val rebuscar = noJira.filter { j ->
                if (j.key !in updatedLocal) {
                    true
                } else {
                    val u = updatedLocal.getValue(j.key)
                    j.updated != null && (u == null || j.updated > u)
                }
            }.map { it.key }.sorted()
            return PlanoDeReconciliacao(renomear, remover, rebuscar)
        }
    }
}
