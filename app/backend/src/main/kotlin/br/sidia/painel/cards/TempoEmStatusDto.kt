package br.sidia.painel.cards

import br.sidia.painel.domain.PersonStatus
import br.sidia.painel.domain.StatusStat
import br.sidia.painel.domain.StuckIssue
import kotlinx.serialization.Serializable

/**
 * Resposta do card "Tempo em status". Só carrega o que o papel de quem pediu pode ver:
 * nomes de pessoas fora da visibilidade nunca entram em [pessoaStatus] nem em [abertasParadas].
 * [unit] é `util` ou `corrido`; [avisos] traz problemas de dados, cada um com código estável e gravidade;
 * só os `bloqueante` tornam os números indisponíveis (ver [AvisoDto]).
 */
@Serializable
data class TempoEmStatusDto(
    val analisadas: Int,
    val eficienciaFluxo: Double?,
    val paradas: Int,
    val porStatus: List<StatusStat>,
    val pessoaStatus: List<PersonStatus>,
    val abertasParadas: List<StuckIssue>,
    val unit: String,
    val avisos: List<AvisoDto>,
)

/**
 * Aviso de dados do card. [codigo] é estável (o frontend traduz por ele, não pela [mensagem]);
 * [gravidade] é `bloqueante` (números indisponíveis) ou `informativo` (números continuam válidos).
 * [params] leva os dados do aviso (nome do status, contagem) para o frontend montar o texto pelo código, sem
 * extrair nada da [mensagem]: `STATUS_SEM_MAPEAMENTO` traz `status` e `CONCLUSAO_DIVERGENTE` traz `n`.
 */
@Serializable
data class AvisoDto(
    val codigo: String,
    val gravidade: String,
    val mensagem: String,
    val params: Map<String, String> = emptyMap(),
) {
    companion object {
        const val STATUS_SEM_MAPEAMENTO = "STATUS_SEM_MAPEAMENTO"
        /** Status concluído sem `resolution`, ou `resolution` sem status concluído (spec 4.2: Qualidade dos dados). */
        const val CONCLUSAO_DIVERGENTE = "CONCLUSAO_DIVERGENTE"
        /** Status concluído e `resolution` preenchida, mas sem data de resolução: fica fora dos números (informativo). */
        const val CONCLUIDA_SEM_DATA = "CONCLUIDA_SEM_DATA"
        const val CALCULO_INDISPONIVEL = "CALCULO_INDISPONIVEL"
        const val DATAS_INCONSISTENTES = "DATAS_INCONSISTENTES"
        const val OS_INDISPONIVEL = "OS_INDISPONIVEL"
        /** Faixa histórica (P50/P85) com menos itens que o mínimo: o número aparece, o aviso diz que é instável (`tipo`, `n`, `minimo`). */
        const val AMOSTRA_PEQUENA = "AMOSTRA_PEQUENA"
        /** Nenhuma issue da base tem prioridade: a base foi sincronizada antes dos campos de trabalho (informativo). */
        const val CAMPOS_DE_TRABALHO_INDISPONIVEIS = "CAMPOS_DE_TRABALHO_INDISPONIVEIS"
        /** Issues em andamento de um tipo sem concluídas no histórico: ficam fora da comparação com o P85 (`n`). */
        const val SEM_HISTORICO_DO_TIPO = "SEM_HISTORICO_DO_TIPO"
        /** Status concluído sem `resolution`: fica fora dos números e vai para a Qualidade dos dados (`n`). */
        const val DONE_SEM_RESOLUTION = "DONE_SEM_RESOLUTION"
        /** `status_map.ordem` não configurada: a "devolvida" não pode ser calculada. */
        const val ORDEM_DO_FLUXO_AUSENTE = "ORDEM_DO_FLUXO_AUSENTE"
        /** Nenhuma entrega na janela de semanas da Previsão: sem vazão não há como estimar (informativo; sem datas). */
        const val SEM_VAZAO_HISTORICA = "SEM_VAZAO_HISTORICA"
        /** Nenhum issue da base tem o campo de épico (`Epic Link`): a tabela de épicos fica vazia (informativo). */
        const val EPICO_INDISPONIVEL = "EPICO_INDISPONIVEL"
        /** Itens da Central de um módulo atrás da trava do DPO ficam fora da lista (`modulos`: nomes separados por vírgula). Informativo. */
        const val ITENS_AGUARDANDO_ACEITE_DPO = "ITENS_AGUARDANDO_ACEITE_DPO"
        /** O histórico da gaveta fica fora enquanto a Auditoria aguarda o aceite do DPO. Informativo. */
        const val HISTORICO_AGUARDANDO_ACEITE_DPO = "HISTORICO_AGUARDANDO_ACEITE_DPO"
        /** Eventos de estimativa gravados sem valor (antes da carga que traz os segundos): não geram sinal (`n`). Informativo. */
        const val HISTORICO_DE_ESTIMATIVA_INCOMPLETO = "HISTORICO_DE_ESTIMATIVA_INCOMPLETO"
        /** Worklogs gravados sem a data de criação (antes da nova carga): não geram sinal (`n`). Informativo. */
        const val HISTORICO_DE_WORKLOG_INCOMPLETO = "HISTORICO_DE_WORKLOG_INCOMPLETO"
        /** Alguma lista de opções de filtro passou do limite e foi cortada (`lista`, `limite`). */
        const val OPCOES_TRUNCADAS = "OPCOES_TRUNCADAS"
        /** Issues cujos campos do dicionário vieram só do `raw` antigo: a reconciliação completa os completa (`n`). */
        const val CAMPOS_DO_DICIONARIO_INCOMPLETOS = "CAMPOS_DO_DICIONARIO_INCOMPLETOS"
        const val BLOQUEANTE = "bloqueante"
        const val INFORMATIVO = "informativo"

        // Auditoria (Plano 4): todos informativos; os dados vão em `params`.
        /** Há evento do campo `reporter` (o changelog só guarda o nome do campo): a autorização usa o reporter atual. */
        const val REPORTER_ALTERADO = "REPORTER_ALTERADO"
        /** Valores de vencimento no changelog que não são data legível. */
        const val VENCIMENTO_ILEGIVEL = "VENCIMENTO_ILEGIVEL"
        /** Issues ignoradas por status fora do mapa (`status` e `n`). */
        const val ISSUES_IGNORADAS_STATUS = "ISSUES_IGNORADAS_STATUS"
        /** Review ligada a mais de uma task (ou task com mais de uma Review): ficou o par de menor chave. */
        const val VINCULO_AMBIGUO = "VINCULO_AMBIGUO"
        /** Nenhum vínculo do tipo configurado na base: o card Task × Review fica vazio. */
        const val VINCULOS_INDISPONIVEIS = "VINCULOS_INDISPONIVEIS"
    }
}

/** Linha do detalhamento (drill). [responsavel] é nulo quando a pessoa está fora da visibilidade. */
@Serializable
data class DrillRow(
    val key: String,
    val resumo: String,
    val responsavel: String?,
    val status: String,
    val detalhe: String,
)

/** Quem está logado, com os papéis do diretório na data de hoje (nunca de claims). */
@Serializable
data class MeDto(val userId: String, val roles: List<String>, val modulosBloqueados: List<String> = emptyList())
