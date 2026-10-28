package br.sidia.painel.config

/**
 * Perfil de execução, escolhido pela propriedade de JVM `painel.perfil`.
 *
 * O padrão de `DASHBOARD_DEV_MODE` é `true` (decisão do projeto para o desenvolvimento). Num pacote instalado como
 * serviço (`installDist`), um arquivo de variáveis esquecido faria o painel subir SEM login e com todos os dados. Por isso
 * só os scripts do pacote (`bin/painel` e `bin/painel.bat`) passam `-Dpainel.perfil=producao`; `iniciar.*` e
 * `./gradlew run` não passam e seguem em modo de desenvolvimento.
 */
object PerfilDeExecucao {
    const val PROPRIEDADE = "painel.perfil"
    const val PRODUCAO = "producao"

    /**
     * Sem perfil (nulo, vazio ou só espaços): devolve [env] igual. Perfil `producao`: se `DASHBOARD_DEV_MODE` estiver
     * ausente ou vazia, acrescenta `false`; valor explícito vale (quem escreve a linha de modo dev num pacote a quer; a
     * faixa vermelha continua). Outro perfil: [IllegalArgumentException] citando só o nome da propriedade.
     * Nunca acrescenta nem altera variáveis `JIRA_*`.
     */
    fun aplicar(perfil: String?, env: Map<String, String>): Map<String, String> {
        val p = perfil?.trim()?.takeIf { it.isNotEmpty() } ?: return env
        require(p == PRODUCAO) { "$PROPRIEDADE inválido (esperado '$PRODUCAO' ou ausente)" }
        if (!env["DASHBOARD_DEV_MODE"].isNullOrBlank()) return env
        return env + ("DASHBOARD_DEV_MODE" to "false")
    }
}
