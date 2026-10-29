package br.sidia.painel.cards

import br.sidia.painel.AppContext
import br.sidia.painel.sync.CampoDeFiltro
import br.sidia.painel.sync.CamposDoDicionario
import br.sidia.painel.sync.FiltroDeFicha
import br.sidia.painel.sync.PreenchimentoDeCampos
import br.sidia.painel.trabalho.IssueSnapshot
import br.sidia.painel.trabalho.WorkStore
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.Locale

/** Código estável do 400 de filtro malformado ou desconhecido (`params["campo"]` leva o slug, quando for um slug válido). */
internal const val CODIGO_FILTRO_INVALIDO = "FILTRO_INVALIDO"

/**
 * A issue casa TODOS os campos pedidos? Cada campo precisa ter algum dos valores aceitos, sem diferenciar maiúsculas nem
 * espaços nas pontas; um campo repetível (valores unidos por "; ") casa se ALGUM dos seus valores casar. Issue sem o campo
 * não casa. Sem campos pedidos, todas casam.
 */
fun IssueSnapshot.casaCampos(campos: Map<String, Set<String>>): Boolean = campos.all { (nome, aceitos) ->
    val valor = camposDicionario[nome] ?: return@all false
    val candidatos = CamposDoDicionario.partesDe(valor) + valor.trim()
    aceitos.any { a -> candidatos.any { it.equals(a.trim(), ignoreCase = true) } }
}

/**
 * O [WorkStore] com o filtro por campo aplicado a TODA leitura de issues, antes de qualquer cálculo: o card e o drill
 * leem a mesma lista filtrada, então "card = tamanho do detalhe" vale também com `campo.<slug>`.
 */
internal class WorkStoreFiltradoPorCampos(private val base: WorkStore, private val campos: Map<String, Set<String>>) : WorkStore by base {
    override fun snapshots(c: br.sidia.painel.trabalho.ConsultaDeTrabalho): List<IssueSnapshot> =
        base.snapshots(c).filter { it.casaCampos(campos) }

    override fun comEventoEntre(
        instance: String, filtro: FiltroDeFicha?, campos: Set<String>, de: Instant, ate: Instant,
    ): List<IssueSnapshot> = base.comEventoEntre(instance, filtro, campos, de, ate).filter { it.casaCampos(this.campos) }
}

/** Um grupo de um campo do dicionário: [valor] nulo = "sem valor". */
@Serializable
data class ContagemGrupoDto(val valor: String?, val n: Int)

/** Agrupamento por um campo do dicionário (grupos alfabéticos, "sem valor" por último). */
@Serializable
data class AgrupamentoDto(val campo: String, val slug: String, val grupos: List<ContagemGrupoDto>)

/** O agrupamento e, por segmento `campo:<slug>:<valor>` (`-` = sem valor), as issues de cada grupo. */
class AgrupamentoPorCampos(val dtos: List<AgrupamentoDto>, val segmentos: Map<String, List<IssueSnapshot>>) {
    companion object {
        val VAZIO = AgrupamentoPorCampos(emptyList(), emptyMap())
        const val SEM_VALOR = "-"
    }
}

/**
 * Um agrupamento por campo de [campos], na ordem dada. Um campo repetível ("A; B") entra uma vez em cada um dos seus
 * valores (a soma dos grupos pode passar do total de issues); o mesmo valor com outra caixa ou espaço sobrando é um grupo
 * só, com a primeira grafia em ordem alfabética.
 */
fun agruparPorCampos(issues: List<IssueSnapshot>, campos: List<CampoDeFiltro>): AgrupamentoPorCampos {
    val segmentos = LinkedHashMap<String, List<IssueSnapshot>>()
    val dtos = campos.map { c ->
        val porValor = HashMap<String, MutableList<IssueSnapshot>>()
        val grafia = HashMap<String, String>()
        val sem = mutableListOf<IssueSnapshot>()
        for (s in issues) {
            val partes = s.camposDicionario[c.nome]?.let { CamposDoDicionario.partesDe(it) }.orEmpty()
            if (partes.isEmpty()) {
                sem += s
                continue
            }
            for (p in partes.distinctBy { it.lowercase(Locale.ROOT) }) {
                val k = p.lowercase(Locale.ROOT)
                grafia.merge(k, p) { a, b -> if (b < a) b else a }
                porValor.getOrPut(k) { mutableListOf() } += s
            }
        }
        val grupos = porValor.keys.sorted().map { k ->
            val valor = grafia.getValue(k)
            segmentos["campo:${c.slug}:$valor"] = porValor.getValue(k)
            ContagemGrupoDto(valor, porValor.getValue(k).size)
        }
        val comSem = if (sem.isEmpty()) grupos else {
            segmentos["campo:${c.slug}:${AgrupamentoPorCampos.SEM_VALOR}"] = sem
            grupos + ContagemGrupoDto(null, sem.size)
        }
        AgrupamentoDto(c.nome, c.slug, comSem)
    }
    return AgrupamentoPorCampos(dtos, segmentos)
}

/** Os campos de filtro do dicionário para a instância de trabalho dos cards. */
internal fun camposDeFiltroDe(ctx: AppContext): List<CampoDeFiltro> = CamposDoDicionario.deFiltro(ctx.campos, INSTANCIA_TRABALHO)

/**
 * Aviso informativo `CAMPOS_DO_DICIONARIO_INCOMPLETOS` enquanto houver issue só preenchida do `raw` antigo (a reconciliação
 * completa, com a flag ligada pelo usuário, as completa). Sem campo com customId não há o que completar: sem aviso.
 */
internal fun avisoDeCamposIncompletos(ctx: AppContext): AvisoDto? {
    val ds = ctx.dataSource ?: return null
    if (camposDeFiltroDe(ctx).none { it.customId != null }) return null
    val n = PreenchimentoDeCampos(ds).linhasIncompletas(INSTANCIA_TRABALHO)
    if (n == 0) return null
    return AvisoDto(
        AvisoDto.CAMPOS_DO_DICIONARIO_INCOMPLETOS, AvisoDto.INFORMATIVO, "$n issue(s) com campos do dicionário a completar",
        mapOf("n" to n.toString()),
    )
}
