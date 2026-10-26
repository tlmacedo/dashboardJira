package br.sidia.painel.config

import java.nio.file.Files
import java.nio.file.Path

/**
 * Lê um arquivo de variáveis simples (`NOME=valor`, `#` comenta, aspas simples ou duplas opcionais ao redor do valor).
 * Sem o Docker para injetar as variáveis, é assim que o arquivo de variáveis local chega ao painel. Variáveis já
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

    private val NOME = Regex("^[A-Za-z_][A-Za-z0-9_]*$")
}
