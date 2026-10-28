package br.sidia.painel.governanca

import br.sidia.painel.cards.AvisoDto
import br.sidia.painel.cards.CardDeLeitura
import br.sidia.painel.cards.CartaoDeLeitura
import br.sidia.painel.cards.EntradaDeCard
import br.sidia.painel.cards.INSTANCIA_TRABALHO
import br.sidia.painel.cards.ResultadoDeCard
import br.sidia.painel.cards.contrato.CartaoRegistrado
import br.sidia.painel.cards.contrato.FonteDoDado
import br.sidia.painel.cards.contrato.GrupoDeCartao
import br.sidia.painel.cards.contrato.Natureza
import br.sidia.painel.cards.fluxo.CardBloqueios
import br.sidia.painel.cards.linhaDe
import br.sidia.painel.cards.metaDeTrabalho
import br.sidia.painel.domain.ORDEM_NATURAL_DE_CHAVES
import br.sidia.painel.domain.StatusCategory
import br.sidia.painel.domain.UnmappedStatusException
import br.sidia.painel.security.Role
import br.sidia.painel.sync.FiltroDeFicha
import br.sidia.painel.trabalho.ConsultaDeTrabalho
import br.sidia.painel.trabalho.IssueSnapshot
import br.sidia.painel.trabalho.Situacao
import java.time.Duration

/** Quem vê a Qualidade dos dados: coordenação e gerente (o item "sem estimativa" da Central abre esta tela). */
internal val PAPEIS_DA_QUALIDADE = setOf(Role.COORDENADOR, Role.GERENTE)

/** Janela, em dias, das verificações de conclusão divergente e de labels. */
private const val JANELA_DIAS = 90L

/**
 * Qualidade dos dados (QD-01 a QD-05): verificações informativas sobre o que o painel recebeu do Jira. O número de cada linha é
 * o das tasks que a gaveta abre. Status fora do mapa NÃO bloqueia esta tela (é uma das suas verificações): as issues com ele
 * ficam de fora das demais contas e aparecem em "Status sem mapeamento" com o nome do status. Somente leitura: o vocabulário
 * (labels, status, campos) é do gestor, nos YAML.
 */
object CardQualidade : CardDeLeitura<QualidadeDto> {
    override val id = "qualidade"
    override val periodoPadraoDias: Int? = null

    override fun calcular(e: EntradaDeCard): ResultadoDeCard<QualidadeDto> {
        val ctx = e.ctx
        val map = ctx.statusMap
        val ficha = FiltroDeFicha.de(ctx.fichas.getValue(id))
        val corte = e.agora.minus(Duration.ofDays(JANELA_DIAS))
        val abertasTodas = e.store.snapshots(ConsultaDeTrabalho(INSTANCIA_TRABALHO, ficha, Situacao.ABERTAS, osVersion = e.filtros.os))
        val resolvidasTodas = e.store.snapshots(ConsultaDeTrabalho(INSTANCIA_TRABALHO, ficha, Situacao.RESOLVIDAS, resolvidasDe = corte, osVersion = e.filtros.os))

        fun categoria(s: IssueSnapshot) = try {
            map.category(s.status)
        } catch (x: UnmappedStatusException) {
            null
        }

        fun daVisao(s: IssueSnapshot) = e.escopo.permiteAtual(s) && e.filtros.aceita(s)
        val abertas = abertasTodas.filter(::daVisao)
        // Recentes: as resolvidas na janela e as que ainda constam como abertas mas estão (ou ficaram) concluídas, com resolution
        // ou sem ela, mexidas na janela (o Jira pode concluir sem data de resolução).
        val concluidasSemData = abertas.filter { s ->
            (categoria(s) == StatusCategory.DONE || !s.resolution.isNullOrBlank()) && !(s.updated ?: s.created).isBefore(corte)
        }
        val recentes = (resolvidasTodas.filter(::daVisao) + concluidasSemData).distinctBy { it.key }

        // Campos "a confirmar": há valor em ALGUMA issue da base (não só da visão; é um fato do dado sincronizado).
        val todas = abertasTodas + resolvidasTodas
        val camposDisponiveis = linkedMapOf(
            "Story Points" to todas.any { it.storyPoints != null },
            "Epic Link" to todas.any { !it.epicKey.isNullOrBlank() },
            "Flagged" to todas.any { it.flagged },
            "OS Version" to todas.any { !it.osVersion.isNullOrBlank() },
        )

        // Bloqueadas sem causa: a conta e a gaveta são as do Bloqueios (BQ-04), então o número é o mesmo nas duas telas.
        val bloqueios = try {
            CardBloqueios.calcular(e)
        } catch (x: UnmappedStatusException) {
            null
        }
        val chavesSemCausa = bloqueios?.detalhar?.invoke("semCausa")?.map { it.key }?.toSet()
        val semCausa = chavesSemCausa?.let { chaves -> abertas.filter { it.key in chaves } }

        val analise = QualidadeDosDados.analisar(
            abertas, recentes, map, ctx.roles, e.hoje, ctx.regrasPlanejamento.prioridadePadrao, camposDisponiveis, ctx.labelsDeAtraso, semCausa,
        )
        val foraDoMapa = analise.tasks.getValue(VerificacaoQD.STATUS_SEM_MAPEAMENTO).size
        val avisos = buildList {
            if (foraDoMapa > 0) {
                add(AvisoDto(AvisoDto.ISSUES_IGNORADAS_STATUS, AvisoDto.INFORMATIVO, "issues com status fora do mapa ficaram fora das demais contas", mapOf("n" to foraDoMapa.toString())))
            }
            if (!e.store.temCamposDeTrabalho(INSTANCIA_TRABALHO)) {
                add(AvisoDto(AvisoDto.CAMPOS_DE_TRABALHO_INDISPONIVEIS, AvisoDto.INFORMATIVO, "a base foi sincronizada antes dos campos de trabalho"))
            }
        }
        val dto = QualidadeDto(
            versaoRegras = "qualidade v1", janelaDias = JANELA_DIAS.toInt(),
            linhas = analise.linhas.map { LinhaQDDto(it.verificacao.name, it.n, it.universo, it.fracao, it.estado, it.nomes) },
            labels = analise.tasksPorLabel.map { LabelSemClasseDto(it.key, it.value.size) },
            avisos = avisos,
        )

        fun linhas(v: VerificacaoQD, detalhe: String, tasks: List<IssueSnapshot>) =
            tasks.sortedWith { a, b -> ORDEM_NATURAL_DE_CHAVES.compare(a.key, b.key) }.map { linhaDe(it, e.escopo, detalhe) }
        val porSegmento = mapOf(
            VerificacaoQD.SEM_ESTIMATIVA to "Sem estimativa original",
            VerificacaoQD.SEM_RESPONSAVEL to "Sem responsável",
            VerificacaoQD.SEM_VENCIMENTO to "Sem vencimento",
            VerificacaoQD.SEM_EPICO to "Sem épico",
            VerificacaoQD.EM_ANDAMENTO_SEM_HORAS to "Em andamento sem horas registradas",
            VerificacaoQD.SEM_SQUAD to "Responsável fora do papeis.yaml hoje",
            VerificacaoQD.PRIORIDADE_PADRAO to "Ainda com a prioridade padrão",
            VerificacaoQD.DONE_SEM_RESOLUTION to "Status concluído sem resolution",
            VerificacaoQD.RESOLUTION_SEM_DONE to "Resolution preenchida sem status concluído",
            VerificacaoQD.STATUS_SEM_MAPEAMENTO to "Status fora do status_map.yaml",
            VerificacaoQD.LABEL_SEM_CLASSIFICACAO to "Tem label que não está em labels_atraso.yaml",
            VerificacaoQD.BLOQUEADA_SEM_CAUSA to "Bloqueada (status ou flag) sem nenhum vínculo aberto que explique",
        )
        return ResultadoDeCard(dto) { segmento ->
            when {
                segmento.startsWith("label:") -> analise.tasksPorLabel[segmento.removePrefix("label:")]?.let { linhas(VerificacaoQD.LABEL_SEM_CLASSIFICACAO, "Label sem classificação: ${segmento.removePrefix("label:")}", it) }
                else -> VerificacaoQD.entries.firstOrNull { it.name == segmento && it in porSegmento }?.let { v -> linhas(v, porSegmento.getValue(v), analise.tasks.getValue(v)) }
            }
        }
    }

