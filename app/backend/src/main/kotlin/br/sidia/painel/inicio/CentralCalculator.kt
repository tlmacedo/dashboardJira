package br.sidia.painel.inicio

import br.sidia.painel.cards.AvisoDto
import br.sidia.painel.cards.fechadas.SemanaDto
import br.sidia.painel.cards.previsao.QuandoDto

/**
 * Os indicadores da Central de atenção (VG-09), na ordem da tela. [cartao] e [segmento] são os do cartão da Central que abre as
 * tasks (`VG-09`, ou `VG-13` para o indício); sem eles o indicador não tem tasks. [rota] é a tela de destino. `SEM_ATUALIZACAO` e
 * `PARADAS_NO_STATUS` são dois itens: uma task pode estar nos dois e nunca se somam num só.
 */
enum class ItemCentral(internal val cartao: String?, internal val rota: String, internal val gravidade: String) {
    VENCIDAS("VG-09", "negligenciadas", "problema"),
    RISCO_ALTO("VG-09", "riscos", "problema"),
    ACIMA_DA_CAPACIDADE("VG-09", "carga", "problema"),
    ATRASOS_PREVISTOS("VG-09", "capacidade", "problema"),
    SEM_ATUALIZACAO("VG-09", "negligenciadas", "atencao"),
    PARADAS_NO_STATUS("VG-09", "negligenciadas", "atencao"),
    SEM_RESPONSAVEL("VG-09", "negligenciadas", "atencao"),
    BLOQUEADAS("VG-09", "negligenciadas", "problema"),
    AUSENCIAS_14D(null, "capacidade", "neutro"),
    COM_FOLGA("VG-09", "carga", "neutro"),
    META_DE_PRAZO("VG-09", "metas", "meta"),
    PARES_CR_COM_ALERTA("VG-09", "checklistReview", "atencao"),
    TASK_REVIEW_DIVERGENTE("VG-09", "divergencia", "atencao"),
    ALTERACOES_SEM_AUTORIZACAO("VG-09", "auditoria", "problema"),
    INDICIO_ALTO("VG-13", "integridade", "problema"),
    SEM_ESTIMATIVA("VG-09", "qualidade", "fracao"),
}

/**
 * De onde a Central tira tudo. Em produção cada número vem dos cartões `VG-nn` (que chamam os cartões de destino, então o número
 * é o do card de destino); nos testes de unidade, de uma fonte simulada.
 */
interface FontesDaCentral {
    /** Os números do cartão da Central [cartao] (`VG-01`, `VG-09`...) que quem pergunta pode ver; segmento que não aparece não existe para ele. */
    suspend fun valoresDe(cartao: String): Map<String, Int>

    /** Total das abertas com status mapeado (universo da verificação "sem estimativa"); nulo se não dá para calcular. */
    suspend fun universoDeEstimativa(): Int?

    /** Pessoas com alguma ausência nos próximos 14 dias (sem tasks por trás). */
    suspend fun ausentesEm14Dias(): Int

    /** Monte Carlo do backlog (VG-11): backlog, quando termina e a confiança; nulo se indisponível. */
    suspend fun previsao(): PrevisaoDaCentral?

    /** VG-12: um cartão por squad, em ordem alfabética. */
    suspend fun squads(): List<CartaoSquadDto>

    /** Avisos de dados; um `bloqueante` torna os números dependentes indisponíveis. */
    suspend fun avisos(): List<AvisoDto>

    /** Meta de "no prazo" (fração) e faixa de atenção em pontos percentuais, de `metas.yaml`. */
    val metaNoPrazo: Double
    val faixaAtencaoPp: Double
}

class PrevisaoDaCentral(val backlog: Int, val termina: QuandoDto, val confianca: String)

/** Os dois avisos fixos que acompanham todo dado de Integridade (spec 3.1): o item de indício alto nunca aparece sem eles. */
private val AVISOS_FIXOS_DE_INTEGRIDADE = listOf(
    AvisoDto("INDICIO_NAO_PROVA", AvisoDto.INFORMATIVO, "Indício, não prova"),
    AvisoDto("VALIDAR_BASE_LEGAL_COM_DPO", AvisoDto.INFORMATIVO, "Validar a base legal com o DPO antes do uso real"),
)

/** Limites do "sem estimativa" (spec 4.2): acima de 20% é problema; acima de 10%, atenção. */
internal const val LIMITE_SEM_ESTIMATIVA_PROBLEMA = 0.20
internal const val LIMITE_SEM_ESTIMATIVA_ATENCAO = 0.10

/**
 * A Central de atenção. Não calcula as contas dos cards: junta os números que os cartões da Central devolvem, decide o estado de
 * cada item e esconde o que o papel não vê. Sem ranking de pessoas (só os squads, em ordem alfabética, e contagens).
 */
