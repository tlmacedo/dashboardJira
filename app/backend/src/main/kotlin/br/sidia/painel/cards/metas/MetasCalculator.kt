package br.sidia.painel.cards.metas

import br.sidia.painel.cards.AvisoDto
import br.sidia.painel.cards.abertas.ContagemDto
import br.sidia.painel.domain.FaixaDePrazo
import br.sidia.painel.domain.LabelsDeAtraso
import br.sidia.painel.domain.NoPrazo
import br.sidia.painel.domain.ORDEM_NATURAL_DE_CHAVES
import br.sidia.painel.domain.StatusMap
import br.sidia.painel.domain.classificar
import br.sidia.painel.security.RoleDirectory
import br.sidia.painel.time.BusinessCalendar
import br.sidia.painel.trabalho.Fluxo
import br.sidia.painel.trabalho.IssueSnapshot
import br.sidia.painel.trabalho.toAuditIssue
import br.sidia.painel.trabalho.vencimentoOriginal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** O DTO e, por segmento, as issues que formam cada número (a gaveta abre exatamente estas). */
class MetasAnalise(
    val dto: MetasDto,
    val segmentos: Map<String, List<IssueSnapshot>>,
    /** Δ (dia de Manaus da resolução − vencimento) de cada task do universo, por chave. */
    val deltas: Map<String, Int>,
    val diaDaResolucao: Map<String, LocalDate>,
)

/**
 * Card Metas e prazos. O universo, "no prazo" e "no prazo ajustado" vêm de [NoPrazo] (a regra única); este
 * calculador nunca faz contagem própria dessas três. Tabelas por pessoa e squad em ordem alfabética, nunca por taxa.
 */
