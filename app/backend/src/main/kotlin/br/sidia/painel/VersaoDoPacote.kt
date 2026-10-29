package br.sidia.painel

import java.io.InputStream
import java.util.Properties

/**
 * Versão do pacote em execução. [versao] é `AAAA.MM.N` (ou `0.0.0-dev` em build local); [commit] e [construidaEm] ficam no
 * recurso, no `VERSAO.txt` do pacote e numa linha de log da subida, NUNCA na resposta pública de `/api/meta`.
 */
data class Versao(val versao: String, val commit: String, val construidaEm: String) {
    /** Texto curto para a tela: `v2026.11.1`; `dev` quando é build local. */
    val rotulo: String get() = if (versao == VERSAO_DE_DESENVOLVIMENTO) "dev" else "v$versao"

    companion object {
        const val VERSAO_DE_DESENVOLVIMENTO = "0.0.0-dev"
    }
}

/** Lê o recurso `versao.properties`, gerado pelo Gradle (`gerarVersaoProperties`) a partir de `-Pversao` e `-Pcommit`. */
object VersaoDoPacote {
    val atual: Versao by lazy { ler(VersaoDoPacote::class.java.getResourceAsStream("/versao.properties")) }

    /** Recurso ausente (ou campo vazio): usa `0.0.0-dev`, `desconhecido` e data vazia. */
    fun ler(entrada: InputStream?): Versao {
        val p = Properties()
        entrada?.use { p.load(it.reader(Charsets.UTF_8)) }
        fun campo(nome: String, padrao: String) = p.getProperty(nome)?.trim()?.takeIf { it.isNotEmpty() } ?: padrao
        return Versao(
            versao = campo("versao", Versao.VERSAO_DE_DESENVOLVIMENTO),
            commit = campo("commit", "desconhecido"),
            construidaEm = campo("construidaEm", ""),
        )
    }
}