class CentralCalculator(private val fontes: FontesDaCentral) {
    suspend fun calcular(): CentralDto {
        val avisos = fontes.avisos()
        val bloqueado = avisos.any { it.gravidade == AvisoDto.BLOQUEANTE }
        val vg01 = fontes.valoresDe("VG-01")
        val vg04 = fontes.valoresDe("VG-04")
        val vg05 = fontes.valoresDe("VG-05")
        val vg08 = fontes.valoresDe("VG-08")
        val vg09 = fontes.valoresDe("VG-09")
        val vg10 = fontes.valoresDe("VG-10")
        val vg13 = fontes.valoresDe("VG-13")

        fun n(valor: Int?): Int? = valor.takeUnless { bloqueado }
        val universoPrazo = n(vg04["universo"])
        val kpis = listOfNotNull(
            vg01["abertas"]?.let { kpi("ABERTAS", n(it), null, "neutro", "abertas", "VG-01", "abertas") },
            vg05["vencidas"]?.let { kpi("VENCIDAS", n(it), null, estadoDeContagem(n(it), "problema"), "negligenciadas", "VG-05", "vencidas") },
            vg08["alto"]?.let { kpi("RISCO_ALTO", n(it), null, estadoDeContagem(n(it), "problema"), "riscos", "VG-08", "alto") },
            vg04["noPrazo"]?.let { kpi("NO_PRAZO", n(it), universoPrazo, estadoDeMeta(n(it), universoPrazo), "metas", "VG-04", "noPrazo") },
            vg04["noPrazoAjustado"]?.let {
                kpi("NO_PRAZO_AJUSTADO", n(it), universoPrazo, estadoDeMeta(n(it), universoPrazo), "metas", "VG-04", "noPrazoAjustado")
            },
        )

        val universoEstimativa = if (bloqueado) null else fontes.universoDeEstimativa()
        val itens = ItemCentral.entries.mapNotNull { item ->
            if (item == ItemCentral.AUSENCIAS_14D) {
                val ausentes = fontes.ausentesEm14Dias()
                return@mapNotNull ItemCentralDto(item.name, ausentes.toString(), ausentes, null, "neutro", item.rota, null, null)
            }
            val valores = if (item.cartao == "VG-13") vg13 else vg09
            val bruto = valores[item.name] ?: return@mapNotNull null
            val valor = n(bruto)
            val universo = when (item) {
                ItemCentral.META_DE_PRAZO -> universoPrazo
                ItemCentral.SEM_ESTIMATIVA -> universoEstimativa
                else -> null
            }
            val estado = when (item.gravidade) {
                "meta" -> estadoDeMeta(valor, universo)
                "fracao" -> estadoDeFracao(valor, universo)
                else -> estadoDeContagem(valor, item.gravidade)
            }
            ItemCentralDto(item.name, textoDe(valor, universo), valor, universo, estado, item.rota, item.cartao, item.name)
        }

        val previsao = if (bloqueado) null else fontes.previsao()
        return CentralDto(
            kpis = kpis,
            itens = itens,
            entregasPorSemana = vg10.entries.sortedBy { it.key }.map { SemanaDto(it.key.removePrefix("semana:"), it.value) },
            backlogTermina = previsao?.termina,
            backlog = previsao?.backlog,
            confiancaDoBacklog = previsao?.confianca,
            squads = if (bloqueado) emptyList() else fontes.squads().sortedBy { it.squad },
            avisos = avisos + if (itens.any { it.item == ItemCentral.INDICIO_ALTO.name }) AVISOS_FIXOS_DE_INTEGRIDADE else emptyList(),
        )
    }

    private fun kpi(nome: String, n: Int?, universo: Int?, estado: String, rota: String, cartao: String, segmento: String) =
        ItemCentralDto(nome, textoDe(n, universo), n, universo, estado, rota, cartao, segmento)

    private fun textoDe(n: Int?, universo: Int?): String = when {
        n == null -> "—"
        universo != null -> "$n/$universo"
        else -> n.toString()
    }

    /** Contagem: zero é `ok`; acima de zero, o estado da gravidade do item (`problema`, `atencao` ou `neutro`). */
    private fun estadoDeContagem(n: Int?, gravidade: String): String = when {
        n == null -> "indisponivel"
        gravidade == "neutro" -> "neutro"
        n == 0 -> "ok"
        else -> gravidade
    }

    /** "No prazo": a taxa contra a meta de `metas.yaml` (ok na meta, atenção até a faixa abaixo dela, problema abaixo disso). */
    private fun estadoDeMeta(n: Int?, universo: Int?): String {
        if (n == null) return "indisponivel"
        if (universo == null || universo == 0) return "neutro"
        val taxa = n.toDouble() / universo
        return when {
            taxa >= fontes.metaNoPrazo - EPS -> "ok"
            taxa >= fontes.metaNoPrazo - fontes.faixaAtencaoPp / 100.0 - EPS -> "atencao"
            else -> "problema"
        }
    }

    /** "Sem estimativa": a fração do universo (acima de 20% problema, acima de 10% atenção). */
    private fun estadoDeFracao(n: Int?, universo: Int?): String {
        if (n == null) return "indisponivel"
        if (universo == null || universo == 0) return if (n == 0) "ok" else "atencao"
        val f = n.toDouble() / universo
        return when {
            f > LIMITE_SEM_ESTIMATIVA_PROBLEMA + EPS -> "problema"
            f > LIMITE_SEM_ESTIMATIVA_ATENCAO + EPS -> "atencao"
            else -> "ok"
        }
    }

    private companion object {
        const val EPS = 1e-9
    }
}
