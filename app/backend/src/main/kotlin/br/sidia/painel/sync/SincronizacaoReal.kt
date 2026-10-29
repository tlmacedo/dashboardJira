package br.sidia.painel.sync

import java.sql.Connection

/**
 * A prova de que o banco já recebeu dado sincronizado de verdade, compartilhada pela trava do DPO, pelo expurgo da
 * demonstração e pelo sinal "aguardando a primeira carga" da tela.
 *
 * Vale como prova: uma execução de sincronização REAL (incremental ou reconciliação; a retenção roda sempre, até em
 * demonstração, e não conta), ou QUALQUER linha de estado de uma instância em `sync_state` (a retenção pode apagar o
 * histórico de execuções e a migração V7 zera a marca d'água, mas a linha da instância sobrevive). A instância
 * [INSTANCIA_RETENCAO] é a da retenção e não conta.
 */
object SincronizacaoReal {
    fun existe(con: Connection): Boolean =
        con.createStatement().use { st ->
            st.executeQuery(
                "SELECT 1 FROM sync_run WHERE kind IN ('INCREMENTAL', 'RECONCILIACAO') " +
                    "UNION ALL SELECT 1 FROM sync_state WHERE instance <> '$INSTANCIA_RETENCAO' LIMIT 1",
            ).use { it.next() }
        }
}
