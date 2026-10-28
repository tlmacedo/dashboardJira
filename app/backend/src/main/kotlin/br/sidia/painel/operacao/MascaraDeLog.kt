package br.sidia.painel.operacao

import br.sidia.painel.jira.padraoEscapado
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Máscara de segredos para tudo o que sai em log. Duas camadas:
 * 1. segredos registrados na subida (tokens do Jira, segredos da sessão e do OIDC), pelo valor exato e também nas formas
 *    escapadas em URL, JSON e HTML e em base64 (mesma técnica do cliente do Jira);
 * 2. padrões conhecidos, sempre ativos mesmo sem registro: `Bearer`/`Basic`, `Authorization:`, `chave=valor` de segredo,
 *    credencial em URL e cookie de sessão.
 * A máscara troca só o valor por `***`; o resto do texto fica intacto.
 */
object MascaraDeLog {
    /** Segredos curtos demais gerariam falso positivo em texto comum. */
    private const val TAMANHO_MINIMO = 8

    private class Registro(val tamanho: Int, val padrao: Regex)

    private val registrados = CopyOnWriteArrayList<Registro>()

    /** Registra valores que nunca podem aparecer em log. Vazios e com menos de 8 caracteres são ignorados. */
    fun registrarSegredos(valores: Collection<String>) {
        for (valor in valores) {
            if (valor.length < TAMANHO_MINIMO) continue
            adicionar(valor)
            adicionar(Base64.getEncoder().encodeToString(valor.toByteArray(Charsets.UTF_8)))
        }
        // Os mais longos primeiro: o base64 de um par "usuario:token" não contém o token cru.
        val ordenados = registrados.sortedByDescending { it.tamanho }
        registrados.clear()
        registrados.addAll(ordenados)
    }

    private fun adicionar(valor: String) {
        val padrao = padraoEscapado(valor)
        if (registrados.none { it.padrao.pattern == padrao.pattern }) registrados.add(Registro(valor.length, padrao))
    }

    /** Devolve [texto] sem os segredos registrados nem os padrões conhecidos. */
    fun mascarar(texto: String): String {
        if (texto.isEmpty()) return texto
        var r = texto
        // Segredos registrados são literais (busca linear): valem no texto inteiro, ANTES do corte, para que um segredo
        // que cruze o limite não deixe pedaço para trás.
        for (registro in registrados) r = registro.padrao.replace(r, "***")
        // Os padrões genéricos têm custo que cresce com o tamanho da linha: o texto é cortado antes (e o prefixo das
        // regras é limitado a PREFIXO_MAXIMO caracteres). Linha de log com mais de 8 KB não é útil e vira um vetor de DoS.
        val cortado = r.length > LIMITE_DA_LINHA
        if (cortado) r = cortar(r)
        for ((padrao, troca) in PADROES) r = padrao.replace(r, troca)
        return if (cortado) r + MARCA_DE_CORTE else r
    }

    /** Corta em [LIMITE_DA_LINHA] sem deixar uma palavra pela metade (um pedaço de segredo) e marca o corte. */
    private fun cortar(texto: String): String {
        var fim = LIMITE_DA_LINHA
        val inicioDaPalavra = texto.lastIndexOfAny(charArrayOf(' ', '\n', '\r', '\t'), fim - 1)
        if (inicioDaPalavra > fim - 256) fim = inicioDaPalavra
        return texto.substring(0, fim)
    }

    private const val LIMITE_DA_LINHA = 8 * 1024
    private const val MARCA_DE_CORTE = " ...[truncado]"
    private const val PREFIXO_MAXIMO = 64

    /** Só para testes: o registro é global do processo. */
    internal fun limparRegistrosParaTeste() {
        registrados.clear()
    }

    private val PADROES: List<Pair<Regex, (MatchResult) -> CharSequence>> = listOf(
        // `Authorization: <qualquer coisa até o fim da linha>`
        Regex("(?i)(authorization\\s*:\\s*)[^\\r\\n]+") to { m -> m.groupValues[1] + "***" },
        // `Bearer abc...` / `Basic abc...` (credencial de pelo menos 8 caracteres de token)
        Regex("(?i)\\b(Bearer|Basic)\\s+[A-Za-z0-9._~+/=\\-]{8,}") to { m -> m.groupValues[1] + " ***" },
        // `Cookie:` / `Set-Cookie:` inteiro e o cookie de sessão onde aparecer
        Regex("(?i)((?:set-)?cookie\\s*:\\s*)[^\\r\\n]+") to { m -> m.groupValues[1] + "***" },
        Regex("(?i)(painel_sessao=)[^;\\s\"]+") to { m -> m.groupValues[1] + "***" },
        // JSON dentro de JSON (mensagem de log que carrega um corpo JSON): `{\"password\":\"x y\"}` com as aspas escapadas
        // por uma ou mais barras. O valor vai até a próxima aspa escapada.
        Regex(
            "(?i)((?:[A-Za-z0-9_.-]{0,$PREFIXO_MAXIMO}(?:password|passwd|senha|secret|token|apikey|api_key)|\\bpat)\\\\+[\"']\\s*[=:]\\s*\\\\+[\"'])(?:(?!\\\\+[\"']).)*",
        ) to { m -> m.groupValues[1] + "***" },
        // Senha digitada por uma pessoa (`password=a b`, `senha: a b`) pode ter espaços e vir sem aspas: o valor vai até o fim
        // da linha ou até a próxima `chave=` (nunca além de `"`, `'`, `&`, `,` e `;`). Os demais segredos (tokens, chaves)
        // são opacos e param no espaço, na regra seguinte.
        Regex(
            "(?i)((?:[A-Za-z0-9_.-]{0,$PREFIXO_MAXIMO}(?:password|passwd|senha))[\"']?\\s*[=:]\\s*+)(?![\"'])" +
                // Palavras separadas por espaço/tab; o lookahead da próxima `chave=` roda só no início de cada palavra e os
                // quantificadores são possessivos: tempo linear mesmo com milhares de espaços.
                "[^\\s\"'&,;]++(?:[ \\t]++(?![A-Za-z_][A-Za-z0-9_.-]*\\s*=)[^\\s\"'&,;]++)*+",
        ) to { m -> m.groupValues[1] + "***" },
        // `password=x`, `senha: x`, `SESSION_SECRET=x`, `"token":"x"`
        // Valor entre aspas (simples ou duplas): vai até a aspa de fechamento, com espaços e aspas escapadas dentro.
        Regex(
            "(?i)((?:[A-Za-z0-9_.-]{0,$PREFIXO_MAXIMO}(?:password|senha|secret|token|apikey|api_key)|\\bpat)[\"']?\\s*[=:]\\s*)([\"'])(?:\\\\.|(?!\\2)[^\\\\])*\\2",
        ) to { m -> m.groupValues[1] + m.groupValues[2] + "***" + m.groupValues[2] },
        // Sem aspas: até o próximo espaço ou separador.
        Regex(
            "(?i)((?:[A-Za-z0-9_.-]{0,$PREFIXO_MAXIMO}(?:password|senha|secret|token|apikey|api_key)|\\bpat)[\"']?\\s*[=:]\\s*[\"']?)[^\\s\"'&,;]+",
        ) to { m -> m.groupValues[1] + "***" },
        // `//usuario:senha@host` (tudo até o último `@` do trecho vira credencial: seguro por excesso)
        Regex("(?<=://)[^\\s]{0,1024}@") to { _ -> "***@" },
    )
}