class MetasCalculator(
    private val cal: BusinessCalendar,
    private val map: StatusMap,
    private val labels: LabelsDeAtraso,
    private val regras: RegrasDeMetas,
    private val roles: RoleDirectory,
) {
    companion object {
        const val SEM_PRIORIDADE = "(sem)"
        const val SEM_SQUAD = "(sem squad)"
        private val ORDEM_DE_PRIORIDADE = listOf("Highest", "High", "Medium", "Low", "Lowest")

        /** `ok` (✓) com a taxa na meta ou acima, `atencao` (●) até a faixa abaixo dela, `problema` (▲) além disso. */
        fun estadoDaTaxa(taxa: Double?, regras: RegrasDeMetas): String = when {
            taxa == null -> "ok"
            taxa >= regras.metaNoPrazo - 1e-9 -> "ok"
            taxa >= regras.metaNoPrazo - regras.faixaAtencaoPp / 100.0 - 1e-9 -> "atencao"
            else -> "problema"
        }
    }

    fun calcular(
        candidatasNoPrazo: List<IssueSnapshot>, resolvidasNoPeriodo: List<IssueSnapshot>, de: LocalDate, ate: LocalDate,
        g: Granularidade, visiveis: Set<String>,
    ): MetasDto = analisar(candidatasNoPrazo, resolvidasNoPeriodo, de, ate, g, visiveis).dto

    @Suppress("LongMethod")
    fun analisar(
        candidatasNoPrazo: List<IssueSnapshot>, resolvidasNoPeriodo: List<IssueSnapshot>, de: LocalDate, ate: LocalDate,
        g: Granularidade, visiveis: Set<String>,
    ): MetasAnalise {
        val zona = cal.zone
        // `NoPrazo.noUniverso` ignora em silêncio o status fora do mapa (a Auditoria o relata à parte); aqui ele vira o
        // aviso bloqueante do card (STATUS_SEM_MAPEAMENTO), como em Fechadas, em vez de números que não somam.
        (candidatasNoPrazo + resolvidasNoPeriodo).forEach { map.category(it.status) }
        val porChave = candidatasNoPrazo.associateBy { it.key }
        val prazo = NoPrazo.detalhar(candidatasNoPrazo.map { it.toAuditIssue() }, labels, map, zona, de, ate)
        fun snaps(l: List<br.sidia.painel.auditoria.AuditIssue>) = l.mapNotNull { porChave[it.key] }
        val universo = snaps(prazo.concluidas)
        val noPrazo = snaps(prazo.noPrazo)
        val ajustadas = snaps(prazo.noPrazoAjustado)

        val dia = universo.associate { it.key to cal.localDate(requireNotNull(it.resolved)) }
        val classes = universo.associate { s -> s.key to NoPrazo.classificar(dia.getValue(s.key), requireNotNull(s.duedate), s.labels, labels) }
        val deltas = classes.mapValues { it.value.delta }
        val originais = universo.filter { s ->
            NoPrazo.classificar(dia.getValue(s.key), s.vencimentoOriginalOuAtual(), s.labels, labels).noPrazo
        }
        val antecipadas = universo.filter { classes.getValue(it.key).delta <= -1 }
        val emD1D2 = universo.filter { classes.getValue(it.key).faixa in setOf(FaixaDePrazo.D1, FaixaDePrazo.D2) }
        val noDia = universo.filter { classes.getValue(it.key).faixa == FaixaDePrazo.D }
        val atrasadas = universo.filter { classes.getValue(it.key).delta > 0 }
        val semVencimento = resolvidasNoPeriodo.filter { s ->
            s.duedate == null && Fluxo.concluidaPositiva(s, map) && !s.issueType.trim().equals("Epic", ignoreCase = true)
        }

        fun taxa(n: Int): Double? = if (universo.isEmpty()) null else n.toDouble() / universo.size
        val taxaNoPrazo = taxa(noPrazo.size)
        val taxaOriginal = taxa(originais.size)

        // Séries pelo VENCIMENTO (o mesmo critério do universo): cada compromisso fica no período em que vencia.
        fun chaveDoPeriodo(s: IssueSnapshot): String {
            val d = requireNotNull(s.duedate)
            return when (g) {
                Granularidade.DIA -> d.toString()
                Granularidade.SEMANA -> d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString()
                Granularidade.MES -> "%04d-%02d".format(d.year, d.monthValue)
            }
        }
        val porPeriodoMapa = universo.groupBy(::chaveDoPeriodo).toSortedMap()
        val chavesNoPrazo = noPrazo.map { it.key }.toSet()
        val chavesAjustadas = ajustadas.map { it.key }.toSet()

        val segmentos = LinkedHashMap<String, List<IssueSnapshot>>()
        segmentos["universo"] = universo
        segmentos["noPrazo"] = noPrazo
        segmentos["noPrazoAjustado"] = ajustadas
        segmentos["noPrazoOriginal"] = originais
        segmentos["antecipadas"] = antecipadas
        segmentos["emD1D2"] = emD1D2
        segmentos["noDia"] = noDia
        segmentos["atrasadas"] = atrasadas
        segmentos["semVencimento"] = semVencimento
        for (f in FaixaDePrazo.entries) segmentos["faixa:${f.name}"] = universo.filter { classes.getValue(it.key).faixa == f }
        for ((p, l) in porPeriodoMapa) {
            segmentos["periodo:$p"] = l
            segmentos["periodo:$p:noPrazo"] = l.filter { it.key in chavesNoPrazo }
        }

        fun linhas(prefixo: String, grupos: Map<String, List<IssueSnapshot>>): List<LinhaMetaDto> =
            grupos.map { (chave, l) ->
                segmentos["$prefixo:$chave"] = l
                val np = l.count { it.key in chavesNoPrazo }
                LinhaMetaDto(chave, l.size, np, l.count { it.key in chavesAjustadas }, estadoDaTaxa(np.toDouble() / l.size, regras))
            }
        val porTipo = linhas("tipo", universo.groupBy { it.issueType }.toSortedMap())
        val porPrioridade = linhas(
            "prioridade",
            universo.groupBy { it.priority ?: SEM_PRIORIDADE }.toSortedMap(
                compareBy({ ORDEM_DE_PRIORIDADE.indexOf(it).let { i -> if (i < 0) Int.MAX_VALUE else i } }, { it }),
            ),
        )
        // Pessoa e squad pela posse NO DIA DA RESOLUÇÃO; só pessoas da visão de quem consulta.
        val responsavelNaResolucao = universo.associate { s -> s.key to Fluxo.responsavelEm(s, requireNotNull(s.resolved)) }
        val porSquad = linhas(
            "squad",
            universo.groupBy { s -> responsavelNaResolucao.getValue(s.key)?.let { roles.squadOf(it, dia.getValue(s.key)) } ?: SEM_SQUAD }.toSortedMap(),
        )
        val porPessoa = linhas(
            "pessoa",
            universo.filter { responsavelNaResolucao.getValue(it.key)?.let { p -> p in visiveis } == true }
                .groupBy { requireNotNull(responsavelNaResolucao.getValue(it.key)) }.toSortedMap(),
        )

        val ordenados = segmentos.mapValues { (_, l) -> l.sortedWith(Comparator { a, b -> ORDEM_NATURAL_DE_CHAVES.compare(a.key, b.key) }) }

        val dto = MetasDto(
            versaoRegras = "metas v${regras.versao}, labels v${labels.versao}",
            universo = universo.size,
            noPrazo = noPrazo.size,
            noPrazoAjustado = ajustadas.size,
            noPrazoOriginal = originais.size,
            antecipadas = antecipadas.size,
            emD1D2 = emD1D2.size,
            noDia = noDia.size,
            atrasadas = atrasadas.size,
            semVencimento = semVencimento.size,
            taxaNoPrazo = taxaNoPrazo,
            taxaAjustada = taxa(ajustadas.size),
            taxaOriginal = taxaOriginal,
            taxaAntecipadas = taxa(antecipadas.size),
            alertaDataOriginal = taxaNoPrazo != null && taxaOriginal != null && taxaNoPrazo - taxaOriginal >= regras.alertaDataOriginalPp / 100.0 - 1e-9,
            metaNoPrazo = regras.metaNoPrazo,
            metaAntecipadas = regras.metaAntecipadas,
            granularidade = g.name.lowercase(),
            porPeriodo = porPeriodoMapa.map { (p, l) ->
                val np = l.count { it.key in chavesNoPrazo }
                PeriodoMetaDto(
                    chave = p, universo = l.size, noPrazo = np, noPrazoAjustado = l.count { it.key in chavesAjustadas },
                    taxaNoPrazo = np.toDouble() / l.size, antecipadas = l.count { classes.getValue(it.key).delta <= -1 },
                    noDia = l.count { classes.getValue(it.key).faixa == FaixaDePrazo.D }, atrasadas = l.count { classes.getValue(it.key).delta > 0 },
                )
            },
            distribuicao = FaixaDePrazo.entries.map { ContagemDto(it.name, ordenados.getValue("faixa:${it.name}").size) },
            porTipo = porTipo,
            porPrioridade = porPrioridade,
            porSquad = porSquad,
            porPessoa = porPessoa,
            avisos = emptyList<AvisoDto>(),
        )
        return MetasAnalise(dto, ordenados, deltas, dia)
    }

    /** O vencimento original (1º valor do changelog) ou, sem ele, o atual. */
    private fun IssueSnapshot.vencimentoOriginalOuAtual(): LocalDate =
        vencimentoOriginal() ?: requireNotNull(duedate)
}
