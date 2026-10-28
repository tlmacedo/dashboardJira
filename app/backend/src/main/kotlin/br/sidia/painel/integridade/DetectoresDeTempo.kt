package br.sidia.painel.integridade

import br.sidia.painel.auditoria.AuditTimeline
import br.sidia.painel.domain.ORDEM_NATURAL_DE_CHAVES
import br.sidia.painel.domain.StatusCategory
import br.sidia.painel.trabalho.Fluxo
import br.sidia.painel.trabalho.IssueSnapshot
import br.sidia.painel.trabalho.toAuditIssue
import java.time.Duration
import java.time.Instant

/**
 * Sinais de TEMPO da Integridade: troca de responsável perto do fechamento, criada e fechada em minutos, reabre e fecha, fechamento
 * em lote, fechada por outro e fechada no limite do prazo. Todos partem de uma task concluída de verdade (categoria concluída,
 * resolução positiva e data): cancelamento não é fechamento. Funções puras; cada evidência é um INDÍCIO, nunca uma prova.
 */
object DetectoresDeTempo {
    private fun horas(h: Double): Duration = Duration.ofMillis((h * MILIS_POR_HORA).toLong())

    /**
     * Alguém assumiu a task pouco antes de fechá-la e foi quem a fechou (o app anterior chamava de "apropriação"): troca de um
     * responsável para outro, até `trocaAntesDeFecharHoras` (inclusive) antes do fechamento, com o novo responsável sendo o autor
     * do fechamento. A pessoa é o autor da troca; a evidência é a troca mais próxima do fechamento.
     */
    private val trocaAntesDeFechar = DetectorIntegridade { issues, regras, ctx ->
        val janela = horas(regras.trocaAntesDeFecharHoras)
        issues.mapNotNull { s ->
            val f = Fechamentos.de(s, ctx.map) ?: return@mapNotNull null
            val quemFechou = Fechamentos.autor(f.autor) ?: return@mapNotNull null
            val troca = s.events
                .filter { it.field.equals("assignee", ignoreCase = true) && !it.from.isNullOrBlank() && !it.to.isNullOrBlank() && it.from != it.to }
                .filter { !it.at.isAfter(f.at) && Duration.between(it.at, f.at) <= janela && it.to.equals(quemFechou, ignoreCase = true) }
                .maxByOrNull { it.at } ?: return@mapNotNull null
            regras.evidencia(
                SinalIntegridade.TROCA_ANTES_DE_FECHAR, s, troca.at, Fechamentos.pessoa(troca.authorRef, s, troca.at),
                mapOf("minutosAntesDeFechar" to Duration.between(troca.at, f.at).toMinutes().toString()),
            )
        }
    }

    /** Criada e fechada em menos de `criadaEFechadaMinutos` (estrito). A pessoa é o autor do fechamento. */
    private val criadaEFechadaRapido = DetectorIntegridade { issues, regras, ctx ->
        val limite = Duration.ofMinutes(regras.criadaEFechadaMinutos.toLong())
        issues.mapNotNull { s ->
            val f = Fechamentos.de(s, ctx.map) ?: return@mapNotNull null
            val vida = Duration.between(s.created, f.at)
            if (vida.isNegative || vida >= limite) return@mapNotNull null
            regras.evidencia(
                SinalIntegridade.CRIADA_E_FECHADA_RAPIDO, s, f.at, Fechamentos.pessoa(f.autor, s, f.at),
                mapOf("minutos" to vida.toMinutes().toString()),
            )
        }
    }

    /**
     * Fechada, reaberta e fechada de novo, com cada passo em menos de `reabreEFechaHoras` (o fechamento anterior a menos de
     * `reabreEFechaHoras` da reabertura e o novo fechamento também): a data de resolução é renovada. Uma devolução tardia
     * (reaberta dias depois) não conta. A pessoa é o autor do segundo fechamento (senão, o da reabertura).
     */
    private val reabreEFechaRapido = DetectorIntegridade { issues, regras, ctx ->
        val limite = horas(regras.reabreEFechaHoras)
        issues.mapNotNull { s ->
            if (Fechamentos.de(s, ctx.map) == null) return@mapNotNull null
            val ordenadas = s.timeline.transitions.sortedBy { it.at }
            ordenadas.firstNotNullOfOrNull { reabertura ->
                val reaberta = reabertura.from != null &&
                    ctx.map.category(reabertura.from) == StatusCategory.DONE && ctx.map.category(reabertura.to) != StatusCategory.DONE
                if (!reaberta) return@firstNotNullOfOrNull null
                val fechouAntes = ordenadas.filter { it.at.isBefore(reabertura.at) && ctx.map.category(it.to) == StatusCategory.DONE }.maxByOrNull { it.at }
                val fechouDeNovo = ordenadas.firstOrNull { it.at.isAfter(reabertura.at) && ctx.map.category(it.to) == StatusCategory.DONE }
                if (fechouAntes == null || fechouDeNovo == null) return@firstNotNullOfOrNull null
                val reabriuLogo = Duration.between(fechouAntes.at, reabertura.at) < limite
                val fechouLogo = Duration.between(reabertura.at, fechouDeNovo.at) < limite
                if (!reabriuLogo || !fechouLogo) return@firstNotNullOfOrNull null
                regras.evidencia(
                    SinalIntegridade.REABRE_E_FECHA_RAPIDO, s, fechouDeNovo.at,
                    Fechamentos.pessoa(fechouDeNovo.authorRef ?: reabertura.authorRef, s, fechouDeNovo.at),
                    mapOf("minutosParaRefechar" to Duration.between(reabertura.at, fechouDeNovo.at).toMinutes().toString()),
                )
            }
        }
    }

