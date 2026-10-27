package br.sidia.painel.config

import java.nio.file.Files
import java.nio.file.Path

/**
 * Lê um arquivo de variáveis simples (`NOME=valor`, `#` comenta, aspas simples ou duplas opcionais ao redor do valor).
 * É assim que o arquivo de variáveis local chega ao painel. Variáveis já
 * definidas no ambiente do processo têm precedência sobre o arquivo. Nunca registra valores (podem ser tokens).
 */
object ArquivoEnv {
    fun ler(arquivo: Path): Map<String, String> {
        if (!Files.isRegularFile(arquivo)) return emptyMap()
        return interpretar(Files.readAllLines(arquivo))
    }

    internal fun interpretar(linhas: List<String>): Map<String, String> {
        val mapa = LinkedHashMap<String, String>()
        for (bruta in linhas) {
            val linha = bruta.trim().removePrefix("﻿")
            if (linha.isEmpty() || linha.startsWith("#")) continue
            val sem = linha.removePrefix("export ").trim()
            val i = sem.indexOf('=')
            if (i <= 0) continue
            val nome = sem.substring(0, i).trim()
            if (!NOME.matches(nome)) continue
            var valor = sem.substring(i + 1).trim()
            valor = if (valor.length >= 2 && (valor.first() == '"' || valor.first() == '\'') && valor.last() == valor.first()) {
                valor.substring(1, valor.length - 1)
            } else {
                valor.substringBefore(" #").trim()
            }
            mapa[nome] = valor
        }
        return mapa
    }

    /** Ambiente efetivo: o do processo por cima do arquivo (valor vazio no ambiente não esconde o do arquivo). */
    fun combinar(arquivo: Map<String, String>, ambiente: Map<String, String>): Map<String, String> =
        arquivo + ambiente.filterValues { it.isNotBlank() }

    /** Única variável que NÃO aceita o ambiente do processo: o acesso ao Jira é controlado só pelo arquivo. */
    const val FLAG_DO_JIRA = "JIRA_LIVE_ENABLED"

    /**
     * Como [combinar], mas a flag de acesso ao Jira vale **somente** como está no arquivo: variável de terminal, de
     * sistema ou de outra ferramenta com o mesmo nome é ignorada. Sem a linha no arquivo, a flag fica desligada.
     * Assim, mudar o valor no arquivo de variáveis é a única forma de ligar ou desligar o acesso.
     */
    fun combinarComFlagSoDoArquivo(arquivo: Map<String, String>, ambiente: Map<String, String>): Map<String, String> {
        val efetivo = combinar(arquivo, ambiente - FLAG_DO_JIRA).toMutableMap()
        efetivo.remove(FLAG_DO_JIRA)
        arquivo[FLAG_DO_JIRA]?.let { efetivo[FLAG_DO_JIRA] = it }
        return efetivo
    }

    private val NOME = Regex("^[A-Za-z_][A-Za-z0-9_]*$")
}
