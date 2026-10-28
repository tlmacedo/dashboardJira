package br.sidia.painel.operacao

import br.sidia.painel.jira.INSTANCIAS_CONHECIDAS
import br.sidia.painel.sync.INSTANCIA_RETENCAO
import br.sidia.painel.sync.RunStatus
import br.sidia.painel.sync.RegistroDeExecucao
import br.sidia.painel.sync.SyncKind
import br.sidia.painel.sync.SyncStateStore
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.Tags
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.core.instrument.binder.system.UptimeMetrics
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import java.time.Clock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Métricas do painel (Prometheus), com RÓTULOS FECHADOS: só valores de listas fixas (instância conhecida, tipo e status
 * de execução, volume `banco`/`backup`, código de alerta, modelo da rota). Nunca username, chave de issue, squad, JQL,
 * URL nem caminho de arquivo, nem como rótulo nem como nome. Servidas só na porta interna (ver [ServidorDeMetricas]).
 */
class Metricas(val registro: PrometheusMeterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)) {
    // Gauges mantêm referência fraca ao objeto observado: os valores ficam aqui, com referência forte.
    private val alertas = ConcurrentHashMap<String, AtomicLong>()
    private val discos = ConcurrentHashMap<String, AtomicLong>()
    private val backupUltimoOk = AtomicLong(0)
    private val backupTamanho = AtomicLong(0)
    private val contadorDeFalhasDeAccessLog = Counter.builder("painel.access.log.falhas")
        .description("Falhas ao gravar no registro de acesso").register(registro)

    init {
        Gauge.builder("painel.backup.ultimo.ok.timestamp.segundos", backupUltimoOk) { it.get().toDouble() }
            .description("Instante (segundos desde 1970) do último backup OK").register(registro)
        Gauge.builder("painel.backup.tamanho.bytes", backupTamanho) { it.get().toDouble() }
            .description("Tamanho do último backup OK").register(registro)
    }

    /** JVM, GC, threads, classes, processador e tempo no ar (binders padrão do Micrometer). */
    fun registrarBindersDaJvm(): Metricas {
        JvmMemoryMetrics().bindTo(registro)
        JvmGcMetrics().bindTo(registro)
        JvmThreadMetrics().bindTo(registro)
        ClassLoaderMetrics().bindTo(registro)
        ProcessorMetrics().bindTo(registro)
        UptimeMetrics().bindTo(registro)
        return this
    }

    /** `painel_http_requisicoes_total` e `painel_http_duracao_segundos`, com a rota pelo MODELO (nunca o caminho real). */
    fun requisicaoHttp(rota: String, metodo: String, status: Int, duracaoSegundos: Double) {
        // Rótulos de lista fechada: o cliente (até o anônimo da porta pública) não escolhe a cardinalidade.
        val metodoFechado = if (metodo in METODOS_CONHECIDOS) metodo else "OUTRO"
        val statusFechado = if (status in 100..599) status else 0
        val tags = Tags.of("rota", rota, "metodo", metodoFechado, "status", statusFechado.toString())
        Counter.builder("painel.http.requisicoes").tags(tags).register(registro).increment()
        DistributionSummary.builder("painel.http.duracao").baseUnit("segundos").tags(tags)
            .serviceLevelObjectives(0.05, 0.1, 0.25, 0.5, 1.0, 2.5, 5.0, 10.0)
            .register(registro).record(duracaoSegundos)
    }

    fun execucaoDeSync(instancia: String, tipo: SyncKind, status: RunStatus) {
        Counter.builder("painel.sync.execucoes")
            .tags("instancia", rotuloDaInstancia(instancia), "tipo", tipo.name, "status", status.name)
            .register(registro).increment()
    }

    fun falhaDeAccessLog() = contadorDeFalhasDeAccessLog.increment()

    /** Quantas falhas de gravação do registro de acesso já houve neste processo (base do alerta `ACCESS_LOG_FALHANDO`). */
    fun falhasDeAccessLog(): Double = contadorDeFalhasDeAccessLog.count()

    /** Restaura as métricas do último backup OK a partir do banco (depois de reiniciar), sem contar uma execução nova. */
    fun ultimoBackupOk(quando: java.time.Instant, tamanhoBytes: Long?) {
        backupUltimoOk.set(quando.epochSecond)
        if (tamanhoBytes != null) backupTamanho.set(tamanhoBytes)
    }

