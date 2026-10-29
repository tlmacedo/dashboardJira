package br.sidia.painel.security

/**
 * Como o username do Jira sai da claim do provedor (Plano 13, T2). Uma só estratégia por instalação, escolhida em
 * `OIDC_USERNAME_ESTRATEGIA`: não há tentativa em cascata, porque tentar várias formas poderia casar com OUTRA pessoa.
 */
enum class EstrategiaDeUsername {
    /** A claim já é o username (Keycloak e similares). */
    CLAIM_DIRETO,

    /** A claim é um e-mail/UPN (`ana.silva@empresa`): vale a parte antes do `@` (Entra ID). */
    PARTE_LOCAL_DO_EMAIL,

    /** A claim é uma conta do Windows (`DOMINIO\ana.silva`): vale a parte depois da barra (ADFS). */
    CONTA_WINDOWS,
    ;

    companion object {
        /** Lê o valor de `OIDC_USERNAME_ESTRATEGIA` (sem diferenciar maiúsculas); a mensagem cita só o NOME da variável. */
        fun daVariavel(bruto: String?): EstrategiaDeUsername {
            val valor = bruto?.trim()?.takeIf { it.isNotEmpty() } ?: return CLAIM_DIRETO
            return entries.firstOrNull { it.name.equals(valor, ignoreCase = true) }
                ?: throw IllegalArgumentException("OIDC_USERNAME_ESTRATEGIA inválida (esperado claim_direto, parte_local_do_email ou conta_windows)")
        }
    }
}

object ExtratorDeUsername {
    /**
     * Devolve o username normalizado ([normalizarUsername]) ou `null` se a claim faltar, não for texto, ficar vazia ou fugir
     * do formato da [estrategia]. Nunca "adivinha": formato inesperado é `null` (o login responde 401 `USERNAME_ILEGIVEL`).
     */
    fun extrair(claims: Map<String, Any?>, claim: String, estrategia: EstrategiaDeUsername): String? =
        (claims[claim] as? String)?.let { extrairDoTexto(it, estrategia) }

    /** Mesma regra sobre o valor bruto já lido da claim. */
    fun extrairDoTexto(valor: String, estrategia: EstrategiaDeUsername): String? {
        val texto = valor.trim()
        if (texto.isEmpty()) return null
        val bruto = when (estrategia) {
            EstrategiaDeUsername.CLAIM_DIRETO -> texto
            EstrategiaDeUsername.PARTE_LOCAL_DO_EMAIL -> parteUnica(texto, '@', antes = true)
            EstrategiaDeUsername.CONTA_WINDOWS -> parteUnica(texto, '\\', antes = false)
        } ?: return null
        return try {
            normalizarUsername(bruto)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /**
     * Divide em exatamente duas partes pelo [separador] e devolve a que importa; mais (ou menos) de um separador, ou uma das
     * duas partes vazia, = nulo.
     */
    private fun parteUnica(texto: String, separador: Char, antes: Boolean): String? {
        if (texto.count { it == separador } != 1) return null
        val i = texto.indexOf(separador)
        val esquerda = texto.substring(0, i)
        val direita = texto.substring(i + 1)
        if (esquerda.isBlank() || direita.isBlank()) return null
        return if (antes) esquerda else direita
    }
}
