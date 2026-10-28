package br.sidia.painel.cards.fluxo

import br.sidia.painel.cards.AvisoDto
import kotlinx.serialization.Serializable

/**
 * Uma célula da tabela cruzada: [n] tasks (o que o drill `c:<linha>:<coluna>` abre) e, com a medida idade P50, o [valor]
 * (P50 da idade na unidade do card; nulo sem tasks). [amostraPequena]: idade P50 de célula com 1 a 4 tasks.
 */
@Serializable
data class CelulaDto(val n: Int, val valor: Double?, val amostraPequena: Boolean)

/**
 * Resposta do card Cruzamentos (CZ-01). [linhas] e [colunas] são os valores dos dois eixos, na ordem de cada eixo (nunca por
 * contagem; pessoa em ordem alfabética), e [celulas] vai `[linha][coluna]`. O valor `(sem)` é "sem esse dado" (sem responsável,
 * sem prioridade, sem vencimento...). [totaisDasLinhas] é nulo quando as linhas são PESSOAS: não há total por pessoa em
 * destaque. Segmentos do drill, POSICIONAIS (1 = primeiro valor da lista): `c:<linha>:<coluna>`, `l:<linha>` (total da
 * linha, exceto pessoa), `k:<coluna>` e `total`. [eixosDeLinha] e [eixosDeColuna] são a lista fechada oferecida.
 */
@Serializable
data class CruzamentosDto(
    val linhasEixo: String,
    val colunasEixo: String,
    val medida: String,
    val linhas: List<String>,
    val colunas: List<String>,
    val celulas: List<List<CelulaDto>>,
    val totaisDasLinhas: List<Int>?,
    val totaisDasColunas: List<Int>,
    val total: Int,
    val eixosDeLinha: List<String>,
    val eixosDeColuna: List<String>,
    val unit: String,
    val avisos: List<AvisoDto>,
)
