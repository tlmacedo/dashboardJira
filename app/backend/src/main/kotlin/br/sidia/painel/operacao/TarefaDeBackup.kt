package br.sidia.painel.operacao

import br.sidia.painel.AppContext
import br.sidia.painel.db.Database
import br.sidia.painel.sync.Agenda
import br.sidia.painel.sync.TarefaDeOperacao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Clock

/**
 * A tarefa diária de backup do `Agendador` (`BACKUP_HORA`, fuso do painel): devida quando já passou a hora e a última
 * execução registrada em `backup_run` (qualquer status) não foi hoje. Sem `BACKUP_DIR` não há tarefa. Não toca o Jira e não
 * é um `SyncKind`. Depois de cada cópia OK, a restauração dela é verificada em arquivo temporário (ver [VerificadorDeRestauracao]).
 */
fun tarefaDeBackup(ctx: AppContext, clock: Clock, metricas: Metricas?): TarefaDeOperacao? {
    val cfg = ctx.settings.operacao.backup
    if (cfg.dir == null) return null
    val ds = ctx.dataSource ?: return null
    val runs = SqliteBackupRunStore(ds)
    val servico = ServicoDeBackup(ds, Database.arquivoDe(ds) ?: ctx.settings.databasePath, cfg, runs, metricas, clock, ctx.settings.zone)
    return TarefaDeOperacao(
        nome = "BACKUP",
        devida = { agora ->
            val ultima = withContext(Dispatchers.IO) { runs.ultima(TipoDeBackup.BACKUP)?.inicio }
            Agenda.diariaDevidaDesde(agora, ultima, cfg.hora, ctx.settings.zone)
        },
        executar = {
            withContext(Dispatchers.IO) { servico.executar() }
        },
    )
}
