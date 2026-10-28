package br.sidia.painel.operacao

import br.sidia.painel.AppContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * O avaliador do painel real: estado e registro de backups no banco do pool em uso, calendário e configuração do contexto,
 * disco da pasta do banco e, se `PAINEL_ALERTAS_ARQUIVO` existir, o arquivo de alertas. Sem banco (só testes) não há alertas.
 */
fun montarAvaliadorDeAlertas(ctx: AppContext, metricas: Metricas): AvaliadorDeAlertas? {
    val ds = ctx.dataSource ?: return null
    val banco = br.sidia.painel.db.Database.arquivoDe(ds) ?: ctx.settings.databasePath
    val pastaDoBanco = banco.toAbsolutePath().parent ?: banco.toAbsolutePath()
    val saidas = listOfNotNull<SaidaDeAlertas>(ctx.settings.operacao.alertasArquivo?.let { ArquivoDeAlertas(it, ctx.settings.zone) })
    return AvaliadorDeAlertas(
        estado = br.sidia.painel.sync.SqliteSyncStateStore(ds),
        backups = SqliteBackupRunStore(ds),
        metricas = metricas,
        settings = ctx.settings,
        calendario = ctx.calendar,
        discoLivre = { VerificacaoDeDisco.livreBytes(pastaDoBanco) },
        saidas = saidas,
        clock = ctx.clock,
        discoDoBackup = ctx.settings.operacao.backup.dir?.let { pasta -> { VerificacaoDeDisco.livreBytes(pasta) } },
    )
}

/**
 * Avalia os alertas a cada [passo] (5 min), no processo do painel, sem Jira e sem serviço externo. NÃO é um `SyncKind`
 * (o `CHECK` de `sync_run` não muda). Como o `Agendador`, é iniciado no `ApplicationStarted` e cancelado no
 * `ApplicationStopping`. A primeira avaliação espera [primeiroAtraso] para o painel terminar de subir. Nenhuma exceção
 * derruba o laço: só a classe é logada.
 */
class LacoDeAlertas(
    private val avaliador: AvaliadorDeAlertas,
    private val passo: Duration = 5.minutes,
    private val primeiroAtraso: Duration = 30.seconds,
    private val dormir: suspend (Duration) -> Unit = { delay(it) },
) {
    @Volatile
    private var job: Job? = null

    val emExecucao: Boolean get() = job?.isActive == true

    @Synchronized
    fun iniciar(scope: CoroutineScope): Job {
        check(job == null) { "O laço de alertas já foi iniciado" }
        return scope.launch {
            dormir(primeiroAtraso)
            while (isActive) {
                ciclo()
                dormir(passo)
            }
        }.also { job = it }
    }

    /** Uma avaliação (fora do laço de eventos, pois lê o banco). */
    suspend fun ciclo() {
        try {
            withContext(Dispatchers.IO) { avaliador.avaliarEPublicar() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            log.warn("Avaliação de alertas falhou ({})", e.javaClass.simpleName)
        }
    }

    suspend fun parar() {
        job?.cancelAndJoin()
    }

    private companion object {
        val log = LoggerFactory.getLogger(LacoDeAlertas::class.java)
    }
}
