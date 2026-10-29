package br.sidia.painel.docs

import br.sidia.painel.TestPaths
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** B4: toda tela de dados do menu tem a sua ficha em `app/config/cards/<id>.card.yaml` (ou uma isenção com motivo). */
class FichasDoMenuTest {
    /** Telas do menu que NÃO têm ficha própria, cada uma com o motivo (e a ficha ou o cartão que as cobre). */
    private val isentas = mapOf(
        "squad" to "monta-se dos cards Carga, Vencimentos, Capacidade e Riscos com o filtro de squad",
        "meusDados" to "lê só o banco do painel e o diretório de papéis (direito de acesso do titular), sem consulta ao Jira",
        "meu" to "monta-se dos cards Carga, Metas, Riscos e Agenda com o filtro de pessoa",
        "divergencia" to "é a Task x Review, coberta pela ficha da auditoria",
        "rebalanceamento" to "usa a ficha da distribuição",
        "decisoes" to "lê só a tabela de decisões do painel, sem consulta ao Jira",
        "registroAcesso" to "lê só o access_log do painel, sem consulta ao Jira",
        "configuracao" to "mostra só o estado dos arquivos de configuração do painel",
        "mapaCartoes" to "é gerado do catálogo de cartões, sem consulta",
    )

    /** Nome da ficha de cada item do menu (camelCase vira kebab; `tempo` é a ficha do Tempo em status). */
    private fun fichaDe(id: String): String =
        if (id == "tempo") "tempo-em-status" else id.replace(Regex("([A-Z])")) { "-" + it.value.lowercase() }

    private fun idsDoMenu(): List<String> {
        val nav = Files.readString(TestPaths.repoDir.resolve("app/frontend/src/nav/nav.ts"))
        return Regex("""\bitem\('([A-Za-z]+)'""").findAll(nav).map { it.groupValues[1] }.toList()
    }

    @Test
    fun cadaItemDoMenuTemFicha() {
        val ids = idsDoMenu()
        assertTrue(ids.size > 30, "o menu tem poucos itens: ${ids.size}")
        val faltando = ids.filter { it !in isentas }.filter {
            !Files.isRegularFile(TestPaths.configDir.resolve("cards/${fichaDe(it)}.card.yaml"))
        }
        assertEquals(emptyList(), faltando, "item do menu sem ficha em app/config/cards")
        // Toda isenção aponta para um item que existe (a lista não envelhece).
        assertEquals(emptySet(), isentas.keys - ids.toSet())
    }

    @Test
    fun centralEAuditoriaTemFicha() {
        for (id in listOf("central", "auditoria", "integridade")) {
            assertTrue(Files.isRegularFile(TestPaths.configDir.resolve("cards/$id.card.yaml")), id)
        }
    }
}
