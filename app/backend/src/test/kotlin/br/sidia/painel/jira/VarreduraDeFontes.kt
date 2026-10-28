package br.sidia.painel.jira

/** Achado da varredura: arquivo (chave do mapa), linha (1 = primeira) e a regra violada. */
data class Violacao(val arquivo: String, val linha: Int, val regra: String)

/**
 * Varre fontes Kotlin (só texto; função pura) atrás de caminhos que contornam a fábrica de clientes Jira.
 *
 * [fontes] mapeia o arquivo (de preferência o caminho relativo com `/`) ao seu texto; [permitidos] são
 * **nomes** de arquivo (o trecho depois da última barra) que podem construir os clientes. Regras:
 * - `JIRA_CLIENT_CONSTRUIDO`: `JiraClient(` (com espaço antes do parêntese ou qualificado);
 * - `JIRA_CLIENT_REFERENCIA`: `::JiraClient`;
 * - `JIRA_CLIENT_ALIAS`: `import ... JiraClient as X`, `typealias X = JiraClient` (também alias de alias) e
 *   qualquer `X(` em arquivo não permitido, de qualquer pasta;
 * - `HTTP_CLIENT_CONSTRUIDO` e `HTTP_CLIENT_ALIAS`: o mesmo para `HttpClient`;
 * - `SETTINGS_COPY`: `.copy(` aplicado a `settings`/`Settings` (inclusive `ctx.settings.copy(`), `copy(` que
 *   troca o campo `settings =` e `copy(` que mexe em `jiraLiveEnabled`.
 * Comentários (de linha e de bloco, inclusive aninhados) são ignorados; o texto de strings é mantido.
 */
fun varrerFontes(fontes: Map<String, String>, permitidos: Set<String>): List<Violacao> {
    val limpas = fontes.mapValues { (_, texto) -> semComentarios(texto) }
    val aplicaveis = limpas.filterKeys { nomeDoArquivo(it) !in permitidos }

    val aliasJira = aliases(limpas.values, "JiraClient")
    val aliasHttp = aliases(limpas.values, "HttpClient")

    val achados = mutableListOf<Violacao>()
    for ((arquivo, texto) in aplicaveis) {
        fun marcar(regra: String, regex: Regex) {
            regex.findAll(texto).forEach { achados += Violacao(arquivo, linhaDe(texto, it.range.first), regra) }
        }
        marcar("JIRA_CLIENT_CONSTRUIDO", Regex("(?<![\\w])JiraClient\\s*\\("))
        marcar("JIRA_CLIENT_REFERENCIA", Regex("::\\s*JiraClient(?![\\w])"))
        marcar("HTTP_CLIENT_CONSTRUIDO", Regex("(?<![\\w])HttpClient\\s*\\("))
        declaracoesDeAlias(texto, "JiraClient", aliasJira).forEach { achados += Violacao(arquivo, it, "JIRA_CLIENT_ALIAS") }
        declaracoesDeAlias(texto, "HttpClient", aliasHttp).forEach { achados += Violacao(arquivo, it, "HTTP_CLIENT_ALIAS") }
        for (alias in aliasJira) marcar("JIRA_CLIENT_ALIAS", Regex("(?<![\\w.])${Regex.escape(alias)}\\s*\\("))
        for (alias in aliasHttp) marcar("HTTP_CLIENT_ALIAS", Regex("(?<![\\w.])${Regex.escape(alias)}\\s*\\("))
        marcar("SETTINGS_COPY", Regex("(?<![\\w])[sS]ettings\\s*\\.\\s*copy\\s*\\("))
        marcar("SETTINGS_COPY", Regex("\\.copy\\s*\\(\\s*(?:[^()]*,\\s*)?settings\\s*="))
        marcar("SETTINGS_COPY", Regex("\\.copy\\s*\\([^)]*\\bjiraLiveEnabled\\s*="))
    }
    return achados.distinct().sortedWith(compareBy({ it.arquivo }, { it.linha }, { it.regra }))
}

