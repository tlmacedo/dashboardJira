package br.sidia.painel.cards.metas

import br.sidia.painel.cards.AvisoDto
import br.sidia.painel.cards.abertas.ContagemDto
import kotlinx.serialization.Serializable

/** Agrupamento da série de Metas: por dia, semana (segunda a domingo de Manaus) ou mês. */
enum class Granularidade { DIA, SEMANA, MES }

/**
 * Resposta do card Metas e prazos (MT-01 a MT-14). O UNIVERSO são as concluídas positivas, fora de Epic, com
 * VENCIMENTO no período (`NoPrazo.noUniverso`); "no prazo" e "no prazo ajustado" saem da mesma regra de Fechadas e da
 * Auditoria. Taxas são fração de 0 a 1 (nulas com universo vazio). Δ = dia de Manaus da resolução menos o vencimento:
 * [antecipadas] Δ ≤ −1, [emD1D2] Δ de −1 a −2, [noDia] Δ = 0, [atrasadas] Δ > 0. [noPrazoOriginal] usa o vencimento
 * ORIGINAL (1º valor no changelog) e [alertaDataOriginal] avisa quando a taxa original fica abaixo da atual além do
 * limite das regras. [semVencimento] são concluídas do período sem vencimento (fora do universo, com gaveta própria).
 * Cada número abre as MESMAS tasks na gaveta (segmentos: `universo`, `noPrazo`, `noPrazoAjustado`, `noPrazoOriginal`,
 * `antecipadas`, `emD1D2`, `noDia`, `atrasadas`, `semVencimento`, `faixa:<F>`, `periodo:<chave>[:noPrazo]`,
 * `tipo:`, `prioridade:`, `squad:`, `pessoa:`).
 */
@Serializable
data class MetasDto(
    val versaoRegras: String,
    val universo: Int,
    val noPrazo: Int,
    val noPrazoAjustado: Int,
    val noPrazoOriginal: Int,
    val antecipadas: Int,
    val emD1D2: Int,
    val noDia: Int,
    val atrasadas: Int,
    val semVencimento: Int,
    val taxaNoPrazo: Double?,
    val taxaAjustada: Double?,
    val taxaOriginal: Double?,
    val taxaAntecipadas: Double?,
    val alertaDataOriginal: Boolean,
    val metaNoPrazo: Double,
    val metaAntecipadas: Double,
    val granularidade: String,
    val porPeriodo: List<PeriodoMetaDto>,
    val distribuicao: List<ContagemDto>,
    val porTipo: List<LinhaMetaDto>,
    val porPrioridade: List<LinhaMetaDto>,
    val porSquad: List<LinhaMetaDto>,
    val porPessoa: List<LinhaMetaDto>,
    val avisos: List<AvisoDto>,
)

/** Um período da série (pelo vencimento). [chave]: `aaaa-mm-dd` (dia ou segunda-feira da semana) ou `aaaa-mm` (mês). */
@Serializable
data class PeriodoMetaDto(
    val chave: String,
    val universo: Int,
    val noPrazo: Int,
    val noPrazoAjustado: Int,
    val taxaNoPrazo: Double?,
    val antecipadas: Int,
    val noDia: Int,
    val atrasadas: Int,
)

/**
 * Linha de uma tabela de Metas (tipo, prioridade, squad ou pessoa), sempre em ordem alfabética ou de catálogo, nunca por
 * taxa. [estado]: `ok` (✓ taxa ≥ meta), `atencao` (● até a faixa de atenção abaixo da meta) ou `problema` (▲).
 */
@Serializable
data class LinhaMetaDto(val chave: String, val universo: Int, val noPrazo: Int, val noPrazoAjustado: Int, val estado: String)
