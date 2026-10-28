package br.sidia.painel.sync

import br.sidia.painel.AppContext
import br.sidia.painel.db.CacheDeLeituras
import br.sidia.painel.db.Retention
import br.sidia.painel.jira.JiraException
import br.sidia.painel.jira.OrcamentoDeRequisicoes
import br.sidia.painel.jira.OrcamentoEsgotadoException
import br.sidia.painel.jira.TetoDeItensExcedidoException
import br.sidia.painel.jql.AtualizadoDesde
import br.sidia.painel.jql.CardQueryFactory
import br.sidia.painel.jql.CardSpec
import br.sidia.painel.jql.JqlQuery
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
    private val store: SqliteIssueStore,
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

    suspend fun incremental(instance: String): ResultadoExecucao =
        comGate(SyncKind.INCREMENTAL, instance) { executar(instance) }

    /**
     * Flag e instância são checadas ANTES de qualquer acesso ao banco (flag desligada = nem o banco é tocado).
     * A suspensão é lida de novo DENTRO da trava: outra réplica pode ter suspendido a instância enquanto esperávamos.
     * iniciar() e concluir() ficam dentro da trava: nenhuma outra réplica fecha esta execução no meio.
     */
    private suspend fun comGate(kind: SyncKind, instance: String, trabalho: suspend () -> ResultadoExecucao): ResultadoExecucao {
        val previa = gate.avaliar(kind, instance, suspensa = false)
        if (previa is GateDecision.Negada) return ResultadoExecucao.Negada(previa.motivo)
        return estado.comTrava(instance) {
            val decisao = gate.avaliar(kind, instance, io { estado.suspensa(instance) } != null)
            if (decisao is GateDecision.Negada) ResultadoExecucao.Negada(decisao.motivo) else trabalho()
        } ?: ResultadoExecucao.Ocupada
    }

    /**
     * Reconciliação diária: compara a lista leve do Jira (chave, id e `updated` desde o corte de retenção) com as
     * issues ativas da base no mesmo universo. Renomeia as movidas (mesmo `jira_id`), marca como removidas as que
     * sumiram do Jira e rebusca, com changelog, as divergentes ou ausentes da base (isso também recupera issues que
     * a paginação por `startAt` pulou numa carga longa). Não mexe no cursor do incremental.
     * Se a remoção passaria de [SyncSettings.maxFracaoRemocao] da base local, ou o Jira vier vazio com base local,
     * aborta com `RECONCILIACAO_SUSPEITA` sem remover, renomear nem rebuscar nada.
     */
    suspend fun reconciliar(instance: String): ResultadoExecucao =
        comGate(SyncKind.RECONCILIACAO, instance) {
            executar(instance, SyncKind.RECONCILIACAO) { orcamento, progresso -> reconciliarDados(instance, orcamento, progresso) }
        }

    private suspend fun executar(instance: String): ResultadoExecucao =
        executar(instance, SyncKind.INCREMENTAL) { orcamento, progresso -> sincronizar(instance, orcamento, progresso) }

    private suspend fun executar(
        instance: String,
        kind: SyncKind,
        trabalho: suspend (OrcamentoDeRequisicoes, Progresso) -> RunResumo,
    ): ResultadoExecucao {
        val runId = io { estado.iniciar(instance, kind, clock.instant()) }
        val orcamento = OrcamentoDeRequisicoes(cfg.maxRequisicoes)
        val progresso = Progresso()
        try {
            val resumo = trabalho(orcamento, progresso)
            return fechar(runId, instance, kind, RunStatus.OK, resumo, null, null)
        } catch (e: CancellationException) {
            // Um timeout do próprio serviço (chamador ainda ativo) é falha; qualquer outro cancelamento é CANCELADA.
            if (e is TimeoutCancellationException && currentCoroutineContext().isActive) {
                return fechar(runId, instance, kind, RunStatus.FALHA, progresso.resumo(orcamento), "TEMPO_ESGOTADO", null)
            }
            withContext(NonCancellable) {
                estado.concluir(runId, RunStatus.CANCELADA, progresso.resumo(orcamento), "CANCELADA", null, clock.instant())
            }
            throw e
        } catch (e: Exception) {
            val (codigo, http) = classificar(e)
            // Só a CLASSE da exceção (nunca a mensagem, que pode ter valor do Jira) nos erros sem código próprio.
            if (codigo == "ERRO_INTERNO" || codigo == "DADO_MALFORMADO") {
                log.warn("Sincronização falhou ({}): exceção {}", codigo, e::class.simpleName)
            }
            return fechar(runId, instance, kind, RunStatus.FALHA, progresso.resumo(orcamento), codigo, http)
        } finally {
            // Qualquer saída (cancelamento, Error, falha ao fechar) deixa o cache de leituras sem o dado anterior à gravação.
            CacheDeLeituras.invalidar()
        }
    }

    /** Fala com o Jira só pelo cliente da fábrica (que consulta a flag); o corte de retenção limita o universo. */
    private suspend fun reconciliarDados(
        instance: String,
        orcamento: OrcamentoDeRequisicoes,
        progresso: Progresso,
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
                // A paginação por startAt pode ter pulado uma issue: antes de marcar, confirma por chave no Jira.
                val confirmadas = confirmarRemocoes(instance, ficha, fabrica, desde, plano.remover)
                val removidas = io { store.marcarRemovidas(instance, confirmadas.map { it.id }.toSet(), clock.instant()) }
                progresso.removidas = removidas
                var resumo = RunResumo(removidas = removidas)
                if (plano.rebuscar.isNotEmpty()) {
                    val syncer = criarSyncer(instance)
                    for (lote in plano.rebuscar.chunked(TAMANHO_DO_LOTE)) {
                        val query = fabrica.build(ficha, mapOf("desde" to desde, "chaves" to lote))
                        val r = syncer.run(query) { g -> progresso.somar(g) }
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

    /**
     * Dos candidatos a remoção, devolve só os que o Jira NÃO traz ao consultar por chave (`key in (...)`, lotes de
     * [TAMANHO_DO_LOTE], com a mesma ficha). O que o Jira ainda traz fica na base, ativo.
     */
    private suspend fun confirmarRemocoes(
        instance: String,
        ficha: CardSpec,
        fabrica: CardQueryFactory,
        desde: AtualizadoDesde,
        candidatos: List<IndiceIssue>,
    ): List<IndiceIssue> {
        if (candidatos.isEmpty()) return candidatos
        val presentes = HashSet<String>()
        suspend fun consultar(chaves: List<String>) {
            val query = fabrica.buildLeve(ficha, mapOf("desde" to desde, "chaves" to chaves))
            ctx.jira.create(instance).searchAll(query, maxItens = cfg.maxItens).collect { issue ->
                (issue["key"] as? JsonPrimitive)?.content?.let { presentes += it }
            }
        }
        for (lote in candidatos.map { it.key }.chunked(TAMANHO_DO_LOTE)) {
            try {
                consultar(lote)
            } catch (e: JiraException) {
                // O Jira DC responde 400 a um `key in (...)` que cita chave inexistente (issue apagada): confirma uma a uma.
                // 400 ou 404 numa chave sozinha = ausente. Qualquer outro erro (401/403/429/5xx/rede/orçamento) sobe.
                if (e.status != 400) throw e
                for (chave in lote) {
                    try {
                        consultar(listOf(chave))
                    } catch (individual: JiraException) {
                        if (individual.status != 400 && individual.status != 404) throw individual
                    }
                }
            }
        }
        return candidatos.filter { it.key !in presentes }
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
        progresso: Progresso,
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
                    progresso.somar(g)
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

    /** Totais já gravados da execução em curso; é o resumo parcial quando ela termina em falha ou cancelamento. */
    private class Progresso {
        var issues = 0
        var eventos = 0
        var incompletas = 0
        var removidas = 0
        val naoResolvidos = HashSet<String>()

        fun somar(g: IssueGravada) {
            issues++
            eventos += g.eventos
            if (g.incompleta) incompletas++
            naoResolvidos += g.naoResolvidos
        }

        fun resumo(orcamento: OrcamentoDeRequisicoes) = RunResumo(
            issues = issues, eventos = eventos, requisicoes = orcamento.usadas, incompletas = incompletas,
            usuariosNaoResolvidos = naoResolvidos.size, removidas = removidas,
        )
    }

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
        // O que a execução gravou (inclusive a que falhou no meio) já pode ser lido: os cards recarregam na próxima requisição.
        CacheDeLeituras.invalidar()
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
        // Violação de unicidade no banco (SQLite: UNIQUE ou PRIMARY KEY): só o código, nunca o texto da exceção.
        is java.sql.SQLException -> (if (violacaoDeUnicidade(e)) "CONFLITO_DE_UNICIDADE" else "ERRO_INTERNO") to null
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

private fun violacaoDeUnicidade(e: java.sql.SQLException): Boolean {
    val codigo = (e as? org.sqlite.SQLiteException)?.resultCode ?: return false
    return codigo == org.sqlite.SQLiteErrorCode.SQLITE_CONSTRAINT_UNIQUE ||
        codigo == org.sqlite.SQLiteErrorCode.SQLITE_CONSTRAINT_PRIMARYKEY
}
