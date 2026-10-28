package br.sidia.painel.integridade

import br.sidia.painel.auditoria.Vencimentos
import br.sidia.painel.cards.AvisoDto
import br.sidia.painel.domain.StatusCategory
import br.sidia.painel.sync.ChangeEventRecord
import br.sidia.painel.trabalho.IssueSnapshot
import java.time.Duration
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

/** Código do aviso de um sinal ligado que não tem dado para rodar (hoje, só a estimativa). */
const val AVISO_SINAL_SEM_DADO = "SINAL_SEM_DADO"

/**
 * Sinais de PRAZO, FLUXO, ESTIMATIVA e CRITICIDADE da Integridade: resgate por prorrogação, vencimento prorrogado, vencimento
 * definido tarde, pulou o fluxo, estimativa aumentada e label de criticidade removida perto do fim. A pessoa do sinal é o autor
 * do evento do changelog que o dispara. Cada evidência é um INDÍCIO, nunca uma prova.
 */
object DetectoresDePrazoEFluxo {
    private const val CAMPO_ESTIMATIVA = "timeoriginalestimate"
    private val SEPARADOR_DE_LABELS = Regex("\\s+")

    private class Prorrogacao(val e: ChangeEventRecord, val de: LocalDate, val para: LocalDate)

    /** Eventos de vencimento com data de origem e de destino legíveis em que o novo prazo é POSTERIOR ao antigo. */
    private fun prorrogacoes(s: IssueSnapshot): List<Prorrogacao> =
        s.events.filter { it.field.equals("duedate", ignoreCase = true) }.mapNotNull { e ->
            val de = Vencimentos.ler(e.from) ?: return@mapNotNull null
            val para = Vencimentos.ler(e.to) ?: return@mapNotNull null
            if (para.isAfter(de)) Prorrogacao(e, de, para) else null
        }

    /**
     * A prorrogação é um RESGATE quando foi feita DEPOIS de o prazo antigo vencer e a task foi fechada depois do prazo antigo mas
     * até o novo (a entrega parece no prazo só por causa da prorrogação). O dia é o de Manaus.
     */
    private fun ehResgate(p: Prorrogacao, fechadaEm: LocalDate?, ctx: ContextoIntegridade): Boolean {
        if (fechadaEm == null) return false
        val diaDaProrrogacao = p.e.at.atZone(ctx.zone).toLocalDate()
        return diaDaProrrogacao.isAfter(p.de) && fechadaEm.isAfter(p.de) && !fechadaEm.isAfter(p.para)
    }

    private val resgateEProrrogacao = DetectorIntegridade { issues, regras, ctx ->
        issues.flatMap { s ->
            val fechadaEm = Fechamentos.de(s, ctx.map)?.at?.atZone(ctx.zone)?.toLocalDate()
            prorrogacoes(s).mapNotNull { p ->
                val pessoa = Fechamentos.pessoa(p.e.authorRef, s, p.e.at)
                if (ehResgate(p, fechadaEm, ctx)) {
                    regras.evidencia(
                        SinalIntegridade.RESGATE_POR_PRORROGACAO, s, p.e.at, pessoa,
                        mapOf("de" to p.de.toString(), "para" to p.para.toString(), "fechadaEm" to fechadaEm.toString()),
                    )
                } else {
                    // Uma prorrogação não soma resgate e prorrogação no mesmo evento.
                    val dias = java.time.temporal.ChronoUnit.DAYS.between(p.e.at.atZone(ctx.zone).toLocalDate(), p.de)
                    regras.evidencia(
                        SinalIntegridade.VENCIMENTO_PRORROGADO, s, p.e.at, pessoa,
                        mapOf("de" to p.de.toString(), "para" to p.para.toString(), "diasAntesDoVencimento" to dias.toString()),
                    )
                }
            }
        }
    }

    /**
     * O PRIMEIRO vencimento (de nada para uma data) definido depois de `fracaoVidaTardia` da vida da task (criação até a
     * resolução). Só vale para task concluída; mudanças posteriores de prazo são prorrogação, não "definição tardia".
     */
    private val vencimentoTardio = DetectorIntegridade { issues, regras, ctx ->
        issues.mapNotNull { s ->
            Fechamentos.de(s, ctx.map) ?: return@mapNotNull null
            val fim = s.resolved ?: return@mapNotNull null
            val vida = Duration.between(s.created, fim)
            if (vida.isZero || vida.isNegative) return@mapNotNull null
            val primeiro = s.events.filter { it.field.equals("duedate", ignoreCase = true) }.minByOrNull { it.at } ?: return@mapNotNull null
            if (!primeiro.from.isNullOrBlank() || Vencimentos.ler(primeiro.to) == null) return@mapNotNull null
            val fracao = Duration.between(s.created, primeiro.at).toMillis().toDouble() / vida.toMillis().toDouble()
            if (fracao <= regras.fracaoVidaTardia) return@mapNotNull null
            regras.evidencia(
                SinalIntegridade.VENCIMENTO_TARDIO, s, primeiro.at, Fechamentos.pessoa(primeiro.authorRef, s, primeiro.at),
                mapOf("pctDaVida" to (fracao * 100).roundToInt().toString()),
            )
        }
    }

