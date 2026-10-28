package br.sidia.painel.sync

import br.sidia.painel.AppContext
import br.sidia.painel.db.Retention
import br.sidia.painel.operacao.EstadoComMetricas
import br.sidia.painel.operacao.Metricas
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

/**
 * Uma tarefa periódica. [instance] é nula na retenção (só banco local). [devida] decide, só com o relógio e a
 * última execução do mesmo tipo, se é hora de rodar; [executar] faz o trabalho e registra o próprio desfecho.
 */
data class TarefaAgendada(
    val kind: SyncKind,
    val instance: String?,
    val devida: (agora: Instant, ultima: RegistroDeExecucao?) -> Boolean,
    val executar: suspend () -> Unit,
)

/** Regras de calendário das tarefas, puras (sem relógio, banco ou rede). */
object Agenda {
    /** Devida quando nunca rodou ou já passou o [intervalo] desde o INÍCIO da última execução. */
    fun incrementalDevida(agora: Instant, ultima: RegistroDeExecucao?, intervalo: Duration): Boolean =
        ultima == null || agora >= ultima.startedAt.plus(intervalo.toJavaDuration())

    /**
     * Uma vez por dia civil de [zone]: devida se já passou [hora] no dia de [agora] e a última execução não
     * foi nesse mesmo dia. Se o app estava fora do ar na hora marcada, roda no primeiro ciclo depois dela.
     */
    fun diariaDevida(agora: Instant, ultima: RegistroDeExecucao?, hora: LocalTime, zone: ZoneId): Boolean {
        val local = agora.atZone(zone)
        if (local.toLocalTime() < hora) return false
        return ultima == null || ultima.startedAt.atZone(zone).toLocalDate() < local.toLocalDate()
    }
}

/**
 * Executa as [tarefas] devidas, em sequência, a cada [passo]. Um único job por agendador (cancelável em
 * [parar]), sem sobreposição de ciclos, e NENHUMA exceção de uma tarefa derruba o processo nem para as outras.
 * Só a classe da exceção é logada, nunca a mensagem. O agendador não conhece o Jira nem a flag: quem monta
 * a lista de tarefas decide (ver [montarAgendador]) e o `SyncService` ainda reavalia o gate a cada execução.
 */
class Agendador(
    private val tarefas: List<TarefaAgendada>,
    private val estado: SyncStateStore,
    private val clock: Clock,
    private val passo: Duration = 1.minutes,
    private val dormir: suspend (Duration) -> Unit = { delay(it) },
) {
    val tipos: List<SyncKind> = tarefas.map { it.kind }

    private val trava = Mutex()
    @Volatile
    private var job: Job? = null

    /** Verdadeiro entre [iniciar] e o fim do job. */
    val emExecucao: Boolean get() = job?.isActive == true

    /** Inicia o laço no [scope]. Só pode ser chamado uma vez. */
    @Synchronized
    fun iniciar(scope: CoroutineScope): Job {
        check(job == null) { "O agendador já foi iniciado" }
        return scope.launch {
            while (isActive) {
                ciclo()
                dormir(passo)
            }
        }.also { job = it }
    }

    /** Roda as tarefas devidas. Se outro ciclo está em andamento, retorna sem fazer nada. */
    suspend fun ciclo() {
        if (!trava.tryLock()) return
        try {
            for (t in tarefas) {
                try {
                    val ultima = withContext(Dispatchers.IO) { estado.ultimaPorTipo(t.instance)[t.kind] }
                    if (!t.devida(clock.instant(), ultima)) continue
                    t.executar()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    // Inclui Error (ex.: NoSuchMethodError): nada derruba o laço; só a classe é logada, nunca a mensagem.
                    log.warn("Tarefa agendada {} falhou: {}", t.kind, e.javaClass.simpleName)
                }
            }
        } finally {
            trava.unlock()
        }
    }

    /** Cancela o laço e espera ele terminar. Sem job iniciado, não faz nada. */
    suspend fun parar() {
        job?.cancelAndJoin()
    }

    private companion object {
        val log = LoggerFactory.getLogger(Agendador::class.java)
    }
}

private val logMontagem = LoggerFactory.getLogger("br.sidia.painel.sync.Agendador")

