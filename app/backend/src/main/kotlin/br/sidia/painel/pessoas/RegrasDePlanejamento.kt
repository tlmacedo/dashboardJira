package br.sidia.painel.pessoas

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.Serializable
import java.nio.file.Files
import java.nio.file.Path

/**
 * Heurísticas da capacidade e do planejamento, lidas de `planejamento.yaml` (versionado, sem dado pessoal; em produção o
 * arquivo real é obrigatório). Os valores são ponto de partida, a calibrar com o histórico real; a [versao] sai em toda
 * resposta dos cards (`versaoRegras`).
 */
data class RegrasDePlanejamento(
    val versao: Int,
    val focoPadrao: Double,
    val horasMinimas: Double,
    val horasPorTipo: Map<String, Double>,
    val horasPadrao: Double,
    val semanasMinimasDemanda: Int,
    val semanasSemVencimentoIniciada: Int,
    val semanasSemVencimentoNaoIniciada: Int,
    val ocupacaoAtencao: Double,
    val ocupacaoAlta: Double,
    val folga: Double,
    val tetoJain: Double,
    val wipPorStatus: Map<String, Int>,
    val wipPorPessoa: Int,
    val slaRevisaoDias: Int,
    val diaCriticoCapacidade: Double,
    val horizonteCoberturaDias: Int,
) {
    init {
        require(versao == 1) { "versao não suportada (esperado: 1)" }
        require(focoPadrao > 0.0 && focoPadrao <= 1.0) { "focoPadrao fora de (0, 1]" }
        require(horasMinimas > 0.0) { "horasMinimas deve ser maior que 0" }
        require(horasPorTipo.values.all { it > 0.0 }) { "horasPorTipo com valor não positivo" }
        require(horasPadrao > 0.0) { "horasPadrao deve ser maior que 0" }
        require(semanasMinimasDemanda >= 1) { "semanasMinimasDemanda deve ser pelo menos 1" }
        require(semanasSemVencimentoIniciada >= 1) { "semanasSemVencimentoIniciada deve ser pelo menos 1" }
        require(semanasSemVencimentoNaoIniciada >= 1) { "semanasSemVencimentoNaoIniciada deve ser pelo menos 1" }
        require(folga > 0.0) { "folga deve ser maior que 0" }
        require(folga < ocupacaoAtencao) { "folga deve ser menor que ocupacaoAtencao" }
        require(ocupacaoAtencao < ocupacaoAlta) { "ocupacaoAtencao deve ser menor que ocupacaoAlta" }
        require(tetoJain > 0.0) { "tetoJain deve ser maior que 0" }
        require(wipPorStatus.values.all { it >= 1 }) { "wipPorStatus com limite menor que 1" }
        require(wipPorPessoa >= 1) { "wipPorPessoa deve ser pelo menos 1" }
        require(slaRevisaoDias >= 1) { "slaRevisaoDias deve ser pelo menos 1" }
        require(diaCriticoCapacidade > 0.0 && diaCriticoCapacidade <= 1.0) { "diaCriticoCapacidade fora de (0, 1]" }
        require(horizonteCoberturaDias >= 1) { "horizonteCoberturaDias deve ser pelo menos 1" }
    }

    companion object {
        /** Valores do modelo `planejamento.exemplo.yaml`, para os testes que não leem o arquivo. */
        val PADRAO = RegrasDePlanejamento(
            versao = 1, focoPadrao = 0.7, horasMinimas = 0.5,
            horasPorTipo = mapOf("Checklist" to 6.0, "Checklist TG" to 6.0, "Checklist Review" to 2.0), horasPadrao = 6.0,
            semanasMinimasDemanda = 2, semanasSemVencimentoIniciada = 2, semanasSemVencimentoNaoIniciada = 4,
            ocupacaoAtencao = 0.85, ocupacaoAlta = 1.10, folga = 0.50, tetoJain = 3.0,
            wipPorStatus = mapOf("In Progress" to 12, "Submitted" to 5), wipPorPessoa = 3, slaRevisaoDias = 2,
            diaCriticoCapacidade = 0.6, horizonteCoberturaDias = 30,
        )

        /** Estrito: chave desconhecida ou ausente, limite violado ou limiares incoerentes falham citando o arquivo. */
        fun fromYaml(path: Path): RegrasDePlanejamento = lerEstrito(path) {
            val b = Yaml.default.decodeFromString(RegrasDePlanejamentoYaml.serializer(), Files.readString(path))
            RegrasDePlanejamento(
                b.versao, b.focoPadrao, b.horasMinimas, b.horasPorTipo, b.horasPadrao, b.semanasMinimasDemanda,
                b.semanasSemVencimentoIniciada, b.semanasSemVencimentoNaoIniciada, b.ocupacaoAtencao, b.ocupacaoAlta, b.folga,
                b.tetoJain, b.wipPorStatus, b.wipPorPessoa, b.slaRevisaoDias, b.diaCriticoCapacidade, b.horizonteCoberturaDias,
            )
        }
    }
}

@Serializable
private data class RegrasDePlanejamentoYaml(
    val versao: Int,
    val focoPadrao: Double,
    val horasMinimas: Double,
    val horasPorTipo: Map<String, Double>,
    val horasPadrao: Double,
    val semanasMinimasDemanda: Int,
    val semanasSemVencimentoIniciada: Int,
    val semanasSemVencimentoNaoIniciada: Int,
    val ocupacaoAtencao: Double,
    val ocupacaoAlta: Double,
    val folga: Double,
    val tetoJain: Double,
    val wipPorStatus: Map<String, Int>,
    val wipPorPessoa: Int,
    val slaRevisaoDias: Int,
    val diaCriticoCapacidade: Double,
    val horizonteCoberturaDias: Int,
)
