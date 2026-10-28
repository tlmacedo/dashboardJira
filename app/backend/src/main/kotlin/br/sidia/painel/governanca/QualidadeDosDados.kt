package br.sidia.painel.governanca

import br.sidia.painel.domain.LabelsDeAtraso
import br.sidia.painel.domain.StatusCategory
import br.sidia.painel.domain.StatusMap
import br.sidia.painel.domain.UnmappedStatusException
import br.sidia.painel.security.RoleDirectory
import br.sidia.painel.trabalho.Fluxo
import br.sidia.painel.trabalho.IssueSnapshot
import java.time.LocalDate
import java.util.Locale

/**
 * As verificações da Qualidade dos dados. As sete primeiras são as do app anterior (sobre as abertas); as duas seguintes, a
 * divergência entre status concluído e `resolution` (spec 4.2); depois o vocabulário (status fora do mapa, campos sem valor em
 * nenhuma issue e labels sem classificação, informativa) e as bloqueadas sem causa (ponto de extensão do Bloqueios, 17b).
 * `PAI_CONCLUIDO_COM_FILHAS_ABERTAS` (I-14) NÃO está aqui: só existe se a ficha trouxer sub-tarefas (Plano 14b).
 */
enum class VerificacaoQD {
    SEM_ESTIMATIVA, SEM_RESPONSAVEL, SEM_VENCIMENTO, SEM_EPICO, EM_ANDAMENTO_SEM_HORAS, SEM_SQUAD, PRIORIDADE_PADRAO,
    DONE_SEM_RESOLUTION, RESOLUTION_SEM_DONE, STATUS_SEM_MAPEAMENTO, CAMPO_INDISPONIVEL, LABEL_SEM_CLASSIFICACAO, BLOQUEADA_SEM_CAUSA,
}

/**
 * Uma linha da tela: [n] de [universo] ([fracao] nula sem universo). [estado]: `ok` (até 10%), `atencao` (até 30%) ou `problema`
 * (acima); `neutro` na verificação só informativa e `indisponivel` quando o dado para ela não existe. [nomes] são os nomes que a
 * linha cita (status fora do mapa, campos sem valor, labels): vocabulário, nunca pessoa.
 */
data class LinhaQD(val verificacao: VerificacaoQD, val n: Int, val universo: Int, val fracao: Double?, val estado: String, val nomes: List<String> = emptyList())

/** O resultado completo: as [linhas] e, por verificação, as tasks que a formam (a gaveta abre exatamente estas). */
class AnaliseQD(val linhas: List<LinhaQD>, val tasks: Map<VerificacaoQD, List<IssueSnapshot>>, val tasksPorLabel: Map<String, List<IssueSnapshot>>)

object QualidadeDosDados {
    const val LIMITE_OK = 0.10
    const val LIMITE_ATENCAO = 0.30

    /** Estado pela fração: 10% ainda é ok, 30% ainda é atenção, acima disso problema. Sem universo, `ok`. */
    fun estadoDaFracao(fracao: Double?): String = when {
        fracao == null -> "ok"
        fracao <= LIMITE_OK + 1e-9 -> "ok"
        fracao <= LIMITE_ATENCAO + 1e-9 -> "atencao"
        else -> "problema"
    }

    private fun linha(v: VerificacaoQD, n: Int, universo: Int, estado: String? = null, nomes: List<String> = emptyList()): LinhaQD {
        val fracao = if (universo > 0) n.toDouble() / universo else null
        return LinhaQD(v, n, universo, fracao, estado ?: estadoDaFracao(fracao), nomes)
    }