/**
 * Monta o agendador do app. Sem banco devolve null. A retenção entra SEMPRE (só apaga dados locais).
 * As tarefas do Jira (incremental e reconciliação, por instância de `SYNC_INSTANCIAS`) só entram se
 * [SyncGate.podeAgendar], ou seja, com a flag `JIRA_LIVE_ENABLED` ligada. Com a flag desligada nada fala com o Jira.
 * Mesmo montada por engano, a tarefa passa pelo gate do `SyncService` a cada execução e pelo `LiveGuard` do cliente.
 * Instância listada sem credencial configurada vira execução FALHA `INSTANCIA_SEM_CREDENCIAL`, sem derrubar nada.
 */
fun montarAgendador(
    ctx: AppContext,
    clock: Clock = Clock.systemUTC(),
    dormir: suspend (Duration) -> Unit = { delay(it) },
    /** Métricas do painel (opcional): conta as execuções concluídas e as linhas removidas pela retenção. */
    metricas: Metricas? = null,
): Agendador? {
    val ds = ctx.dataSource ?: return null
    val settings = ctx.settings
    val cfg = settings.sync
    val estado: SyncStateStore = SqliteSyncStateStore(ds).let { if (metricas != null) EstadoComMetricas(it, metricas) else it }
    val gate = SyncGate(settings.jiraLiveEnabled, cfg)
    val tarefas = ArrayList<TarefaAgendada>()
    if (gate.podeAgendar()) {
        val servico = SyncService(ctx, SqliteIssueStore(ds), estado, gate, clock)
        for (instancia in cfg.instancias) {
            tarefas += TarefaAgendada(
                SyncKind.INCREMENTAL,
                instancia,
                { agora, ultima -> Agenda.incrementalDevida(agora, ultima, cfg.intervalo) },
            ) {
                executarDoJira(ctx, estado, clock, instancia, SyncKind.INCREMENTAL) { servico.incremental(instancia) }
            }
            tarefas += TarefaAgendada(
                SyncKind.RECONCILIACAO,
                instancia,
                { agora, ultima -> Agenda.diariaDevida(agora, ultima, cfg.horaReconciliacao, settings.zone) },
            ) {
                executarDoJira(ctx, estado, clock, instancia, SyncKind.RECONCILIACAO) { servico.reconciliar(instancia) }
            }
        }
    }
    tarefas += TarefaAgendada(
        SyncKind.RETENCAO,
        null,
        { agora, ultima -> Agenda.diariaDevida(agora, ultima, cfg.horaRetencao, settings.zone) },
    ) { executarRetencao(ctx, estado, clock, metricas) }
    return Agendador(tarefas, estado, clock, dormir = dormir)
}

/** Instância sem credencial vira falha registrada (com código); as demais seguem pelo `SyncService`. */
private suspend fun executarDoJira(
    ctx: AppContext,
    estado: SyncStateStore,
    clock: Clock,
    instancia: String,
    kind: SyncKind,
    executar: suspend () -> Unit,
) {
    if (instancia !in ctx.settings.jiraInstances) {
        withContext(Dispatchers.IO) {
            val id = estado.iniciar(instancia, kind, clock.instant())
            estado.concluir(id, RunStatus.FALHA, RunResumo(), "INSTANCIA_SEM_CREDENCIAL", null, clock.instant())
        }
        logMontagem.warn("Instância {} listada em SYNC_INSTANCIAS sem credencial configurada: execução {} não feita", instancia, kind)
        return
    }
    executar()
}

/** Purga local (nenhuma requisição) registrada como `sync_run` RETENCAO, instância [INSTANCIA_RETENCAO]. */
private suspend fun executarRetencao(ctx: AppContext, estado: SyncStateStore, clock: Clock, metricas: Metricas?) {
    val ds = requireNotNull(ctx.dataSource)
    withContext(Dispatchers.IO) {
        val id = estado.iniciar(INSTANCIA_RETENCAO, SyncKind.RETENCAO, clock.instant())
        try {
            val hoje = LocalDate.now(clock.withZone(ctx.settings.zone))
            val apagadas = Retention(ds, ctx.calendar).purge(
                hoje, ctx.settings.retentionMonths, ctx.settings.accessLogRetentionMonths,
                aoRemover = { categoria, n -> metricas?.retencaoRemoveu(categoria, n) },
            )
            estado.concluir(id, RunStatus.OK, RunResumo(removidas = apagadas), null, null, clock.instant())
        } catch (e: Exception) {
            logMontagem.warn("Retenção falhou: {}", e.javaClass.simpleName)
            estado.concluir(id, RunStatus.FALHA, RunResumo(), "ERRO_INTERNO", null, clock.instant())
        }
    }
}