    /**
     * Chegou a um status concluído sem NUNCA ter passado por um status de categoria ativa (nem ter nascido em um). Espera e
     * bloqueio não substituem trabalho. A ordem do fluxo (`status_map.ordem`) não muda o resultado: a categoria basta.
     */
    private val puloOFluxo = DetectorIntegridade { issues, regras, ctx ->
        issues.mapNotNull { s ->
            val f = Fechamentos.de(s, ctx.map) ?: return@mapNotNull null
            val visitados = listOf(s.timeline.initialStatus) + s.timeline.transitions.map { it.to }
            if (visitados.any { ctx.map.category(it) == StatusCategory.ACTIVE }) return@mapNotNull null
            regras.evidencia(SinalIntegridade.PULOU_O_FLUXO, s, f.at, Fechamentos.pessoa(f.autor, s, f.at))
        }
    }

    private fun numero(v: String?): Double? = v?.trim()?.toDoubleOrNull()?.takeIf { it.isFinite() }

    /**
     * Estimativa original aumentada em `estimativaAumento` ou mais (inclusive). O changelog hoje só guarda o NOME do campo da
     * estimativa; sem eventos com valor legível não há evidência (e o aviso `SINAL_SEM_DADO` explica por quê).
     */
    private val estimativaAumentada = DetectorIntegridade { issues, regras, _ ->
        issues.flatMap { s ->
            s.events.filter { it.field.equals(CAMPO_ESTIMATIVA, ignoreCase = true) }.mapNotNull { e ->
                val antes = numero(e.from) ?: return@mapNotNull null
                val depois = numero(e.to) ?: return@mapNotNull null
                if (antes <= 0.0 || depois < antes * (1.0 + regras.estimativaAumento)) return@mapNotNull null
                regras.evidencia(
                    SinalIntegridade.ESTIMATIVA_AUMENTADA, s, e.at, Fechamentos.pessoa(e.authorRef, s, e.at),
                    mapOf("aumentoPct" to ((depois / antes - 1.0) * 100).roundToInt().toString()),
                )
            }
        }
    }

    /** Label de criticidade removida até `labelRemovidaDias` dias (inclusive) ANTES do fechamento, sem diferenciar maiúsculas. */
    private val criticidadeRemovida = DetectorIntegridade { issues, regras, ctx ->
        val criticas = regras.labelsCriticidade.map { it.trim().lowercase(Locale.ROOT) }.toSet()
        val janela = Duration.ofDays(regras.labelRemovidaDias.toLong())
        issues.flatMap { s ->
            val f = Fechamentos.de(s, ctx.map) ?: return@flatMap emptyList()
            s.events.filter { it.field.equals("labels", ignoreCase = true) }.mapNotNull { e ->
                if (e.at.isAfter(f.at) || Duration.between(e.at, f.at) > janela) return@mapNotNull null
                val antes = separar(e.from)
                val depois = separar(e.to).map { it.lowercase(Locale.ROOT) }.toSet()
                val removidas = antes.filter { it.lowercase(Locale.ROOT) in criticas && it.lowercase(Locale.ROOT) !in depois }
                if (removidas.isEmpty()) return@mapNotNull null
                regras.evidencia(
                    SinalIntegridade.LABEL_CRITICIDADE_REMOVIDA, s, e.at, Fechamentos.pessoa(e.authorRef, s, e.at),
                    mapOf("label" to removidas.joinToString(" "), "diasAntesDeFechar" to Duration.between(e.at, f.at).toDays().toString()),
                )
            }
        }
    }

    private fun separar(texto: String?): List<String> = texto?.split(SEPARADOR_DE_LABELS)?.filter { it.isNotEmpty() }.orEmpty()

    val todos: List<DetectorIntegridade> = listOf(resgateEProrrogacao, vencimentoTardio, puloOFluxo, estimativaAumentada, criticidadeRemovida)

    /** Algum evento de estimativa traz valor legível (de ou para)? Hoje o changelog guarda só o nome do campo. */
    fun estimativaTemDado(issues: List<IssueSnapshot>): Boolean =
        issues.any { s -> s.events.any { it.field.equals(CAMPO_ESTIMATIVA, ignoreCase = true) && (numero(it.from) != null || numero(it.to) != null) } }

    /**
     * Avisos de dado que falta: `SINAL_SEM_DADO` (informativo) quando `ESTIMATIVA_AUMENTADA` está ativo e nenhum evento de
     * estimativa tem valor. Sinal desligado não avisa (o catálogo já o mostra como inativo).
     */
    fun avisosDeDados(issues: List<IssueSnapshot>, regras: RegrasDeIntegridade): List<AvisoDto> =
        if (regras.ativo(SinalIntegridade.ESTIMATIVA_AUMENTADA) && !estimativaTemDado(issues)) {
            listOf(
                AvisoDto(
                    AVISO_SINAL_SEM_DADO, AvisoDto.INFORMATIVO, "o changelog não guarda o valor da estimativa",
                    mapOf("sinal" to SinalIntegridade.ESTIMATIVA_AUMENTADA.name),
                ),
            )
        } else {
            emptyList()
        }
}
