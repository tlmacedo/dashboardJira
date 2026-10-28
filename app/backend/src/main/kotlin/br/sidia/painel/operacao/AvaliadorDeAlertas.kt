package br.sidia.painel.operacao

import br.sidia.painel.config.Settings
import br.sidia.painel.sync.INSTANCIA_RETENCAO
import br.sidia.painel.sync.RegistroDeExecucao
import br.sidia.painel.sync.RunStatus
import br.sidia.painel.sync.SyncKind
import br.sidia.painel.sync.SyncStateStore
import br.sidia.painel.time.BusinessCalendar
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Avalia, só com o estado que o painel já tem (execuções, contadores, configuração, calendário, disco e `backup_run`), os
 * 14 alertas operacionais. Não faz NENHUMA requisição: nem ao Jira nem a lugar algum. O alerta de PAT usa somente a
 * validade que o operador informou. Relógio injetado; dias em Manaus (fuso de [settings]).
 *
 * [avaliar] é puro quanto aos alertas (a lista de agora). [avaliarEPublicar] guarda o que está ativo e, SÓ NA MUDANÇA DE
 * ESTADO, loga `ALERTA <codigo> ATIVO|RESOLVIDO`, chama as [saidas] e atualiza a métrica `painel_alerta_ativo`.
 * [noArDesde] é o início deste processo: "nunca rodou" só vira alerta depois de um tempo no ar.
 */