    fun retencaoRemoveu(categoria: String, n: Int) {
        Counter.builder("painel.retencao.removidas").tag("categoria", categoria).register(registro).increment(n.toDouble())
    }

    /** 1 enquanto o alerta [codigo] está ativo, 0 depois de resolvido. */
    fun alertaAtivo(codigo: String, ativo: Boolean) {
        val valor = alertas.computeIfAbsent(codigo) {
            AtomicLong(0).also { v ->
                Gauge.builder("painel.alerta.ativo", v) { it.get().toDouble() }.tag("codigo", codigo).register(registro)
            }
        }
        valor.set(if (ativo) 1 else 0)
    }

    /** Conta a execução do backup e, se OK, guarda o instante e o tamanho. */
    fun backup(status: StatusDeBackup, tamanhoBytes: Long?, quando: java.time.Instant = java.time.Instant.now()) {
        Counter.builder("painel.backup.execucoes").tag("status", status.name).register(registro).increment()
        if (status == StatusDeBackup.OK) {
            backupUltimoOk.set(quando.epochSecond)
            if (tamanhoBytes != null) backupTamanho.set(tamanhoBytes)
        }
    }

    /** Conta uma verificação de restauração (`painel_restauracao_execucoes_total{status}`). */
    fun restauracao(status: StatusDeBackup) {
        Counter.builder("painel.restauracao.execucoes").tag("status", status.name).register(registro).increment()
    }

    /** Espaço livre do volume `banco` ou `backup` (o rótulo nunca leva o caminho). */
    fun discoLivre(volume: String, bytes: Long) {
        require(volume == "banco" || volume == "backup") { "volume desconhecido" }
        val valor = discos.computeIfAbsent(volume) {
            AtomicLong(0).also { v ->
                Gauge.builder("painel.disco.livre.bytes", v) { it.get().toDouble() }.baseUnit("bytes").tag("volume", volume).register(registro)
            }
        }
        valor.set(bytes)
    }

    /**
     * Gauges do estado da sincronização, lidos do banco A CADA coleta (`painel_sync_suspensa`,
     * `painel_sync_cursor_idade_segundos` por instância e `painel_sync_ultima_ok_timestamp_segundos` por instância e tipo).
     * Leitura que falha vira NaN: a coleta de métricas nunca derruba nada.
     */
    fun vincularSincronizacao(estado: SyncStateStore, instancias: List<String>, relogio: Clock) {
        for (instancia in instancias.filter { it in INSTANCIAS_CONHECIDAS }) {
            Gauge.builder("painel.sync.suspensa", estado) { e -> lerOuNaN { if (e.suspensa(instancia) != null) 1.0 else 0.0 } }
                .tag("instancia", instancia).register(registro)
            Gauge.builder("painel.sync.cursor.idade.segundos", estado) { e ->
                lerOuNaN { e.cursor(instancia)?.let { (relogio.instant().epochSecond - it.epochSecond).toDouble() } ?: Double.NaN }
            }.tag("instancia", instancia).register(registro)
            for (tipo in listOf(SyncKind.INCREMENTAL, SyncKind.RECONCILIACAO)) {
                Gauge.builder("painel.sync.ultima.ok.timestamp.segundos", estado) { e ->
                    lerOuNaN { ultimaOk(e, instancia, tipo)?.finishedAt?.epochSecond?.toDouble() ?: 0.0 }
                }.tags("instancia", instancia, "tipo", tipo.name).register(registro)
            }
        }
    }

    private fun ultimaOk(estado: SyncStateStore, instancia: String, tipo: SyncKind): RegistroDeExecucao? =
        estado.ultimas(instancia, 50).firstOrNull { it.kind == tipo && it.status == RunStatus.OK }

    private fun lerOuNaN(leitura: () -> Double): Double =
        try {
            leitura()
        } catch (e: Exception) {
            Double.NaN
        }

    private fun rotuloDaInstancia(instancia: String): String =
        when {
            instancia in INSTANCIAS_CONHECIDAS -> instancia
            instancia == INSTANCIA_RETENCAO -> "local"
            else -> "desconhecida"
        }

    private companion object {
        val METODOS_CONHECIDOS = setOf("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS")
    }

    /** Texto Prometheus (formato 0.0.4) de todas as métricas. */
    fun texto(): String = registro.scrape()
}
