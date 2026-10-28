package br.sidia.painel.cards.fluxo

import br.sidia.painel.cards.abertas.AbertasCalculator
import br.sidia.painel.domain.LabelsDeAtraso
import br.sidia.painel.domain.ORDEM_NATURAL_DE_CHAVES
import br.sidia.painel.domain.Percentiles
import br.sidia.painel.domain.StatusCategory
import br.sidia.painel.domain.StatusMap
import br.sidia.painel.domain.TimeUnitMode
import br.sidia.painel.time.BusinessCalendar
import br.sidia.painel.trabalho.Fluxo
import br.sidia.painel.trabalho.IssueSnapshot
import java.time.DayOfWeek
import java.time.Instant
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * Os eixos da lista FECHADA do Cruzamentos. [soLinha]: pessoa só pode ser linha (nunca coluna). Os campos do dicionário
 * (Checklist Type, SKU Type...) NÃO são eixos aqui: dependem do Plano 10 (ver [CruzamentosCalculator.DICIONARIO_PENDENTE]).
 */
enum class Eixo(val id: String, val soLinha: Boolean = false) {
    SQUAD("squad"), CATEGORIA("categoria"), STATUS("status"), TIPO("tipo"), PRIORIDADE("prioridade"), OS("os"),
    IDADE("idade"), VENCIMENTO("vencimento"), ATRASO("atraso"), PESSOA("pessoa", soLinha = true);

    companion object {
        fun deId(id: String): Eixo? = entries.firstOrNull { it.id == id }
    }
}

enum class Medida(val id: String) {
    CONTAGEM("contagem"), IDADE_P50("idadeP50");

    companion object {
        fun deId(id: String): Medida? = entries.firstOrNull { it.id == id }
    }
}

/** O DTO, por segmento as tasks por trás de cada número e a idade (na unidade pedida) de cada task, por chave. */
class CruzamentosAnalise(val dto: CruzamentosDto, val segmentos: Map<String, List<IssueSnapshot>>, val idades: Map<String, Double>)

/**
 * Cruzamentos (I-10): tabela cruzada das tasks ABERTAS (fora da categoria concluído) sobre dois eixos da lista fechada
 * ([Eixo]), com a medida contagem ou idade P50. A ordem de cada eixo é a do próprio eixo (categoria pela ordem do fluxo,
 * status pela `ordem` do `status_map`, prioridade de Highest a Lowest, faixa de idade natural, semana de vencimento
 * cronológica, o resto em ordem alfabética) e `(sem)` vem por último: NUNCA se ordena por contagem. Pessoa só como linha,
 * sem total por pessoa. Os segmentos do drill são posicionais (`c:<linha>:<coluna>`, `l:`, `k:`, `total`), então nenhum
 * valor de eixo (que pode ser nome de pessoa) vai para o `access_log`. As tasks já vêm no escopo de quem consulta.
 */
