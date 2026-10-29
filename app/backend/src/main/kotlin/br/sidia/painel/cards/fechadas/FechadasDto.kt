package br.sidia.painel.cards.fechadas

import br.sidia.painel.cards.AvisoDto
import br.sidia.painel.cards.abertas.ContagemDto
import br.sidia.painel.cards.abertas.FaixaTipoDto
import kotlinx.serialization.Serializable

/**
 * Resposta do card Fechadas (FE-01 a FE-12). Cada número abre, pelo drill genérico, as MESMAS tasks que o formam
 * (segmentos: `concluidas`, `comPontos`, `ciclo`, `lead`, `noPrazo`, `noPrazoAjustado`, `comVencimento`, `canceladas`,
 * `semana:<segunda>`, `dispersao`, `tipo:<tipo>`, `projeto:<projeto>`, `pessoa:<id>:finalizou|participou`,
 * `semResolution`). "No prazo" e "no prazo ajustado" vêm lado a lado da MESMA regra da Auditoria e das Metas
 * (`NoPrazo`). Taxas são fração de 0 a 1, nulas sem tasks com vencimento. [pontos] é nulo quando nenhuma concluída tem
 * pontos. [unit] é `util` ou `corrido`.
 */
@Serializable
data class FechadasDto(
    val concluidas: Int,
    val pontos: Double?,
    /** Quantas concluídas têm pontos (as tasks por trás do total de [pontos]). */
    val comPontos: Int,
    val ciclo: FaixaDto?,
    val lead: FaixaDto?,
    val noPrazo: Int,
    val noPrazoAjustado: Int,
    val comVencimento: Int,
    val taxaNoPrazo: Double?,
    val taxaAjustada: Double?,
    val canceladas: Int,
    val porSemana: List<SemanaDto>,
    val dispersao: DispersaoDto?,
    val porTipo: List<FaixaTipoDto>,
    val porProjeto: List<ContagemDto>,
    val porPessoa: List<EntregaPessoaDto>,
    val linhas: List<LinhaFechadaDto>,
    val semResolution: Int,
    val unit: String,
    val avisos: List<AvisoDto>,
    /** Concluídas por campo do dicionário com `filtro: true` (segmento `campo:<slug>:<valor>`; `-` = sem valor). */
    val porCampo: List<br.sidia.painel.cards.AgrupamentoDto> = emptyList(),
)

@Serializable
data class FaixaDto(val p50: Double, val p85: Double, val n: Int)

@Serializable
data class PontoCicloDto(val key: String, val dia: String, val ciclo: Double)

@Serializable
data class DispersaoDto(val p50: Double, val p85: Double, val p95: Double, val pontos: List<PontoCicloDto>)

@Serializable
data class SemanaDto(val semana: String, val n: Int)

/**
 * Entregas de uma pessoa (ordem alfabética, sem posição nem "melhor/pior"). [finalizou]: era o responsável ao resolver;
 * [participou]: teve a task em algum momento. Horas pelas estimativas e pelo tempo registrado da própria issue
 * (o worklog por pessoa fica para a Fase 6).
 */
@Serializable
data class EntregaPessoaDto(
    val pessoa: String,
    val finalizou: Int,
    val participou: Int,
    val pontos: Double?,
    val cicloP50: Double?,
    val horasEstimadas: Double?,
    val horasGastas: Double?,
    /** FE-11: dias (na unidade pedida) em status ATIVO enquanto a pessoa era a responsável, nas concluídas do período; nulo sem trecho ativo dela. */
    val tempoAtivoDias: Double? = null,
)

/** Uma concluída na tabela (FE-12). [prazo]: `noPrazo`, `ajustado`, `atrasada` ou `semVencimento`. */
@Serializable
data class LinhaFechadaDto(
    val key: String,
    val resumo: String,
    val tipo: String,
    val responsavel: String?,
    val resolvida: String,
    val ciclo: Double?,
    val lead: Double?,
    val vence: String?,
    val prazo: String,
    val pontos: Double?,
    val horasEstimadas: Double?,
    val horasGastas: Double?,
    /** FE-12: horas gastas ÷ horas estimadas; nulo sem estimativa (ou estimativa zero) ou sem horas gastas. */
    val realSobreEstimado: Double? = null,
)
