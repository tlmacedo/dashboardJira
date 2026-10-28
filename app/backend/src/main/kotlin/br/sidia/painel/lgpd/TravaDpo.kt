package br.sidia.painel.lgpd

import br.sidia.painel.config.Settings
import javax.sql.DataSource

/**
 * Os módulos que tratam dados pessoais sensíveis e ficam atrás da trava do DPO. Os dois passam pela [TravaDpo]; o registro de
 * acesso (`access_log`) das rotas deles guarda só QUEM consultou o módulo, nunca o achado nem o alvo.
 */
enum class ModuloSensivel { AUDITORIA, INTEGRIDADE }

/** O módulo está fechado pela trava do DPO. O código estável é [TravaDpo.CODIGO]; a mensagem nunca leva dado. */
class BloqueadoPeloDpoException(val modulo: ModuloSensivel) : RuntimeException(TravaDpo.CODIGO, null, false, false)

/**
 * A PORTA do DPO: Auditoria e Integridade só servem dados reais quando o encarregado de dados (DPO) aceitar o uso. A decisão sai
 * da configuração: [liberada] é verdadeira com o aceite (`DASHBOARD_DPO_ACEITE=true` com a referência do parecer, ligado só pelo
 * usuário) OU no modo de desenvolvimento offline (`DASHBOARD_DEV_MODE=true` e `JIRA_LIVE_ENABLED=false`), sobre um banco que
 * ainda não recebeu dado sincronizado de verdade (ver [semDadoSincronizado]). Em produção sem aceite, e em desenvolvimento com
 * o Jira ligado sem aceite, ela fica FECHADA (fail-closed).
 *
 * O aceite NÃO consulta o banco. A condição de desenvolvimento consulta o banco só para FECHAR mais (nunca para abrir): é a
 * defesa de quem desligou a flag do Jira depois de sincronizar de verdade (decisão do Plano 7a, mantida no Plano 11).
 */
class TravaDpo(private val settings: Settings, private val dataSource: DataSource?) {
    val liberada: Boolean
        get() = settings.lgpd.aceiteDpo || (settings.dashboardDevMode && !settings.jiraLiveEnabled && semDadoSincronizado())

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

    /** Os módulos fechados agora, em ordem estável (vazio com a porta aberta); é o que `/api/me` entrega à tela. */
    fun modulosBloqueados(): List<ModuloSensivel> = if (liberada) emptyList() else ModuloSensivel.entries.toList()

    companion object {
        const val CODIGO = "AGUARDANDO_ACEITE_DPO"
    }
}
