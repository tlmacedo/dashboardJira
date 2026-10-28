package br.sidia.painel.operacao

/** O que uma linha de `backup_run` registra: uma cópia do banco ou o teste de restauração dela. */
enum class TipoDeBackup { BACKUP, RESTAURACAO }

/** Desfecho de uma cópia ou de uma verificação de restauração. */
enum class StatusDeBackup { OK, FALHA }
