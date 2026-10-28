package br.sidia.painel.operacao

import br.sidia.painel.config.Settings
import br.sidia.painel.db.Database
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.time.Clock
import java.time.LocalDate

/**
 * Subcomandos de operação do mesmo binário (`painel <subcomando>`): rodam e saem, sem subir o servidor, sem tomar a
 * trava do banco e sem tocar o Jira. Códigos de saída: 0 ok, 1 problema encontrado, 2 uso incorreto.
 *
 * Devolve `null` quando [args] está vazio, ou seja, não é subcomando e a subida normal do servidor segue.
 * Nenhuma saída leva segredo, caminho de banco ou valor de configuração.
 */
fun comandoDeOperacao(args: List<String>, env: Map<String, String>, saida: (String) -> Unit = ::println): Int? {
    if (args.isEmpty()) return null
    val resto = args.drop(1)
    return when (args.first()) {
        "validar-config" -> validarConfig(resto, env, saida)
        "backup-agora" -> if (resto.isEmpty()) backupAgora(env, saida) else uso(saida)
        else -> uso(saida)
    }
}

private fun uso(saida: (String) -> Unit): Int {
    saida("USO: painel validar-config [--dir <caminho>] | backup-agora")
    return 2
}

/**
 * `painel backup-agora`: uma cópia imediata do banco para `BACKUP_DIR`, a mesma do agendamento. Abre o banco SEM tomar a
 * trava e SEM migrar (funciona com o serviço rodando, porque `VACUUM INTO` só lê) e nunca cria um banco novo. Imprime
 * `BACKUP OK <nome> <tamanho>` (0) ou `BACKUP FALHOU <codigo>` (1); sem `BACKUP_DIR`, explica e sai com 2.
 */
private fun backupAgora(env: Map<String, String>, saida: (String) -> Unit): Int {
    val settings = try {
        Settings.fromEnv(env)
    } catch (e: IllegalArgumentException) {
        saida("BACKUP FALHOU SETTINGS_INVALIDO")
        return 1
    }
    val cfg = settings.operacao.backup
    if (cfg.dir == null) {
        saida("BACKUP_DIR não está definido: o backup está desligado. Defina BACKUP_DIR (pasta fora do repositório e fora da pasta do banco) no arquivo de variáveis.")
        return 2
    }
    if (!Files.isRegularFile(settings.databasePath)) {
        saida("BACKUP FALHOU BANCO_NAO_ENCONTRADO")
        return 1
    }
    val ds = Database.connect(Database.urlDeArquivo(settings.databasePath), poolSize = 1)
    return (ds as AutoCloseable).use {
        val relogio = Clock.system(settings.zone)
        val servico = ServicoDeBackup(ds, settings.databasePath, cfg, SqliteBackupRunStore(ds), null, relogio, settings.zone)
        when (val r = servico.executar()) {
            is ResultadoDoBackup.Ok -> {
                saida("BACKUP OK ${r.arquivo.fileName} ${r.tamanhoBytes}")
                0
            }
            is ResultadoDoBackup.Falha -> {
                saida("BACKUP FALHOU ${r.codigo}")
                1
            }
        }
    }
}

private fun validarConfig(args: List<String>, env: Map<String, String>, saida: (String) -> Unit): Int {
    var dir: Path? = null
    var i = 0
    while (i < args.size) {
        when (args[i]) {
            "--dir" -> {
                val valor = args.getOrNull(i + 1) ?: return uso(saida)
                dir = try {
                    Path.of(valor)
                } catch (e: InvalidPathException) {
                    return uso(saida)
                }
                i += 2
            }
            else -> return uso(saida)
        }
    }
    // Settings inválido não impede de validar: o próprio validador reporta SETTINGS_INVALIDO.
    val padrao = runCatching { Settings.fromEnv(env) }.getOrDefault(Settings())
    val resultado = ValidadorDeConfig.validar(env, dir ?: padrao.configDir, LocalDate.now(padrao.zone))
    resultado.arquivosOk.forEach { saida("OK $it") }
    resultado.problemas.forEach { p ->
        saida("ERRO ${p.codigo} ${p.arquivo}" + (p.linha?.let { ":$it" } ?: ""))
    }
    return if (resultado.ok) {
        saida("VALIDACAO OK")
        0
    } else {
        saida("VALIDACAO FALHOU ${resultado.problemas.size}")
        1
    }
}