class CruzamentosCalculator(
    private val cal: BusinessCalendar,
    private val map: StatusMap,
    private val labels: LabelsDeAtraso,
    /** Squad da pessoa na data de hoje (diretório de papéis); nulo se ela não tem squad. */
    private val squadDe: (String) -> String?,
) {
    fun analisar(
        abertas: List<IssueSnapshot>, agora: Instant, unit: TimeUnitMode, linhas: Eixo, colunas: Eixo, medida: Medida,
    ): CruzamentosAnalise {
        require(!colunas.soLinha) { "o eixo ${colunas.id} só pode ser linha" }
        val tasks = abertas.filter { map.category(it.status) != StatusCategory.DONE }
        val idades = tasks.associate { it.key to Fluxo.idadeDias(it, map, cal, unit, agora) }
        val daLinha = tasks.associateWith { valor(linhas, it, idades) }
        val daColuna = tasks.associateWith { valor(colunas, it, idades) }
        val rotulosL = ordenar(linhas, daLinha.values.distinct())
        val rotulosC = ordenar(colunas, daColuna.values.distinct())

        fun natural(l: List<IssueSnapshot>) = l.sortedWith(compareBy(ORDEM_NATURAL_DE_CHAVES) { it.key })
        val segmentos = LinkedHashMap<String, List<IssueSnapshot>>()
        val celulas = rotulosL.mapIndexed { i, rl ->
            rotulosC.mapIndexed { j, rc ->
                val dela = natural(tasks.filter { daLinha.getValue(it) == rl && daColuna.getValue(it) == rc })
                segmentos["c:${i + 1}:${j + 1}"] = dela
                val ages = dela.map { idades.getValue(it.key) }
                val p50 = if (medida == Medida.IDADE_P50) Percentiles.median(ages) else null
                CelulaDto(dela.size, p50, medida == Medida.IDADE_P50 && dela.isNotEmpty() && dela.size < Fluxo.AMOSTRA_MINIMA)
            }
        }
        val totaisL = rotulosL.mapIndexed { i, rl ->
            natural(tasks.filter { daLinha.getValue(it) == rl }).also { if (linhas != Eixo.PESSOA) segmentos["l:${i + 1}"] = it }.size
        }
        val totaisC = rotulosC.mapIndexed { j, rc -> natural(tasks.filter { daColuna.getValue(it) == rc }).also { segmentos["k:${j + 1}"] = it }.size }
        segmentos["total"] = natural(tasks)

        val dto = CruzamentosDto(
            linhasEixo = linhas.id, colunasEixo = colunas.id, medida = medida.id, linhas = rotulosL, colunas = rotulosC, celulas = celulas,
            totaisDasLinhas = if (linhas == Eixo.PESSOA) null else totaisL, totaisDasColunas = totaisC, total = tasks.size,
            eixosDeLinha = Eixo.entries.map { it.id }, eixosDeColuna = Eixo.entries.filterNot { it.soLinha }.map { it.id },
            unit = if (unit == TimeUnitMode.UTIL) "util" else "corrido", avisos = emptyList(),
        )
        return CruzamentosAnalise(dto, segmentos, idades)
    }

    private fun valor(eixo: Eixo, s: IssueSnapshot, idades: Map<String, Double>): String = when (eixo) {
        Eixo.SQUAD -> s.assignee?.let(squadDe) ?: SEM
        Eixo.CATEGORIA -> map.category(s.status).name
        Eixo.STATUS -> s.status
        Eixo.TIPO -> s.issueType
        Eixo.PRIORIDADE -> s.priority?.takeIf { it.isNotBlank() } ?: SEM
        Eixo.OS -> s.osVersion?.takeIf { it.isNotBlank() } ?: SEM
        Eixo.IDADE -> AbertasCalculator.faixaDeIdade(idades.getValue(s.key))
        Eixo.VENCIMENTO -> s.duedate?.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))?.toString() ?: SEM
        Eixo.ATRASO -> if (s.labels.any { labels.justifica(it) || labels.ehTardia(it) }) "com" else "sem"
        Eixo.PESSOA -> s.assignee ?: SEM
    }

    /** A ordem do próprio eixo; `(sem)` sempre por último. Nunca por contagem. */
    private fun ordenar(eixo: Eixo, valores: List<String>): List<String> {
        val (sem, com) = valores.partition { it == SEM }
        val ordenados = when (eixo) {
            Eixo.CATEGORIA -> com.sortedBy { StatusCategory.valueOf(it).ordinal }
            Eixo.STATUS -> com.sortedWith(compareBy<String> { map.indiceNaOrdem(it) ?: Int.MAX_VALUE }.thenBy { it })
            Eixo.PRIORIDADE -> com.sortedWith(compareBy<String> { ORDEM_DE_PRIORIDADE.indexOf(it).takeIf { i -> i >= 0 } ?: Int.MAX_VALUE }.thenBy { it })
            Eixo.IDADE -> com.sortedBy { AbertasCalculator.FAIXAS_DE_IDADE.indexOf(it) }
            Eixo.ATRASO -> com.sortedBy { ORDEM_DE_ATRASO.indexOf(it) }
            Eixo.VENCIMENTO -> com.sorted()
            Eixo.SQUAD, Eixo.TIPO, Eixo.OS, Eixo.PESSOA -> com.sortedWith(compareBy<String> { it.lowercase(Locale.ROOT) }.thenBy { it })
        }
        return ordenados + sem
    }

    companion object {
        /** O valor de eixo "sem esse dado" (sem responsável, prioridade, OS, vencimento ou squad). */
        const val SEM = AbertasCalculator.SEM_PRIORIDADE

        /**
         * Ponto de extensão: eixos de campos do dicionário do Jira (Checklist Type, SKU Type...) que o Plano 10 (campos do
         * dicionário nas telas) vai oferecer. Hoje NÃO são eixos: pedi-los é 400 e eles não aparecem na lista oferecida.
         */
        val DICIONARIO_PENDENTE = listOf("checklistType", "skuType")

        private val ORDEM_DE_PRIORIDADE = listOf("Highest", "High", "Medium", "Low", "Lowest")
        private val ORDEM_DE_ATRASO = listOf("com", "sem")
    }
}
