package br.sidia.painel.governanca

import br.sidia.painel.cards.AvisoDto
import kotlinx.serialization.Serializable

/**
 * Uma linha da Qualidade dos dados. [estado]: `ok` (até 10%), `atencao` (até 30%), `problema`, `neutro` (só informativa) ou
 * `indisponivel` (falta o dado para a verificação). [nomes] são nomes de vocabulário (status, campos, labels), nunca pessoas.
 */
@Serializable
data class LinhaQDDto(val verificacao: String, val n: Int, val universo: Int, val fracao: Double?, val estado: String, val nomes: List<String>)

/** Um label recente sem classificação e quantas tasks o carregam; a lista vem em ordem alfabética (nunca por contagem). */
@Serializable
data class LabelSemClasseDto(val label: String, val n: Int)

/**
 * Resposta da Qualidade dos dados (QD-01 a QD-05). Só leitura: a lista de labels ignoradas e o vocabulário são do gestor, que edita
 * os YAML; o painel não grava nada. [janelaDias] é a janela das verificações de conclusão e de labels (90 dias).
 */
@Serializable
data class QualidadeDto(
    val versaoRegras: String,
    val janelaDias: Int,
    val linhas: List<LinhaQDDto>,
    val labels: List<LabelSemClasseDto>,
    val avisos: List<AvisoDto>,
)
