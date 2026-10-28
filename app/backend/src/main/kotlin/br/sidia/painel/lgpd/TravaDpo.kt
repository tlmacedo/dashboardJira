package br.sidia.painel.lgpd

import br.sidia.painel.config.Settings

/** Os módulos que tratam dados pessoais sensíveis e ficam atrás da trava do DPO. */
enum class ModuloSensivel { AUDITORIA, INTEGRIDADE }

/** O módulo está fechado pela trava do DPO. O código estável é [TravaDpo.CODIGO]; a mensagem nunca leva dado. */
class BloqueadoPeloDpoException(val modulo: ModuloSensivel) : RuntimeException(TravaDpo.CODIGO, null, false, false)

/**
 * A PORTA do DPO: Integridade (e, depois, Auditoria) só servem dados reais quando o encarregado de dados (DPO) aceitar o uso.
 * Sem o aceite, só valem fixtures e a demonstração: [liberada] é verdadeira SÓ no modo de desenvolvimento com o acesso ao Jira
 * desligado (dados fictícios). Em produção, e em desenvolvimento com Jira ligado, ela fica FECHADA (fail-closed).
 *
 * Ponto de extensão do Plano 11 (Tarefa 1): o aceite entra como `LgpdSettings.aceiteDpo` (configuração `DASHBOARD_DPO_ACEITE`,
 * com a referência do parecer) e [liberada] passa a ser `aceiteDpo || (devMode && !jiraLive)`. Esta classe é decidida SÓ por
 * configuração, nunca pelo conteúdo do banco.
 */
class TravaDpo(private val settings: Settings) {
    val liberada: Boolean get() = settings.dashboardDevMode && !settings.jiraLiveEnabled

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