class AvaliadorDeAlertas(
    private val estado: SyncStateStore,
    private val backups: BackupRunStore,
    private val metricas: Metricas,
    private val settings: Settings,
    private val calendario: BusinessCalendar,
    private val discoLivre: () -> Long,
    private val saidas: List<SaidaDeAlertas>,
    private val clock: Clock,
    private val noArDesde: Instant = clock.instant(),
    private val discoDoBackup: (() -> Long)? = null,
) {
    private data class Chave(val codigo: CodigoAlerta, val instancia: String?)

    private val ativos = LinkedHashMap<Chave, Alerta>()
    private val amostrasDeAccessLog = ArrayDeque<Pair<Instant, Double>>()

    /** Os alertas de agora: CRITICO antes de ATENCAO, depois a ordem do código e a instância. */
    @Synchronized
    fun avaliar(): List<Alerta> {
        val agora = clock.instant()
        return condicoes(agora, LocalDate.now(clock.withZone(settings.zone)))
            .map { Alerta(it.codigo, it.gravidade, it.instancia, ativos[Chave(it.codigo, it.instancia)]?.desde ?: agora) }
            .sortedWith(compareByDescending<Alerta> { it.gravidade }.thenBy { it.codigo }.thenBy { it.instancia.orEmpty() })
    }

    /** Avalia e anuncia as mudanças (uma vez por mudança de estado). Devolve os alertas de agora. */
    @Synchronized
    fun avaliarEPublicar(): List<Alerta> {
        val agora = clock.instant()
        amostrarAccessLog(agora)
        atualizarMetricasDeApoio()
        val atuais = avaliar()
        val porChave = atuais.associateBy { Chave(it.codigo, it.instancia) }
        for ((chave, alerta) in porChave) {
            val anterior = ativos[chave]
            if (anterior == null || anterior.gravidade != alerta.gravidade) {
                ativos[chave] = alerta
                anunciar(alerta, ativo = true, agora)
            }
        }
        for ((chave, alerta) in ativos.toList()) {
            if (chave !in porChave) {
                ativos.remove(chave)
                anunciar(alerta, ativo = false, agora)
            }
        }
        // Um valor por código (0 quando nenhum alerta do código está ativo): vale para alertas por instância também.
        for (codigo in CodigoAlerta.entries) metricas.alertaAtivo(codigo.name, atuais.any { it.codigo == codigo })
        return atuais
    }

    private fun anunciar(alerta: Alerta, ativo: Boolean, quando: Instant) {
        log.warn("ALERTA {} {}{}", alerta.codigo, if (ativo) "ATIVO" else "RESOLVIDO", alerta.instancia?.let { " instancia=$it" }.orEmpty())
        for (saida in saidas) {
            try {
                saida.mudou(alerta, ativo, quando)
            } catch (e: Exception) {
                log.warn("Saída de alertas falhou ({})", e.javaClass.simpleName)
            }
        }
    }

    // ---- amostras e métricas de apoio ----

    private fun amostrarAccessLog(agora: Instant) {
        amostrasDeAccessLog.addLast(agora to metricas.falhasDeAccessLog())
        while (amostrasDeAccessLog.isNotEmpty() && amostrasDeAccessLog.first().first < agora.minus(JANELA_ACCESS_LOG)) {
            amostrasDeAccessLog.removeFirst()
        }
    }

    private fun atualizarMetricasDeApoio() {
        seguro { metricas.discoLivre("banco", discoLivre()) }
        seguro { discoDoBackup?.let { metricas.discoLivre("backup", it()) } }
        seguro { backups.ultima(TipoDeBackup.BACKUP, StatusDeBackup.OK)?.let { metricas.ultimoBackupOk(it.fim ?: it.inicio, it.tamanhoBytes) } }
    }

    // ---- regras ----

    private data class Condicao(val codigo: CodigoAlerta, val gravidade: GravidadeAlerta, val instancia: String? = null)

    private fun condicoes(agora: Instant, hoje: LocalDate): List<Condicao> {
        val saida = mutableListOf<Condicao>()
        val live = settings.jiraLiveEnabled
        val producao = !settings.dashboardDevMode
        val noAr = Duration.between(noArDesde, agora)

        for (instancia in settings.sync.instancias) {
            seguro {
                val suspensa = estado.suspensa(instancia) != null
                if (suspensa) saida += Condicao(CodigoAlerta.SYNC_SUSPENSA, GravidadeAlerta.CRITICO, instancia)
                val execucoes = estado.ultimas(instancia, LIMITE_DE_EXECUCOES)
                if (live && !suspensa) {
                    val limite = Duration.ofMillis(settings.sync.intervalo.inWholeMilliseconds * 3)
                    val ultimoOk = execucoes.ultimoOk(SyncKind.INCREMENTAL)
                    if (atrasado(ultimoOk, agora, limite, noAr, esperaSemExecucao = HORA)) {
                        saida += Condicao(CodigoAlerta.SYNC_ATRASADA, GravidadeAlerta.ATENCAO, instancia)
                    }
                }
                if (execucoes.firstOrNull { it.kind == SyncKind.RECONCILIACAO }?.codigoErro == "RECONCILIACAO_SUSPEITA") {
                    saida += Condicao(CodigoAlerta.RECONCILIACAO_SUSPEITA, GravidadeAlerta.CRITICO, instancia)
                }
                if (live && atrasado(execucoes.ultimoOk(SyncKind.RECONCILIACAO), agora, VINTE_E_SEIS_HORAS, noAr, VINTE_E_SEIS_HORAS)) {
                    saida += Condicao(CodigoAlerta.RECONCILIACAO_ATRASADA, GravidadeAlerta.ATENCAO, instancia)
                }
            }
        }

        seguro {
            val retencao = estado.ultimas(INSTANCIA_RETENCAO, LIMITE_DE_EXECUCOES)
            val falhou = retencao.firstOrNull { it.kind == SyncKind.RETENCAO }?.status == RunStatus.FALHA
            if (falhou || atrasado(retencao.ultimoOk(SyncKind.RETENCAO), agora, VINTE_E_SEIS_HORAS, noAr, VINTE_E_SEIS_HORAS)) {
                saida += Condicao(CodigoAlerta.RETENCAO_FALHOU, GravidadeAlerta.ATENCAO)
            }
        }

        // Falhas do registro de acesso: o contador cresceu em algum ponto da última hora (amostrada a cada avaliação).
        amostrasDeAccessLog.firstOrNull()?.let { (_, base) ->
            if (metricas.falhasDeAccessLog() > base) saida += Condicao(CodigoAlerta.ACCESS_LOG_FALHANDO, GravidadeAlerta.CRITICO)
        }

        if (live) {
            for ((instancia, validade) in settings.operacao.patValidade) {
                val dias = ChronoUnit.DAYS.between(hoje, validade)
                when {
                    dias < 0 -> saida += Condicao(CodigoAlerta.PAT_EXPIRADO, GravidadeAlerta.CRITICO, instancia)
                    dias <= DIAS_DE_AVISO_DO_PAT -> saida += Condicao(CodigoAlerta.PAT_EXPIRANDO, GravidadeAlerta.ATENCAO, instancia)
                }
            }
        }

        if (hoje.monthValue >= 11 && calendario.movableHolidays.none { it.year == hoje.year + 1 }) {
            saida += Condicao(CodigoAlerta.CALENDARIO_SEM_PROXIMO_ANO, GravidadeAlerta.ATENCAO)
        }

        seguro {
            if (discoLivre() < settings.operacao.discoMinMb.toLong() * 1024 * 1024) {
                saida += Condicao(CodigoAlerta.DISCO_BAIXO, GravidadeAlerta.CRITICO)
            }
        }

        seguro {
            if (backups.ultima(TipoDeBackup.BACKUP)?.codigoErro == "INTEGRIDADE_FALHOU") {
                saida += Condicao(CodigoAlerta.BANCO_SUSPEITO, GravidadeAlerta.CRITICO)
            }
            if (producao) {
                val ultimoBackupOk = backups.ultima(TipoDeBackup.BACKUP, StatusDeBackup.OK)
                if (atrasadoBackup(ultimoBackupOk, agora, VINTE_E_SEIS_HORAS, noAr)) {
                    saida += Condicao(CodigoAlerta.BACKUP_ATRASADO, GravidadeAlerta.CRITICO)
                }
                val ultimaRestauracaoOk = backups.ultima(TipoDeBackup.RESTAURACAO, StatusDeBackup.OK)
                if (atrasadoBackup(ultimaRestauracaoOk, agora, TRES_DIAS, noAr)) {
                    val ultima = backups.ultima(TipoDeBackup.RESTAURACAO)
                    val gravidade = if (ultima?.status == StatusDeBackup.FALHA) GravidadeAlerta.CRITICO else GravidadeAlerta.ATENCAO
                    saida += Condicao(CodigoAlerta.RESTAURACAO_NAO_TESTADA, gravidade)
                }
            }
        }

        if (!producao && live) saida += Condicao(CodigoAlerta.MODO_DEV_COM_JIRA_LIGADO, GravidadeAlerta.CRITICO)
        return saida
    }

    private fun List<RegistroDeExecucao>.ultimoOk(tipo: SyncKind): RegistroDeExecucao? =
        firstOrNull { it.kind == tipo && it.status == RunStatus.OK }

    /** Passou do [limite] desde o fim do último OK; sem nenhum OK, só depois de [esperaSemExecucao] no ar. */
    private fun atrasado(ultimoOk: RegistroDeExecucao?, agora: Instant, limite: Duration, noAr: Duration, esperaSemExecucao: Duration): Boolean {
        val referencia = ultimoOk?.let { it.finishedAt ?: it.startedAt } ?: return noAr > esperaSemExecucao
        return Duration.between(referencia, agora) > limite
    }

    private fun atrasadoBackup(ultimoOk: RegistroDeBackup?, agora: Instant, limite: Duration, noAr: Duration): Boolean {
        val referencia = ultimoOk?.let { it.fim ?: it.inicio } ?: return noAr > limite
        return Duration.between(referencia, agora) > limite
    }

    /** Uma regra que não consegue ler seu estado (ex.: banco fora) não derruba as outras nem a rota. */
    private inline fun seguro(bloco: () -> Unit) {
        try {
            bloco()
        } catch (e: Exception) {
            log.warn("Uma regra de alerta não pôde ser avaliada ({})", e.javaClass.simpleName)
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(AvaliadorDeAlertas::class.java)
        const val LIMITE_DE_EXECUCOES = 500
        const val DIAS_DE_AVISO_DO_PAT = 14L
        val HORA: Duration = Duration.ofHours(1)
        val VINTE_E_SEIS_HORAS: Duration = Duration.ofHours(26)
        val TRES_DIAS: Duration = Duration.ofDays(3)
        val JANELA_ACCESS_LOG: Duration = Duration.ofMinutes(60)
    }
}
