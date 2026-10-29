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
 * Uma consulta do registro de acesso (AU-02). Só o que o `access_log` guarda e nada que identifique o ACHADO: [rota] é o padrão da
 * rota (chave de issue vira `{key}`), [resultado] é o status HTTP e [filtros] são só os NOMES dos filtros usados (nunca os valores,
 * os segmentos nem chaves de task). Sem contagem por pessoa.
 */
@Serializable
data class AcessoDto(val quando: String, val usuario: String, val rota: String, val resultado: Int?, val filtros: List<String>)

/** Uma página do registro de acesso, do mais recente ao mais antigo. [proximo] é o cursor da página seguinte (`depoisDe`); nulo = fim. */
@Serializable
data class AcessosDto(val de: String, val ate: String, val acessos: List<AcessoDto>, val proximo: Long?, val tamanhoDaPagina: Int)

/** Um arquivo de configuração carregado: nome lógico (nunca o caminho), versão do YAML (se tem), SHA-256 do conteúdo e se vale o real ou o modelo. */
@Serializable
data class ArquivoConfigDto(val nome: String, val versao: Int?, val sha256: String, val origem: String)

/**
 * A Configuração em leitura (só gerente): ambiente, modo da sincronização, os arquivos de regras e só as CONTAGENS de pessoas e
 * squads. Nunca traz segredo, token, URL, host, caminho de pasta, nome, username nem o valor de `papeis.yaml`, `equipes.yaml` ou `ausencias.yaml`.
 */
@Serializable
data class ConfiguracaoDto(
    val devMode: Boolean,
    val liveEnabled: Boolean,
    val modoSync: String,
    val arquivos: List<ArquivoConfigDto>,
    val pessoas: Int,
    val squads: Int,
    /** Referência do parecer do DPO quando o aceite está ligado; nulo enquanto a trava do DPO está fechada (só a referência, nunca o parecer). */
    val aceiteDpo: String? = null,
)

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
