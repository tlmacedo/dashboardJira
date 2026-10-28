package br.sidia.painel.governanca

import br.sidia.painel.AppContext
import br.sidia.painel.cards.executarAuditado
import br.sidia.painel.cards.exigirPapel
import br.sidia.painel.cards.hoje
import br.sidia.painel.db.AccessLog
import br.sidia.painel.security.Role
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/**
 * Os arquivos de regras que a Configuração lista. NÃO entram `papeis.yaml`, `equipes.yaml` e `ausencias.yaml` (dados pessoais:
 * nem o hash deles sai) nem `app/.env` (segredos). Os nomes são lógicos: o caminho da pasta nunca é mostrado.
 */
internal val ARQUIVOS_DE_REGRAS = listOf(
    "status_map", "calendario", "labels_atraso", "vinculos", "auditoria", "metas", "risco", "previsao", "planejamento", "esforco",
    "checklist_review", "integridade", "distribuicao", "jira_campos",
)

private val SO_GERENTE = setOf(Role.GERENTE)
private val LINHA_DE_VERSAO = Regex("(?m)^versao:\\s*(\\d{1,6})\\s*(#.*)?$")

/**
 * `GET /api/governanca/configuracao`: a configuração em LEITURA, só do gerente e auditada como qualquer rota de dados. Mostra o
 * ambiente (modo dev, Jira ligado, modo da sincronização), o nome, a versão, o hash e a origem (`real` ou `exemplo`) de cada YAML de
 * regras e das fichas dos cards, e só as contagens de pessoas e squads. NUNCA exibe segredo, token, `SESSION_SECRET`, `OIDC_*`, URL
 * (com ou sem credencial), host, caminho de pasta ou arquivo, nome ou username, nem o valor de `papeis.yaml`, `equipes.yaml` ou
 * `ausencias.yaml`. Não há escrita: o painel não edita configuração.
 */
fun Route.rotasDeConfiguracao(ctx: AppContext, auditoria: AccessLog) {
    get("/api/governanca/configuracao") {
        call.executarAuditado(auditoria, ctx) { principal ->
            exigirPapel(ctx, principal, SO_GERENTE)
            val s = ctx.settings
            ConfiguracaoDto(
                devMode = s.dashboardDevMode,
                liveEnabled = s.jiraLiveEnabled,
                modoSync = when {
                    s.jiraLiveEnabled -> "AO_VIVO"
                    s.dashboardDemoData -> "DEMONSTRACAO"
                    else -> "DESLIGADO"
                },
                arquivos = arquivosCarregados(s.configDir),
                pessoas = ctx.roles.total,
                squads = ctx.roles.squadsEm(hoje(ctx)).size,
            )
        }
    }
}

/** Os arquivos de regras e as fichas que existem na pasta de configuração, em ordem de nome. */
internal fun arquivosCarregados(pasta: Path): List<ArquivoConfigDto> {
    val regras = ARQUIVOS_DE_REGRAS.mapNotNull { nome ->
        val real = pasta.resolve("$nome.yaml")
        val exemplo = pasta.resolve("$nome.exemplo.yaml")
        when {
            Files.isRegularFile(real) -> descrever(nome, real, "real")
            Files.isRegularFile(exemplo) -> descrever(nome, exemplo, "exemplo")
            else -> null
        }
    }
    val fichas = pasta.resolve("cards").let { cards ->
        if (!Files.isDirectory(cards)) {
            emptyList()
        } else {
            Files.list(cards).use { itens ->
                itens.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".card.yaml") }.sorted().toList()
            }.map { descrever("cards/" + it.fileName.toString().removeSuffix(".card.yaml"), it, "real") }
        }
    }
    return (regras + fichas).sortedBy { it.nome }
}

private fun descrever(nome: String, arquivo: Path, origem: String): ArquivoConfigDto {
    val bytes = Files.readAllBytes(arquivo)
    val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    val versao = LINHA_DE_VERSAO.find(String(bytes, Charsets.UTF_8))?.groupValues?.get(1)?.toIntOrNull()
    return ArquivoConfigDto(nome, versao, hash, origem)
}
