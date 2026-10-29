package br.sidia.painel.lgpd

/**
 * Aceite do encarregado de dados (DPO) para o uso real de Auditoria e Integridade. SÓ o usuário liga o aceite
 * (`DASHBOARD_DPO_ACEITE=true` no ambiente de produção, depois do parecer); nenhuma IA define esse valor.
 *
 * O aceite sem a referência do parecer (`DASHBOARD_DPO_ACEITE_REF`, 1 a 80 caracteres `[A-Za-z0-9._/-]`) não existe:
 * o próprio objeto se recusa a nascer (fail-closed). A referência não é segredo: sai no log de subida e na Configuração.
 */
data class LgpdSettings(val aceiteDpo: Boolean = false, val aceiteRef: String? = null) {
    init {
        if (aceiteDpo) {
            require(aceiteRef != null) { "DASHBOARD_DPO_ACEITE_REF obrigatória com DASHBOARD_DPO_ACEITE=true" }
            require(REFERENCIA_VALIDA.matches(aceiteRef)) { "DASHBOARD_DPO_ACEITE_REF inválida (1 a 80 caracteres de A-Z, a-z, 0-9, ponto, barra, hífen e sublinhado)" }
        }
    }

    /** A referência do parecer quando há aceite; senão nulo. É o que a tela de Configuração mostra. */
    val referenciaDoAceite: String? get() = if (aceiteDpo) aceiteRef else null

    companion object {
        private val REFERENCIA_VALIDA = Regex("[A-Za-z0-9._/-]{1,80}")

        /**
         * Lê `DASHBOARD_DPO_ACEITE` (true/false; vazio = false) e `DASHBOARD_DPO_ACEITE_REF`. Valor inválido, aceite sem
         * referência ou referência fora do formato derrubam a subida citando só o NOME da variável. Referência sem aceite é ignorada.
         */
        fun fromEnv(env: Map<String, String>): LgpdSettings {
            val aceite = when (val bruto = env["DASHBOARD_DPO_ACEITE"]?.trim()?.takeIf { it.isNotEmpty() }?.lowercase()) {
                null, "false" -> false
                "true" -> true
                else -> throw IllegalArgumentException("DASHBOARD_DPO_ACEITE inválido (esperado true ou false)")
            }
            if (!aceite) return LgpdSettings()
            val ref = env["DASHBOARD_DPO_ACEITE_REF"]?.trim()?.takeIf { it.isNotEmpty() }
            return LgpdSettings(aceiteDpo = true, aceiteRef = ref)
        }
    }
}
