package br.sidia.painel.jira

/** Tipo de credencial aceita pela instância: token de portador (PAT) ou Basic (usuário:token). */
enum class JiraAuthKind { BEARER, BASIC }

/** Configuração de uma instância Jira. O `token` nunca aparece em `toString`. */
data class JiraInstanceConfig(
    val id: String,
    val baseUrl: String,
    val auth: JiraAuthKind,
    val token: String,
    val username: String? = null,
) {
    // Mascara o segredo para que logs e mensagens de erro nunca o exponham.
    override fun toString(): String =
        "JiraInstanceConfig(id=$id, baseUrl=$baseUrl, auth=$auth, token=***, username=$username)"
}
