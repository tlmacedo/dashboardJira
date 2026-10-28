package br.sidia.painel.cards.previsao

import br.sidia.painel.cards.AvisoDto
import br.sidia.painel.cards.fechadas.SemanaDto
import kotlinx.serialization.Serializable

/**
 * Resposta do card Previsão (PV-01 a PV-06). Cada número abre, pelo drill genérico, as MESMAS tasks que o formam
 * (segmentos: `backlog`, `vazao`, `vazao:<segunda>`, `chegada:<segunda>`, `porIssue`). Monte Carlo sobre a vazão e a
 * taxa de chegada do ESCOPO de quem consulta (semanas fechadas, segunda a domingo de Manaus), com semente derivada do
 * dia e dos filtros. [limiteSemanas]: acima dele a resposta é "mais de N semanas" (campos nulos em [termina]).
 */
@Serializable
data class PrevisaoDto(
    val versaoRegras: String,
    val backlog: Int,
    val termina: QuandoDto,
    /** `baixa`, `media` ou `alta`, pelas semanas com dado ([semanasComDado] de [semanasHistorico]). */
    val confianca: String,
    val semanasComDado: Int,
    val semanasHistorico: Int,
    val limiteSemanas: Int,
    /** Se a taxa de chegada entrou no sorteio (`comChegada` em previsao.yaml). */
    val comChegada: Boolean,
    /** Total de entregas na janela (as tasks por trás do segmento `vazao`). */
    val entregasNaJanela: Int,
    val vazaoSemanal: List<SemanaDto>,
    val chegadaSemanal: List<SemanaDto>,
    val quantos: List<QuantosDto>,
    val porIssue: List<PrevisaoIssueDto>,
    /** Unidade da idade e do restante de [porIssue]: `util` ou `corrido`. */
    val unit: String,
    val avisos: List<AvisoDto>,
)

/**
 * Datas = segunda-feira da semana alvo, a k-ésima semana INTEIRA depois da semana em curso (a semana de hoje não entra
 * na vazão). Todas nulas = "mais de [PrevisaoDto.limiteSemanas] semanas"; com backlog 0 as semanas são 0 e a data é nula.
 */
@Serializable
data class QuandoDto(
    val p50: String?, val p85: String?, val p95: String?,
    val semanasP50: Int?, val semanasP85: Int?, val semanasP95: Int?,
)

/** Quantos itens saem em [semanas] semanas, em percentis conservadores (P85 = 15º percentil dos totais, P95 = 5º). */
@Serializable
data class QuantosDto(val semanas: Int, val p50: Int, val p85: Int, val p95: Int)

/**
 * Previsão condicional de uma task em andamento: dos ciclos históricos do MESMO tipo maiores que a idade dela, o que
 * faltou (P50 e P85, na unidade do card). Nulos quando há menos de 5 ciclos mais longos ([n] diz quantos). As colunas
 * "pela capacidade" e "Atrasa?" chegam na Fase 6.
 */
@Serializable
data class PrevisaoIssueDto(
    val key: String,
    val resumo: String,
    val tipo: String,
    val status: String,
    val responsavel: String?,
    val idade: Double,
    val restanteP50: Double?,
    val restanteP85: Double?,
    val n: Int,
)