private fun nomeDoArquivo(chave: String) = chave.substringAfterLast('/').substringAfterLast('\\')

private fun linhaDe(texto: String, indice: Int): Int = texto.substring(0, indice).count { it == '\n' } + 1

/** Nomes que apontam para [tipo] por `import ... as X` ou `typealias X = ...` (fecha o caso de alias de alias). */
private fun aliases(textos: Collection<String>, tipo: String): Set<String> {
    val encontrados = mutableSetOf<String>()
    var mudou = true
    while (mudou) {
        mudou = false
        for (texto in textos) {
            for (m in regexesDeAlias(tipo, encontrados)) {
                m.findAll(texto).forEach { if (encontrados.add(it.groupValues[1])) mudou = true }
            }
        }
    }
    return encontrados
}

/** Linhas onde um alias de [tipo] é declarado (só as declarações que levam ao tipo ou a outro alias já conhecido). */
private fun declaracoesDeAlias(texto: String, tipo: String, conhecidos: Set<String>): List<Int> =
    regexesDeAlias(tipo, conhecidos).flatMap { r -> r.findAll(texto).map { linhaDe(texto, it.range.first) }.toList() }

private fun regexesDeAlias(tipo: String, conhecidos: Set<String>): List<Regex> {
    val alvo = (listOf(tipo) + conhecidos).joinToString("|") { Regex.escape(it) }
    return listOf(
        Regex("import\\s+[\\w.]*(?<![\\w])(?:$alvo)\\s+as\\s+(\\w+)"),
        Regex("typealias\\s+(\\w+)\\s*=\\s*[\\w.]*(?<![\\w])(?:$alvo)(?![\\w])"),
    )
}

/**
 * Troca comentários por espaços, preservando as quebras de linha (os números de linha continuam valendo).
 * Reconhece strings (`"..."` com escape e `"""..."""`) e literais de caractere para que `//` dentro delas
 * (como em `"http://x"`) não abra comentário.
 */
internal fun semComentarios(texto: String): String {
    val saida = StringBuilder(texto.length)
    var i = 0
    val n = texto.length
    while (i < n) {
        val c = texto[i]
        val proximo = if (i + 1 < n) texto[i + 1] else '\u0000'
        when {
            c == '/' && proximo == '/' -> {
                while (i < n && texto[i] != '\n') { saida.append(' '); i++ }
            }
            c == '/' && proximo == '*' -> {
                var nivel = 0
                while (i < n) {
                    if (texto.startsWith("/*", i)) { nivel++; saida.append("  "); i += 2 }
                    else if (texto.startsWith("*/", i)) { nivel--; saida.append("  "); i += 2; if (nivel == 0) break }
                    else { saida.append(if (texto[i] == '\n') '\n' else ' '); i++ }
                }
            }
            texto.startsWith("\"\"\"", i) -> {
                val fim = texto.indexOf("\"\"\"", i + 3).let { if (it < 0) n else it + 3 }
                saida.append(texto, i, fim); i = fim
            }
            c == '"' -> {
                saida.append(c); i++
                while (i < n && texto[i] != '"' && texto[i] != '\n') {
                    if (texto[i] == '\\' && i + 1 < n) { saida.append(texto[i]); i++ }
                    saida.append(texto[i]); i++
                }
                if (i < n && texto[i] == '"') { saida.append('"'); i++ }
            }
            c == '\'' -> {
                // Literal de caractere ('"', '\n', 'A'): copia até o apóstrofo que fecha, no máximo 8 caracteres.
                val fim = (i + 1 until minOf(n, i + 9)).firstOrNull { texto[it] == '\'' && texto[it - 1] != '\\' }
                if (fim != null) { saida.append(texto, i, fim + 1); i = fim + 1 } else { saida.append(c); i++ }
            }
            else -> { saida.append(c); i++ }
        }
    }
    return saida.toString()
}
