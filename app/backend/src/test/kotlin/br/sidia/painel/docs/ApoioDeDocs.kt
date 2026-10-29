package br.sidia.painel.docs

import br.sidia.painel.TestPaths
import br.sidia.painel.cards.contrato.CartaoMeta
import br.sidia.painel.cards.contrato.catalogoPadrao
import java.nio.file.Files
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Apoio dos testes que amarram documentos (guias, perguntas frequentes, treinamento, DESIGN.md) ao código: o catálogo de
 * cartões, o menu (`nav.ts`) e as traduções. Nada aqui chama o Jira nem a rede.
 */
internal object ApoioDeDocs {
    fun texto(relativo: String): String = Files.readString(TestPaths.repoDir.resolve(relativo))

    fun existe(relativo: String): Boolean = Files.exists(TestPaths.repoDir.resolve(relativo))

    /** Um item do menu, com os papéis que o veem (nomes do enum do backend, como `USUARIO`). */
    data class ItemDoMenu(val id: String, val grupo: String, val papeis: Set<String>)

    val todosOsPapeis = listOf("USUARIO", "SQUAD_LEADER", "COORDENADOR", "GERENTE")
    private val alta = setOf("COORDENADOR", "GERENTE")
    private val lideranca = setOf("SQUAD_LEADER", "COORDENADOR", "GERENTE")

    /** Lê `nav.ts` na ordem do arquivo (que é a ordem do menu: grupos na ordem do plano visual). */
    fun menu(): List<ItemDoMenu> {
        val nav = texto("app/frontend/src/nav/nav.ts")
        val regex = Regex("""(?m)^\s*item\('(\w+)', '(\w+)', ([^)]*?)\),?\s*$""")
        return regex.findAll(nav).map { m ->
            val bruto = m.groupValues[3].trim()
            val papeis = when {
                bruto == "'all'" -> todosOsPapeis.toSet()
                bruto == "ALTA" -> alta
                bruto == "LIDERANCA" -> lideranca
                else -> Regex("""'(\w+)'""").findAll(bruto).map { it.groupValues[1] }.toSet()
            }
            ItemDoMenu(m.groupValues[1], m.groupValues[2], papeis)
        }.toList()
    }

    /** Traduções planas do frontend: `lang` é `pt` ou `en`. */
    fun traducoes(lang: String): Map<String, String> {
        val json = Json.parseToJsonElement(texto("app/frontend/src/i18n/$lang.json")).jsonObject
        return json.mapValues { it.value.jsonPrimitive.content }
    }

    /** Os cartões registrados no catálogo (a fonte da verdade do que existe), por id. */
    fun cartoes(): Map<String, CartaoMeta> = catalogoPadrao().cartoes.associate { it.meta.id to it.meta }

    /** Telas do catálogo cujo id difere do id do item do menu. */
    val telaDoCatalogoParaMenu = mapOf(
        "tempo-em-status" to "tempo",
        "task-review" to "divergencia",
        "aging-wip" to "agingWip",
        "fluxo-da-semana" to "fluxoDaSemana",
        "controle-ciclo" to "fechadas",
        "carga-tipo" to "cargaTipo",
        "checklist-review" to "checklistReview",
    )

    fun itemDoMenuDe(meta: CartaoMeta): String = telaDoCatalogoParaMenu[meta.tela] ?: meta.tela

    /** Rótulo de cada papel nos documentos, por idioma. */
    fun rotuloDoPapel(lang: String): Map<String, String> = if (lang == "pt") {
        mapOf("USUARIO" to "Usuário", "SQUAD_LEADER" to "Squad Leader", "COORDENADOR" to "Coordenador", "GERENTE" to "Gerente")
    } else {
        mapOf("USUARIO" to "User", "SQUAD_LEADER" to "Squad Leader", "COORDENADOR" to "Coordinator", "GERENTE" to "Manager")
    }

    /** Ids de cartão (formato `AA-nn`) citados num texto. */
    fun idsDeCartaoCitados(texto: String): Set<String> =
        Regex("""\b[A-Z]{2}-\d{2}\b""").findAll(texto).map { it.value }.toSet()

    /** Endereços de rede num texto (documentos de entrega não citam host). */
    fun enderecosDeRede(texto: String): List<String> =
        Regex("""https?://[^\s)>`]+""").findAll(texto).map { it.value }.filterNot { it.startsWith("http://127.0.0.1") || it.startsWith("http://localhost") }.toList()
}
