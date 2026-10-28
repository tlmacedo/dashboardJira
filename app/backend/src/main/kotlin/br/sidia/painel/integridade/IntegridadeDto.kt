package br.sidia.painel.integridade

import br.sidia.painel.cards.AvisoDto
import kotlinx.serialization.Serializable

/** Aviso fixo de toda resposta e de toda tela da Integridade: o painel mostra INDÍCIO para revisão humana, nunca prova. */
const val AVISO_INDICIO_NAO_PROVA_INTEGRIDADE = "INDICIO_NAO_PROVA"

/** Aviso fixo: a base legal do uso real ainda precisa ser validada com o DPO (até lá, só fixtures e demonstração). */
const val AVISO_VALIDAR_BASE_LEGAL_COM_DPO = "VALIDAR_BASE_LEGAL_COM_DPO"

/**
 * Resposta de `GET /api/integridade` (só GERENTE, sempre auditada). [aviso] e [avisoDpo] são fixos e não têm valor padrão de
 * propósito: o serializador omite campo com valor padrão, e eles têm de sair em TODA resposta. Nenhuma lista aqui ordena
 * pessoas por desempenho: as pessoas vêm em ordem alfabética.
 */
@Serializable
data class IntegridadeDto(
    val aviso: String,
    val avisoDpo: String,
    val versaoRegras: String,
    val de: String,
    val ate: String,
    val resumo: ResumoIntegridadeDto,
    val evidencias: List<EvidenciaDto>,
    val avisos: List<AvisoDto>,
)

/** Um sinal no resumo: quantas evidências e o quanto pesa (a lista segue a ordem do catálogo, nunca a da contagem). */
@Serializable
data class ContagemDeSinalDto(val sinal: String, val gravidade: Int, val ativo: Boolean, val n: Int)

/**
 * Pessoa com indício ALTO ou MEDIO (as demais não saem na lista, para a API não carregar os pontos de quem está normal).
 * [evidencias] é quantas evidências a pessoa tem no recorte; sem posição nem nota.
 */
@Serializable
data class NivelPessoaDto(
    val pessoa: String,
    val nivel: String,
    val pontos: Int,
    val concluidas: Int,
    val taxa: Double,
    val multiplo: Double,
    val graves: Int,
    val evidencias: Int,
)

@Serializable
data class ResumoIntegridadeDto(
    val evidencias: Int,
    val issuesAfetadas: Int,
    val medianaTaxa: Double,
    val porSinal: List<ContagemDeSinalDto>,
    val pessoas: List<NivelPessoaDto>,
    val pessoasAlto: Int,
    val pessoasMedio: Int,
    /** Evidências das pessoas de cada nível: o que a gaveta dos cartões de nível abre (o número do cartão é este). */
    val evidenciasNivelAlto: Int,
    val evidenciasNivelMedio: Int,
    /** `(no prazo − pela data original) × 100` do mesmo período e squad (IN-06); nulo sem os dois valores. */
    val impactoMetasPp: Double?,
    val taxaNoPrazo: Double?,
    val taxaOriginal: Double?,
    val pontosAlto: Int,
    val multiploAlto: Double,
    val gravesAlto: Int,
    val pontosMedio: Int,
    val multiploMedio: Double,
    val concluidasMinimas: Int,
)

/**
 * Uma evidência (INDÍCIO): o [sinal] na task [key] em [quando] (instante) e [dia] (dia de Manaus). [pessoa] é o autor do evento que
 * dispara o sinal; o responsável da task no instante está em [detalhe] (`responsavel`). Nunca texto livre do Jira.
 */
@Serializable
data class EvidenciaDto(
    val sinal: String,
    val gravidade: Int,
    val key: String,
    val pessoa: String?,
    val quando: String,
    val dia: String,
    val detalhe: Map<String, String>,
)

/** Resposta de `GET /api/integridade/catalogo` (IN-08): o que cada sinal procura, a gravidade e o limite. Sem dado de pessoas. */
@Serializable
data class CatalogoIntegridadeDto(
    val aviso: String,
    val avisoDpo: String,
    val versaoRegras: String,
    val sinais: List<SinalDoCatalogoDto>,
    val niveis: NiveisDoCatalogoDto,
)

/** [params] só leva números e nomes de label de criticidade (o limite da regra), para a tela escrever a frase pelo código. */
@Serializable
data class SinalDoCatalogoDto(val sinal: String, val gravidade: Int, val ativo: Boolean, val params: Map<String, String>)

@Serializable
data class NiveisDoCatalogoDto(
    val pontosAlto: Int,
    val multiploAlto: Double,
    val gravesAlto: Int,
    val pontosMedio: Int,
    val multiploMedio: Double,
    val concluidasMinimas: Int,
    val pisoMediana: Double,
)
