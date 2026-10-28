package br.sidia.painel.operacao

import br.sidia.painel.config.Settings
import java.nio.file.InvalidPathException
import java.nio.file.Path
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
        else -> uso(saida)
    }
}

private fun uso(saida: (String) -> Unit): Int {
    saida("USO: painel validar-config [--dir <caminho>]")
    return 2
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
