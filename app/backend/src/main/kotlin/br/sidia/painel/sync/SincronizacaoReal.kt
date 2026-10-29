package br.sidia.painel.sync

import java.sql.Connection
import javax.sql.DataSource

/**
 * Duas provas distintas sobre o banco, de propósito (a primeira é mais conservadora que a segunda):
 *
 * - [houveTentativa]: o banco já viu QUALQUER sincronização real. Vale uma execução incremental ou de reconciliação em qualquer
 *   status (inclusive em andamento ou falha, como a falha `INSTANCIA_SEM_CREDENCIAL`), ou QUALQUER linha de `sync_state` de
 *   instância (a retenção pode apagar o histórico de execuções e a migração V7 zera a marca d'água, mas a linha sobrevive).
 *   A retenção roda sempre, até em demonstração, e a instância [INSTANCIA_RETENCAO] é a dela: não contam. Usada pela
 *   `TravaDpo` (fecha mais) e pelo expurgo da demonstração (para de apagar assim que houve tentativa).
 * - [cargaConcluida]: o banco já recebeu dado de verdade. Vale uma execução real que terminou com sucesso, uma instância com
 *   marca d'água ou reconciliação gravada, ou qualquer issue (depois do expurgo da subida, issue não é demonstração). Usada
 *   pela tela ("aguardando a primeira carga do Jira" só some quando há carga, não na primeira tentativa).
 */
object SincronizacaoReal {
    fun houveTentativa(con: Connection): Boolean =
        con.createStatement().use { st ->
            st.executeQuery(
                "SELECT 1 FROM sync_run WHERE kind IN ('INCREMENTAL', 'RECONCILIACAO') " +
                    "UNION ALL SELECT 1 FROM sync_state WHERE instance <> '$INSTANCIA_RETENCAO' LIMIT 1",
            ).use { it.next() }
        }

    fun cargaConcluida(con: Connection): Boolean =
        con.createStatement().use { st ->
            st.executeQuery(
                "SELECT 1 FROM sync_run WHERE kind IN ('INCREMENTAL', 'RECONCILIACAO') AND status = 'OK' " +
                    "UNION ALL SELECT 1 FROM sync_state WHERE instance <> '$INSTANCIA_RETENCAO' " +
                    "AND (watermark IS NOT NULL OR ultima_reconciliacao IS NOT NULL) " +
                    "UNION ALL SELECT 1 FROM issue LIMIT 1",
            ).use { it.next() }
        }

    /** Para a tela: sem banco ou com erro de leitura não há como afirmar que falta a carga, então responde falso. */
    fun aguardandoPrimeiraCarga(ds: DataSource?): Boolean =
        ds != null && runCatching { ds.connection.use { !cargaConcluida(it) } }.getOrDefault(false)
}