    override fun vazio(avisos: List<AvisoDto>) = QualidadeDto("qualidade v1", JANELA_DIAS.toInt(), emptyList(), emptyList(), avisos)
}

/**
 * Cartões da Qualidade dos dados: QD-01 (as sete verificações das abertas), QD-02 (conclusão divergente), QD-03 (vocabulário: status
 * e labels sem classificação), QD-04 (bloqueadas sem causa) e QD-05 (campos sem valor, sem gaveta: a lista é de nomes).
 */
internal fun cartoesDeQualidade(): List<CartaoRegistrado> {
    val fontes = listOf(FonteDoDado.ISSUE, FonteDoDado.CONFIGURACAO)
    fun cartao(n: Int, temDrill: Boolean, segmentos: Set<String>, aceitaLabel: Boolean = false, changelog: Boolean = false): CartaoRegistrado = CartaoDeLeitura(
        metaDeTrabalho(
            "QD-0$n", CardQualidade.id, GrupoDeCartao.GOVERNANCA, Natureza.QUALIDADE,
            if (changelog) fontes + FonteDoDado.CHANGELOG else fontes, exigeChangelog = changelog, temDrill = temDrill, papeis = PAPEIS_DA_QUALIDADE,
        ),
        CardQualidade, { it in segmentos || (aceitaLabel && it.startsWith("label:")) },
    ) { dto ->
        if (!temDrill) {
            emptyMap()
        } else {
            buildMap {
                for (l in dto.linhas) if (l.verificacao in segmentos) put(l.verificacao, l.n)
                if (aceitaLabel) for (l in dto.labels) put("label:${l.label}", l.n)
            }
        }
    }
    return listOf(
        cartao(1, true, setOf("SEM_ESTIMATIVA", "SEM_RESPONSAVEL", "SEM_VENCIMENTO", "SEM_EPICO", "EM_ANDAMENTO_SEM_HORAS", "SEM_SQUAD", "PRIORIDADE_PADRAO"), changelog = true),
        cartao(2, true, setOf("DONE_SEM_RESOLUTION", "RESOLUTION_SEM_DONE")),
        cartao(3, true, setOf("STATUS_SEM_MAPEAMENTO", "LABEL_SEM_CLASSIFICACAO"), aceitaLabel = true),
        cartao(4, true, setOf("BLOQUEADA_SEM_CAUSA")),
        cartao(5, false, emptySet()),
    )
}
