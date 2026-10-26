package br.sidia.painel.sync

import br.sidia.painel.AppContext
import br.sidia.painel.db.Retention
import br.sidia.painel.jira.JiraException
import br.sidia.painel.jira.OrcamentoDeRequisicoes
import br.sidia.painel.jira.OrcamentoEsgotadoException
import br.sidia.painel.jira.TetoDeItensExcedidoException
import br.sidia.painel.jql.AtualizadoDesde
import br.sidia.painel.jql.CardQueryFactory
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

    private suspend fun executar(instance: String): ResultadoExecucao {
        val runId = io { estado.iniciar(instance, SyncKind.INCREMENTAL, clock.instant()) }
        val orcamento = OrcamentoDeRequisicoes(cfg.maxRequisicoes)
        var gravadas = 0
        try {
            val resumo = sincronizar(instance, orcamento) { gravadas++ }
            return fechar(runId, instance, RunStatus.OK, resumo, null, null)
        } catch (e: CancellationException) {
            // Um timeout do próprio serviço (chamador ainda ativo) é falha; qualquer outro cancelamento é CANCELADA.
            if (e is TimeoutCancellationException && currentCoroutineContext().isActive) {
                return fechar(runId, instance, RunStatus.FALHA, parcial(gravadas, orcamento), "TEMPO_ESGOTADO", null)
            }
            withContext(NonCancellable) {
                estado.concluir(runId, RunStatus.CANCELADA, parcial(gravadas, orcamento), "CANCELADA", null, clock.instant())
            }
            throw e
        } catch (e: Exception) {
            val (codigo, http) = classificar(e)
            return fechar(runId, instance, RunStatus.FALHA, parcial(gravadas, orcamento), codigo, http)
        }
    }

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
        val syncer = Syncer(
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
     * sucesso nem falha e não suspende nada. Falha 401/403/429 suspende a instância; 3 falhas seguidas também.
     */
    private suspend fun fechar(
        runId: Long,
        instance: String,
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
                io { estado.falhasSeguidas(instance, SyncKind.INCREMENTAL) } >= LIMITE_DE_FALHAS_SEGUIDAS -> "FALHAS_SEGUIDAS"
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
    }
}