    /**
     * Verifica as [abertas] (sem as concluídas) e as [recentes] (resolvidas ou em concluído nos últimos 90 dias). Status fora do
     * mapa NUNCA derruba a verificação: a issue fica de fora das contas por categoria e aparece em [VerificacaoQD.STATUS_SEM_MAPEAMENTO]
     * com o nome do status. [camposDisponiveis] diz, por campo "a confirmar", se alguma issue tem valor; sem `Epic Link` a verificação
     * `SEM_EPICO` fica `indisponivel` (todas "sem épico" seria ruído). [bloqueadasSemCausa] vem do Bloqueios (nulo = indisponível).
     */
    fun verificar(
        abertas: List<IssueSnapshot>,
        recentes: List<IssueSnapshot>,
        map: StatusMap,
        roles: RoleDirectory,
        hoje: LocalDate,
        prioridadePadrao: String,
        camposDisponiveis: Map<String, Boolean>,
        labels: LabelsDeAtraso? = null,
        bloqueadasSemCausa: List<IssueSnapshot>? = null,
    ): List<LinhaQD> = analisar(abertas, recentes, map, roles, hoje, prioridadePadrao, camposDisponiveis, labels, bloqueadasSemCausa).linhas

    fun analisar(
        abertas: List<IssueSnapshot>,
        recentes: List<IssueSnapshot>,
        map: StatusMap,
        roles: RoleDirectory,
        hoje: LocalDate,
        prioridadePadrao: String,
        camposDisponiveis: Map<String, Boolean>,
        labels: LabelsDeAtraso? = null,
        bloqueadasSemCausa: List<IssueSnapshot>? = null,
    ): AnaliseQD {
        fun categoria(s: IssueSnapshot): StatusCategory? = try {
            map.category(s.status)
        } catch (e: UnmappedStatusException) {
            null
        }

        val tudo = (abertas + recentes).distinctBy { it.key }
        val foraDoMapa = tudo.filter { categoria(it) == null }
        // Universo das contas sobre as abertas: não concluídas e com status mapeado.
        val base = abertas.filter { categoria(it).let { c -> c != null && c != StatusCategory.DONE } }
        val tasks = LinkedHashMap<VerificacaoQD, List<IssueSnapshot>>()
        val linhas = ArrayList<LinhaQD>()

        fun sobreAbertas(v: VerificacaoQD, universo: List<IssueSnapshot> = base, regra: (IssueSnapshot) -> Boolean) {
            val achadas = universo.filter(regra)
            tasks[v] = achadas
            linhas += linha(v, achadas.size, universo.size)
        }

        sobreAbertas(VerificacaoQD.SEM_ESTIMATIVA) { (it.estimateS ?: 0L) <= 0L }
        sobreAbertas(VerificacaoQD.SEM_RESPONSAVEL) { it.assignee == null }
        sobreAbertas(VerificacaoQD.SEM_VENCIMENTO) { it.duedate == null }
        if (camposDisponiveis["Epic Link"] == false) {
            tasks[VerificacaoQD.SEM_EPICO] = emptyList()
            linhas += LinhaQD(VerificacaoQD.SEM_EPICO, 0, 0, null, "indisponivel", listOf("Epic Link"))
        } else {
            sobreAbertas(VerificacaoQD.SEM_EPICO) { it.epicKey.isNullOrBlank() }
        }
        val emAndamento = base.filter { Fluxo.emAndamento(it, map) }
        sobreAbertas(VerificacaoQD.EM_ANDAMENTO_SEM_HORAS, emAndamento) { (it.spentS ?: 0L) <= 0L }
        // Sem squad = responsável que não consta no papeis.yaml na data (quem não tem responsável já está em SEM_RESPONSAVEL).
        sobreAbertas(VerificacaoQD.SEM_SQUAD, base.filter { it.assignee != null }) { roles.rolesOf(it.assignee!!, hoje).isEmpty() }
        sobreAbertas(VerificacaoQD.PRIORIDADE_PADRAO) { it.priority?.trim().equals(prioridadePadrao.trim(), ignoreCase = true) }

        // Conclusão divergente: status concluído sem resolution, ou resolution sem status concluído.
        val concluidasRecentes = recentes.filter { categoria(it) == StatusCategory.DONE }
        val doneSemResolution = concluidasRecentes.filter { it.resolution.isNullOrBlank() }
        tasks[VerificacaoQD.DONE_SEM_RESOLUTION] = doneSemResolution
        linhas += linha(VerificacaoQD.DONE_SEM_RESOLUTION, doneSemResolution.size, concluidasRecentes.size)
        val comResolution = recentes.filter { categoria(it) != null && !it.resolution.isNullOrBlank() }
        val resolutionSemDone = comResolution.filter { categoria(it) != StatusCategory.DONE }
        tasks[VerificacaoQD.RESOLUTION_SEM_DONE] = resolutionSemDone
        linhas += linha(VerificacaoQD.RESOLUTION_SEM_DONE, resolutionSemDone.size, comResolution.size)

        // Vocabulário: status fora do mapa (com o nome), campos sem valor em nenhuma issue e labels sem classificação.
        tasks[VerificacaoQD.STATUS_SEM_MAPEAMENTO] = foraDoMapa
        linhas += linha(
            VerificacaoQD.STATUS_SEM_MAPEAMENTO, foraDoMapa.size, tudo.size, if (foraDoMapa.isEmpty()) "ok" else "problema",
            foraDoMapa.map { it.status }.distinct().sorted(),
        )
        val semValor = camposDisponiveis.filterValues { !it }.keys.sorted()
        tasks[VerificacaoQD.CAMPO_INDISPONIVEL] = emptyList()
        linhas += linha(VerificacaoQD.CAMPO_INDISPONIVEL, semValor.size, camposDisponiveis.size, if (semValor.isEmpty()) "ok" else "atencao", semValor)

        val semClasse = labels?.let { l -> semClassificacao(tudo, l) }.orEmpty()
        val comLabelSemClasse = semClasse.values.flatten().distinctBy { it.key }.sortedBy { it.key }
        tasks[VerificacaoQD.LABEL_SEM_CLASSIFICACAO] = comLabelSemClasse
        val comAlgumLabel = tudo.count { s -> s.labels.any { it.isNotBlank() } }
        // Só informativa: nunca "problema". Sem o vocabulário carregado (`labels` nulo) a verificação não existe.
        linhas += if (labels == null) {
            LinhaQD(VerificacaoQD.LABEL_SEM_CLASSIFICACAO, 0, 0, null, "indisponivel")
        } else {
            linha(VerificacaoQD.LABEL_SEM_CLASSIFICACAO, comLabelSemClasse.size, comAlgumLabel, if (comLabelSemClasse.isEmpty()) "ok" else "neutro", semClasse.keys.toList())
        }

        // Bloqueadas sem causa: status ou flag de bloqueio sem nenhum vínculo aberto que explique (conta do Bloqueios, 17b).
        val bloqueadas = base.filter { categoria(it) == StatusCategory.BLOCKED || it.flagged }
        if (bloqueadasSemCausa == null) {
            tasks[VerificacaoQD.BLOQUEADA_SEM_CAUSA] = emptyList()
            linhas += LinhaQD(VerificacaoQD.BLOQUEADA_SEM_CAUSA, 0, 0, null, "indisponivel")
        } else {
            tasks[VerificacaoQD.BLOQUEADA_SEM_CAUSA] = bloqueadasSemCausa
            linhas += linha(VerificacaoQD.BLOQUEADA_SEM_CAUSA, bloqueadasSemCausa.size, maxOf(bloqueadas.size, bloqueadasSemCausa.size))
        }
        return AnaliseQD(linhas, tasks, semClasse)
    }

    /**
     * Label (sem diferenciar maiúsculas; a grafia mostrada é a primeira vista) que não justifica atraso nem está na lista de ignoradas,
     * em ordem alfabética, com as tasks que o carregam. Label em branco não conta.
     */
    private fun semClassificacao(issues: List<IssueSnapshot>, labels: LabelsDeAtraso): Map<String, List<IssueSnapshot>> {
        val porLabel = java.util.TreeMap<String, MutableList<IssueSnapshot>>(String.CASE_INSENSITIVE_ORDER)
        for (s in issues) {
            for (l in s.labels.map { it.trim() }.filter { it.isNotEmpty() && !labels.classificada(it) }.distinctBy { it.lowercase(Locale.ROOT) }) {
                porLabel.getOrPut(l) { mutableListOf() } += s
            }
        }
        return porLabel.mapValues { e -> e.value.distinctBy { it.key }.sortedBy { it.key } }
    }
}
