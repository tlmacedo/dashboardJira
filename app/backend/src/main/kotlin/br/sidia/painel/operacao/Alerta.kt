package br.sidia.painel.operacao

import java.time.Instant

/** Os 14 alertas operacionais. O nome é o código que aparece na tela, na métrica, no log, no arquivo e no runbook. */
enum class CodigoAlerta {
    SYNC_SUSPENSA,
    SYNC_ATRASADA,
    RECONCILIACAO_SUSPEITA,
    RECONCILIACAO_ATRASADA,
    RETENCAO_FALHOU,
    ACCESS_LOG_FALHANDO,
    PAT_EXPIRANDO,
    PAT_EXPIRADO,
    CALENDARIO_SEM_PROXIMO_ANO,
    DISCO_BAIXO,
    BANCO_SUSPEITO,
    BACKUP_ATRASADO,
    RESTAURACAO_NAO_TESTADA,
    MODO_DEV_COM_JIRA_LIGADO,
}

enum class GravidadeAlerta { ATENCAO, CRITICO }

/** Um alerta ativo. [instancia] só existe nos alertas por instância do Jira; [desde] é quando o painel o viu ativar. Sem dado pessoal. */
data class Alerta(val codigo: CodigoAlerta, val gravidade: GravidadeAlerta, val instancia: String?, val desde: Instant)
