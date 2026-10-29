package br.sidia.painel.security

import java.util.Locale

/**
 * Como o username do Jira sai da claim do provedor (Plano 13, T2). Uma só estratégia por instalação, escolhida em
 * `OIDC_USERNAME_ESTRATEGIA`: não há tentativa em cascata, porque tentar várias formas poderia casar com OUTRA pessoa.
 */
enum class EstrategiaDeUsername {
    /** A claim já é o username (Keycloak e similares). */
    CLAIM_DIRETO,

    /** A claim é um e-mail/UPN (`ana.silva@empresa`): vale a parte antes do `@`, SE o domínio for permitido (Entra ID). */
    PARTE_LOCAL_DO_EMAIL,

    /** A claim é uma conta do Windows (`DOMINIO\ana.silva`): vale a parte depois da barra, SE o domínio for permitido (ADFS). */
    CONTA_WINDOWS,
    ;

    /** As duas estratégias que descartam um pedaço da claim exigem `OIDC_DOMINIOS_PERMITIDOS`. */
    val exigeDominio: Boolean get() = this != CLAIM_DIRETO

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
     * Em `parte_local_do_email` e `conta_windows` o domínio TEM de estar em [dominiosPermitidos] (minúsculas, comparação exata,
     * sem sufixo nem subdomínio parcial): sem lista, nada casa. Nessas duas, texto não ASCII também é recusado (homógrafos).
     */
    fun extrair(
        claims: Map<String, Any?>,
        claim: String,
        estrategia: EstrategiaDeUsername,
        dominiosPermitidos: Set<String> = emptySet(),
    ): String? = (claims[claim] as? String)?.let { extrairDoTexto(it, estrategia, dominiosPermitidos) }

    /** Mesma regra sobre o valor bruto já lido da claim. */
    fun extrairDoTexto(valor: String, estrategia: EstrategiaDeUsername, dominiosPermitidos: Set<String> = emptySet()): String? {
        val texto = valor.trim()
        if (texto.isEmpty()) return null
        val bruto = when (estrategia) {
            EstrategiaDeUsername.CLAIM_DIRETO -> texto
            EstrategiaDeUsername.PARTE_LOCAL_DO_EMAIL -> parteComDominio(texto, '@', dominiosPermitidos, usuarioAntes = true)
            EstrategiaDeUsername.CONTA_WINDOWS -> parteComDominio(texto, '\\', dominiosPermitidos, usuarioAntes = false)
        } ?: return null
        return try {
            normalizarUsername(bruto)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /**
     * Divide em exatamente duas partes pelo [separador]; as duas não podem ser vazias, o texto tem de ser ASCII sem controle e o
     * domínio (o lado que não é o usuário) tem de estar em [permitidos]. Devolve o lado do usuário.
     */
    private fun parteComDominio(texto: String, separador: Char, permitidos: Set<String>, usuarioAntes: Boolean): String? {
        if (permitidos.isEmpty()) return null
        if (texto.any { it.code !in 0x21..0x7E }) return null
        if (texto.count { it == separador } != 1) return null
        val i = texto.indexOf(separador)
        val esquerda = texto.substring(0, i)
        val direita = texto.substring(i + 1)
        if (esquerda.isBlank() || direita.isBlank()) return null
        val dominio = (if (usuarioAntes) direita else esquerda).lowercase(Locale.ROOT)
        if (dominio !in permitidos) return null
        return if (usuarioAntes) esquerda else direita
    }
}
