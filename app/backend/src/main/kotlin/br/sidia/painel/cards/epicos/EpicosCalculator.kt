package br.sidia.painel.cards.epicos

import br.sidia.painel.cards.AvisoDto
import br.sidia.painel.domain.ORDEM_NATURAL_DE_CHAVES
import br.sidia.painel.domain.StatusCategory
import br.sidia.painel.domain.StatusMap
import br.sidia.painel.previsao.RegrasDePrevisao
import br.sidia.painel.time.BusinessCalendar
import br.sidia.painel.trabalho.Fluxo
import br.sidia.painel.trabalho.IssueSnapshot
import br.sidia.painel.trabalho.MonteCarlo
import br.sidia.painel.trabalho.SerieSemanal
import java.security.MessageDigest
import java.time.Instant
import java.util.Locale
import kotlin.random.Random

class EpicosAnalise(val dto: EpicosDto, val segmentos: Map<String, List<IssueSnapshot>>)

/**
 * Card Épicos (EP-01). Itens = tasks com `epicKey` (sem os tipos de review e sem as canceladas). A previsão do épico usa
 * SÓ a vazão do próprio épico (semanas fechadas, da primeira entrega dele até a última semana fechada) e só aparece com
 * pelo menos [RegrasDePrevisao.minItensEpico] entregas dele no histórico; sem amostra própria é "—". Sem taxa de chegada:
 * o escopo do épico muda por decisão de gestão, não por fluxo.
 */
class EpicosCalculator(
    private val cal: BusinessCalendar,
    private val map: StatusMap,
    private val regras: RegrasDePrevisao,
    tiposReview: Set<String>,
) {
    private val review = tiposReview.map { it.trim().lowercase(Locale.ROOT) }.toSet()

    companion object {
        /** Código curto e estável do épico para os segmentos do drill (a chave do épico não vai para o `access_log`). */
        fun idDoEpico(chave: String): String =
            MessageDigest.getInstance("SHA-256").digest(chave.toByteArray(Charsets.UTF_8)).take(6).joinToString("") { "%02x".format(it) }
    }

    /** [todas]: as tasks do escopo, abertas e resolvidas de qualquer data. [semente]: `sementeDoDia(...)`. */
    fun analisar(todas: List<IssueSnapshot>, agora: Instant, semente: Long): EpicosAnalise {
        val hoje = cal.localDate(agora)
        val avisos = mutableListOf<AvisoDto>()
        val comEpico = todas.filter { !it.epicKey.isNullOrBlank() }
        if (comEpico.isEmpty()) {
            avisos += AvisoDto(AvisoDto.EPICO_INDISPONIVEL, AvisoDto.INFORMATIVO, "nenhuma task com épico (campo Epic Link não sincronizado ou sem uso)")
        }
        val itens = comEpico.filter { it.issueType.trim().lowercase(Locale.ROOT) !in review && !Fluxo.cancelada(it, map) }
        val ultima = SerieSemanal.ultimaSemanaFechada(hoje)
        val segmentos = LinkedHashMap<String, List<IssueSnapshot>>()
        fun ordena(l: List<IssueSnapshot>) = l.sortedWith(compareBy(ORDEM_NATURAL_DE_CHAVES) { it.key })

        val linhas = itens.groupBy { requireNotNull(it.epicKey) }.entries
            .sortedWith(compareBy(ORDEM_NATURAL_DE_CHAVES) { it.key })
            .map { (epico, lista) ->
                val id = idDoEpico(epico)
                val concluidos = lista.filter { Fluxo.concluidaPositiva(it, map) }
                val abertos = lista.filter { it.resolved == null && map.category(it.status) != StatusCategory.DONE }
                val vencidos = abertos.filter { it.duedate?.isBefore(hoje) == true }
                segmentos["epico:$id:itens"] = ordena(lista)
                segmentos["epico:$id:concluidos"] = ordena(concluidos)
                segmentos["epico:$id:vencidos"] = ordena(vencidos)
                val comPontos = lista.mapNotNull { it.storyPoints }
                LinhaEpicoDto(
                    id = id,
                    epico = epico,
                    itens = lista.size,
                    concluidos = concluidos.size,
                    progresso = if (lista.isEmpty()) null else Math.round(concluidos.size.toDouble() / lista.size * 1e6) / 1e6,
                    maisAntigo = abertos.minByOrNull { it.created }?.let { cal.localDate(it.created).toString() },
                    vencidos = vencidos.size,
                    pontos = comPontos.takeIf { it.isNotEmpty() }?.sum(),
                    previsaoSemanasP85 = previsaoP85(epico, lista, abertos.size, ultima, semente),
                )
            }
        return EpicosAnalise(EpicosDto(linhas, regras.minItensEpico, avisos), segmentos)
    }

    /** P85 em semanas, só com a vazão do próprio épico e ao menos `minItensEpico` entregas dele na janela; senão nulo. */
    private fun previsaoP85(epico: String, itens: List<IssueSnapshot>, abertos: Int, ultima: java.time.LocalDate, semente: Long): Int? {
        val serie = SerieSemanal.calcular(itens, map, ultima, regras.semanasHistorico, cal.zone)
        val vazao = serie.map { it.saidas.size }
        if (vazao.sum() < regras.minItensEpico) return null
        val primeira = vazao.indexOfFirst { it > 0 }
        val usada = vazao.drop(primeira)
        val sementeDoEpico = semente xor epico.hashCode().toLong()
        return MonteCarlo(Random(sementeDoEpico)).quando(usada, null, abertos, regras.sorteios, regras.limiteSemanas).p85
    }
}
