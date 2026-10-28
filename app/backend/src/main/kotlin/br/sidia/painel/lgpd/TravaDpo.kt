package br.sidia.painel.lgpd

import br.sidia.painel.config.Settings
import javax.sql.DataSource

/**
 * Os módulos que tratam dados pessoais sensíveis e ficam (ou ficarão) atrás da trava do DPO. HOJE só a INTEGRIDADE passa pela
 * [TravaDpo]; a AUDITORIA está listada para o Plano 11 ligar e ainda NÃO é barrada nem tem o registro de acesso mascarado.
 */
enum class ModuloSensivel { AUDITORIA, INTEGRIDADE }

/** O módulo está fechado pela trava do DPO. O código estável é [TravaDpo.CODIGO]; a mensagem nunca leva dado. */
class BloqueadoPeloDpoException(val modulo: ModuloSensivel) : RuntimeException(TravaDpo.CODIGO, null, false, false)

/**
 * A PORTA do DPO: Integridade (e, depois, Auditoria) só servem dados reais quando o encarregado de dados (DPO) aceitar o uso.
 * Sem o aceite, só valem fixtures e a demonstração: [liberada] é verdadeira SÓ no modo de desenvolvimento com o acesso ao Jira
 * desligado (dados fictícios). Em produção, e em desenvolvimento com Jira ligado, ela fica FECHADA (fail-closed).
 *
 * Ponto de extensão do Plano 11 (Tarefa 1): o aceite entra como `LgpdSettings.aceiteDpo` (configuração `DASHBOARD_DPO_ACEITE`,
 * com a referência do parecer) e [liberada] passa a ser `aceiteDpo || (devMode && !jiraLive)`. A classe olha a configuração e se o
 * banco já guarda dado sincronizado de verdade (ver [liberada]).
 */
class TravaDpo(private val settings: Settings, private val dataSource: DataSource?) {
    /**
     * Aberta só em desenvolvimento com o Jira desligado E sobre banco SEM dado sincronizado de verdade: se o painel já sincronizou
     * (mesmo que a flag tenha sido desligada depois), o banco tem dado real e a porta continua fechada. Qualquer erro ao consultar o
     * banco fecha a porta. A flag `DASHBOARD_DEMO_DATA` não reabre nada: a demonstração nunca grava execução de sincronização, então
     * um banco só de demonstração já passa; ligar a flag sobre banco com sincronização real não pode abrir a porta.
     */
    val liberada: Boolean get() = settings.dashboardDevMode && !settings.jiraLiveEnabled && semDadoSincronizado()

    private fun semDadoSincronizado(): Boolean {
        // Sem banco não há como provar que é demonstração: fecha.
        val ds = dataSource ?: return false
        return try {
            ds.connection.use { c ->
                c.createStatement().use { st ->
                    // Execução de sincronização REAL (incremental ou reconciliação; a retenção roda sempre, até em demonstração, e não
                    // conta), ou marca d'água de sincronização (a retenção pode apagar o histórico de execuções).
                    st.executeQuery(
                        "SELECT 1 FROM sync_run WHERE kind IN ('INCREMENTAL', 'RECONCILIACAO') " +
                            "UNION ALL SELECT 1 FROM sync_state WHERE watermark IS NOT NULL LIMIT 1",
                    ).use { !it.next() }
                }
            }
        } catch (e: Exception) {
            false
        }
    }

    /** Lança [BloqueadoPeloDpoException] se a porta está fechada. A rota a transforma em 403 com [CODIGO] (e grava o `access_log`). */
    fun exigir(modulo: ModuloSensivel) {
        if (!liberada) throw BloqueadoPeloDpoException(modulo)
    }

    /** Nulo se liberada; senão o código estável do bloqueio. */
    fun motivo(): String? = if (liberada) null else CODIGO

    companion object {
        const val CODIGO = "AGUARDANDO_ACEITE_DPO"
    }
}
