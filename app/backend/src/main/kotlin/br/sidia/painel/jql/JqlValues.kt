package br.sidia.painel.jql

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

/**
 * Valor recusado pelo validador. É um [IllegalArgumentException], como os demais erros de forma
 * da entrada (uma rota HTTP trata uma família só). O valor original fica guardado só para
 * diagnóstico/auditoria; a mensagem (que pode ir para log) leva no máximo 80 caracteres,
 * com caracteres de controle trocados por `?`.
 */
class InvalidJqlValueException(val kind: String, val value: String) :
    IllegalArgumentException("Valor inválido para $kind: ${amostra(value)}") {
    private companion object {
        fun amostra(v: String): String = v.take(80).map { if (Character.isISOControl(it)) '?' else it }.joinToString("")
    }
}

/**
 * Validadores de valores que entram em JQL.
 * Regra: REJEITAR o que for suspeito; nunca escapar. Sem regex com backtracking:
 * toda checagem é por laço simples sobre a string, e o tamanho é conferido antes.
 */
object JqlValues {
    private const val MAX_TEXTO = 120
    private const val MAX_USERNAME = 64
    private const val MIN_DIAS = 1
    private const val MAX_DIAS = 3650

    // uuuu (ano proleptico) com STRICT: 2026-02-30 é recusado em vez de ajustado.
    private val formatoData: DateTimeFormatter =
        DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT)

    /** Data `aaaa-mm-dd` (4 dígitos ASCII) existente no calendário. */
    fun isoDate(value: String): String {
        if (value.length != 10) throw InvalidJqlValueException("data", value)
        for (i in 0 until 10) {
            val c = value[i]
            val ok = if (i == 4 || i == 7) c == '-' else c in '0'..'9'
            if (!ok) throw InvalidJqlValueException("data", value)
        }
        try {
            LocalDate.parse(value, formatoData)
        } catch (e: java.time.format.DateTimeParseException) {
            throw InvalidJqlValueException("data", value)
        }
        return value
    }

    /** Username: `[a-z0-9._-]`, 1 a 64 caracteres, sem começar com hífen. */
    fun username(value: String): String {
        if (value.isEmpty() || value.length > MAX_USERNAME || value[0] == '-') {
            throw InvalidJqlValueException("username", value)
        }
        for (c in value) {
            val ok = c in 'a'..'z' || c in '0'..'9' || c == '.' || c == '_' || c == '-'
            if (!ok) throw InvalidJqlValueException("username", value)
        }
        return value
    }

    /** Só aceita valor presente na lista (comparação exata); devolve entre aspas duplas. */
    fun literal(value: String, allowed: Collection<String>): String {
        if (value.length > MAX_TEXTO || value !in allowed) throw InvalidJqlValueException("literal", value)
        return quoteChecked("literal", value)
    }

    /** `startOfDay(-N)` com 1 <= N <= 3650. */
    fun relativeDays(n: Int): String {
        if (n < MIN_DIAS || n > MAX_DIAS) throw InvalidJqlValueException("dias", n.toString())
        return "startOfDay(-$n)"
    }

    /**
     * Nome de campo: simples sai sem aspas; `cf[123]` e `customfield_123` sem aspas;
     * demais nomes (espaço, hífen, acento) entre aspas duplas.
     */
    fun quotedField(name: String): String {
        if (name.isEmpty() || name.length > MAX_TEXTO) throw InvalidJqlValueException("campo", name)
        if (isSimpleName(name) || isFieldId(name)) return name
        return quoteChecked("campo", name)
    }

    /** Texto livre seguro: sem aspas, barra, controle, unicode invisível, em branco ou acima de 120. */
    fun safeText(value: String): String {
        if (value.length > MAX_TEXTO) throw InvalidJqlValueException("texto", value)
        return quoteChecked("texto", value)
    }

    // --- internos ---

    private fun isSimpleName(s: String): Boolean {
        if (!(s[0] in 'a'..'z' || s[0] in 'A'..'Z')) return false
        for (c in s) {
            if (!(c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '_')) return false
        }
        return true
    }

    private fun allDigits(s: String, from: Int, to: Int): Boolean {
        if (to <= from) return false
        for (i in from until to) if (s[i] !in '0'..'9') return false
        return true
    }

    private fun isFieldId(s: String): Boolean =
        (s.startsWith("cf[") && s.endsWith("]") && allDigits(s, 3, s.length - 1)) ||
            (s.startsWith("customfield_") && allDigits(s, 12, s.length))

    /** Valida o texto (rejeitando, sem escapar) e devolve entre aspas duplas. */
    private fun quoteChecked(kind: String, value: String): String {
        if (value.isBlank()) throw InvalidJqlValueException(kind, value)
        var i = 0
        while (i < value.length) {
            val cp = value.codePointAt(i)
            if (cp == '"'.code || cp == '\\'.code || !isAllowedChar(cp)) {
                throw InvalidJqlValueException(kind, value)
            }
            i += Character.charCount(cp)
        }
        return "\"$value\""
    }

    /** Permite espaço ASCII e caracteres visíveis; recusa controle, formatação (zero-width, direção), separadores exóticos etc. */
    private fun isAllowedChar(cp: Int): Boolean {
        if (cp == ' '.code) return true
        return when (Character.getType(cp).toByte()) {
            Character.CONTROL, Character.FORMAT, Character.LINE_SEPARATOR,
            Character.PARAGRAPH_SEPARATOR, Character.SPACE_SEPARATOR,
            Character.PRIVATE_USE, Character.SURROGATE, Character.UNASSIGNED -> false
            else -> true
        }
    }
}