    /**
     * Fechamento em lote: `loteQuantidade` tasks ou mais fechadas pela mesma pessoa (autor do fechamento) numa janela de até
     * `loteMinutos` contada a partir do primeiro fechamento (inclusive). A janela é de tempo, então atravessar a meia-noite de
     * Manaus não a quebra. Uma evidência por task do lote.
     */
    private val fechamentoEmLote = DetectorIntegridade { issues, regras, ctx ->
        val janela = Duration.ofMinutes(regras.loteMinutos.toLong())
        class Item(val s: IssueSnapshot, val at: Instant)
        val porPessoa = HashMap<String, MutableList<Item>>()
        for (s in issues) {
            val f = Fechamentos.de(s, ctx.map) ?: continue
            val pessoa = Fechamentos.pessoa(f.autor, s, f.at) ?: continue
            porPessoa.getOrPut(pessoa) { mutableListOf() } += Item(s, f.at)
        }
        val saida = mutableListOf<EvidenciaIntegridade?>()
        for ((pessoa, lista) in porPessoa) {
            val itens = lista.sortedWith(compareBy<Item> { it.at }.thenBy(ORDEM_NATURAL_DE_CHAVES) { it.s.key })
            var i = 0
            while (i < itens.size) {
                var j = i
                while (j + 1 < itens.size && Duration.between(itens[i].at, itens[j + 1].at) <= janela) j++
                val tamanho = j - i + 1
                if (tamanho >= regras.loteQuantidade) {
                    val minutos = Duration.between(itens[i].at, itens[j].at).toMinutes().toString()
                    for (k in i..j) {
                        saida += regras.evidencia(
                            SinalIntegridade.FECHAMENTO_EM_LOTE, itens[k].s, itens[k].at, pessoa,
                            mapOf("fechadas" to tamanho.toString(), "minutos" to minutos),
                        )
                    }
                    i = j + 1
                } else {
                    i++
                }
            }
        }
        saida.filterNotNull()
    }

    /** Fechada por quem não era o responsável no instante (o autor da transição para concluído difere dele). Sem autor ou sem responsável, nada a afirmar. */
    private val fechadaPorOutro = DetectorIntegridade { issues, regras, ctx ->
        issues.mapNotNull { s ->
            val f = Fechamentos.de(s, ctx.map) ?: return@mapNotNull null
            val autor = Fechamentos.autor(f.autor) ?: return@mapNotNull null
            val responsavel = Fluxo.responsavelEm(s, f.at) ?: return@mapNotNull null
            if (autor.equals(responsavel, ignoreCase = true)) return@mapNotNull null
            regras.evidencia(SinalIntegridade.FECHADA_POR_OUTRO, s, f.at, autor)
        }
    }

    /**
     * Fechada no dia do vencimento a partir de `limiteInicioHora` (padrão 22h), no fuso do painel (Manaus). O vencimento é o que
     * estava vigente quando a task foi fechada (uma prorrogação depois do fechamento não vale). Sinal fraco: só pesa em volume.
     */
    private val fechadaNoLimite = DetectorIntegridade { issues, regras, ctx ->
        issues.mapNotNull { s ->
            val f = Fechamentos.de(s, ctx.map) ?: return@mapNotNull null
            val vencimento = AuditTimeline(s.toAuditIssue(), ctx.map).vencimentoEm(f.at.plusMillis(1)) ?: return@mapNotNull null
            val local = f.at.atZone(ctx.zone)
            if (local.toLocalDate() != vencimento || local.hour < regras.limiteInicioHora) return@mapNotNull null
            regras.evidencia(
                SinalIntegridade.FECHADA_NO_LIMITE, s, f.at, Fechamentos.pessoa(f.autor, s, f.at),
                mapOf("hora" to local.hour.toString(), "vencimento" to vencimento.toString()),
            )
        }
    }

    val todos: List<DetectorIntegridade> = listOf(
        trocaAntesDeFechar, criadaEFechadaRapido, reabreEFechaRapido, fechamentoEmLote, fechadaPorOutro, fechadaNoLimite,
    )

    private const val MILIS_POR_HORA = 3_600_000.0
}
