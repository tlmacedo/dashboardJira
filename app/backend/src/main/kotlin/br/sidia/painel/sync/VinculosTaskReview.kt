package br.sidia.painel.sync

import br.sidia.painel.domain.ORDEM_NATURAL_DE_CHAVES
import com.charleskorn.kaml.Yaml
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

private val RAIZ = Locale.ROOT

/**
 * Como a Review se liga à sua task: pelo link de issue [tipoDeLink] ("Blocks / is blocked by"), nas duas direções
 * (nunca pelo `parent`). Tipos de issue sem diferenciar maiúsculas. [tiposTask] vazio = qualquer tipo fora de
 * [tiposReview]. Carregado de `vinculos.yaml` (sem dado pessoal).
 */
data class VinculosConfig(
    val versao: Int,
    val tipoDeLink: String = "Blocks",
    val tiposReview: Set<String>,
    val tiposTask: Set<String> = emptySet(),
) {
    private val review = tiposReview.map { it.trim().lowercase(RAIZ) }.toSet()
    private val task = tiposTask.map { it.trim().lowercase(RAIZ) }.toSet()

    fun ehReview(tipo: String): Boolean = tipo.trim().lowercase(RAIZ) in review

    fun ehTask(tipo: String): Boolean =
        !ehReview(tipo) && (task.isEmpty() || tipo.trim().lowercase(RAIZ) in task)

    companion object {
        /**
         * Estrito: chave desconhecida, versão diferente de 1, `tipoDeLink` vazio, `tiposReview` vazio ou um tipo em
         * `tiposReview` e `tiposTask` ao mesmo tempo falham citando o arquivo e o motivo.
         */
        fun fromYaml(path: Path): VinculosConfig {
            val bruto = try {
                Yaml.default.decodeFromString(VinculosYaml.serializer(), Files.readString(path))
            } catch (e: SerializationException) {
                throw IllegalArgumentException("$path: configuração de vínculos inválida: ${e.message}", e)
            }
            fun invalido(motivo: String): Nothing = throw IllegalArgumentException("$path: configuração de vínculos inválida: $motivo")
            if (bruto.versao != 1) invalido("versao ${bruto.versao} não suportada (esperado: 1)")
            if (bruto.tipoDeLink.isBlank()) invalido("tipoDeLink vazio")
            if (bruto.tiposReview.isEmpty() || bruto.tiposReview.any { it.isBlank() }) {
                invalido("tiposReview deve ter ao menos um tipo de issue, sem itens vazios")
            }
            if (bruto.tiposTask.any { it.isBlank() }) invalido("tiposTask com item vazio")
            val comoReview = bruto.tiposReview.associateBy { it.trim().lowercase(RAIZ) }
            bruto.tiposTask.firstOrNull { it.trim().lowercase(RAIZ) in comoReview }
                ?.let { invalido("o tipo '$it' está em tiposReview e em tiposTask") }
            return VinculosConfig(bruto.versao, bruto.tipoDeLink.trim(), bruto.tiposReview.toSet(), bruto.tiposTask.toSet())
        }
    }
}

@Serializable
private data class VinculosYaml(
    val versao: Int,
    val tipoDeLink: String = "Blocks",
    val tiposReview: List<String>,
    val tiposTask: List<String> = emptyList(),
)

/** Um par Task x Review. */
data class ParDeChaves(val task: String, val review: String)

/** [ambiguas]: candidatos descartados no desempate; [ignorados]: vínculos do tipo certo com ponta fora da base. */
data class ResolucaoDePares(val pares: List<ParDeChaves>, val ambiguas: Int, val ignorados: Int)

object ParesTaskReview {
    /**
     * Pares Task x Review a partir dos vínculos. [tipoDe] mapeia chave -> `issuetype` das issues ATIVAS da instância;
     * ponta ausente do mapa é ignorada. Regras, nesta ordem: (1) só o `tipoDeLink` da configuração; (2) as duas pontas
     * na base; (3) é candidato só o vínculo com exatamente uma ponta Review e a outra task (Review x Review e task x task
     * ficam de fora); (4) desempate: para cada Review, a task de menor chave natural; depois, para cada task que
     * sobrou com mais de uma Review, a Review de menor chave; (5) nunca dois pares iguais.
     */
    fun resolver(vinculos: List<VinculoRecord>, tipoDe: Map<String, String>, cfg: VinculosConfig): ResolucaoDePares {
        var ignorados = 0
        val candidatos = LinkedHashSet<ParDeChaves>()
        for (v in vinculos.distinct()) {
            if (!v.tipo.equals(cfg.tipoDeLink, ignoreCase = true)) continue
            val tipoA = tipoDe[v.fromKey]
            val tipoB = tipoDe[v.toKey]
            if (tipoA == null || tipoB == null) {
                ignorados++
                continue
            }
            val aReview = cfg.ehReview(tipoA)
            if (aReview == cfg.ehReview(tipoB)) continue // Review x Review ou task x task
            val par = if (aReview) ParDeChaves(task = v.toKey, review = v.fromKey) else ParDeChaves(task = v.fromKey, review = v.toKey)
            if (cfg.ehTask(if (aReview) tipoB else tipoA)) candidatos += par
        }
        var ambiguas = 0
        // Para cada Review, fica a task de menor chave natural.
        val porReview = candidatos.groupBy { it.review }.map { (_, ps) ->
            ambiguas += ps.size - 1
            ps.minWith(compareBy(ORDEM_NATURAL_DE_CHAVES) { it.task })
        }
        // Para cada task que sobrou com mais de uma Review, fica a Review de menor chave.
        val pares = porReview.groupBy { it.task }.map { (_, ps) ->
            ambiguas += ps.size - 1
            ps.minWith(compareBy(ORDEM_NATURAL_DE_CHAVES) { it.review })
        }.sortedWith(compareBy<ParDeChaves, String>(ORDEM_NATURAL_DE_CHAVES) { it.task }.thenBy(ORDEM_NATURAL_DE_CHAVES) { it.review })
        return ResolucaoDePares(pares, ambiguas, ignorados)
    }
}
