package br.sidia.painel.distribuicao

import br.sidia.painel.pessoas.lerEstrito
import com.charleskorn.kaml.Yaml
import kotlinx.serialization.Serializable
import java.nio.file.Files
import java.nio.file.Path

/**
 * Heurísticas da Distribuição e do Rebalanceamento, lidas de `distribuicao.yaml` (versionado, sem dado pessoal; em
 * produção o arquivo real é obrigatório). Vieram do app anterior e ainda não foram calibradas com dados reais. A [versao]
 * sai em toda resposta (`versaoRegras`) e na decisão gravada, para a auditoria saber com que regra a sugestão foi feita.
 *
 * [histereseRebalanceamentoPp] é em pontos percentuais (20 = 20 pp); [gatilhoRebalanceamento] é a ocupação (1,10 = 110%).
 */
data class RegrasDeDistribuicao(
    val versao: Int,
    val pesoWip: Double,
    val penalidadeWipCheio: Double,
    val pesoCompetenciaFaltante: Double,
    val bonusEpicoOuComponente: Double,
    val produtividadeMin: Double,
    val produtividadeMax: Double,
    val capacidadeMinimaH: Double,
    val gatilhoRebalanceamento: Double,
    val maxMovimentos: Int,
    val histereseRebalanceamentoPp: Double,
) {
    init {
        require(versao == 1) { "versao não suportada (esperado: 1)" }
        for ((nome, v) in listOf(
            "pesoWip" to pesoWip, "penalidadeWipCheio" to penalidadeWipCheio, "pesoCompetenciaFaltante" to pesoCompetenciaFaltante,
            "bonusEpicoOuComponente" to bonusEpicoOuComponente,
        )) {
            require(v >= 0.0 && v <= LIMITE_DE_PESO) { "$nome fora de 0..$LIMITE_DE_PESO" }
        }
        require(produtividadeMin > 0.0 && produtividadeMin <= 1.0) { "produtividadeMin fora de (0, 1]" }
        require(produtividadeMax >= 1.0 && produtividadeMax <= LIMITE_DE_PRODUTIVIDADE) { "produtividadeMax fora de [1, $LIMITE_DE_PRODUTIVIDADE]" }
        require(capacidadeMinimaH >= 0.0 && capacidadeMinimaH <= LIMITE_DE_HORAS) { "capacidadeMinimaH fora de 0..$LIMITE_DE_HORAS" }
        require(gatilhoRebalanceamento > 1.0 && gatilhoRebalanceamento <= LIMITE_DO_GATILHO) {
            "gatilhoRebalanceamento fora de (1, $LIMITE_DO_GATILHO]"
        }
        require(maxMovimentos in 1..LIMITE_DE_MOVIMENTOS) { "maxMovimentos fora de 1..$LIMITE_DE_MOVIMENTOS" }
        require(histereseRebalanceamentoPp >= 0.0 && histereseRebalanceamentoPp <= 100.0) { "histereseRebalanceamentoPp fora de 0..100" }
    }

    /** O texto da versão que sai em toda resposta (`distribuicao v1`). */
    val rotulo: String get() = "distribuicao v$versao"

    companion object {
        private const val LIMITE_DE_PESO = 10.0
        private const val LIMITE_DE_PRODUTIVIDADE = 3.0
        private const val LIMITE_DE_HORAS = 1000.0
        private const val LIMITE_DO_GATILHO = 10.0
        private const val LIMITE_DE_MOVIMENTOS = 100

        /** Valores do modelo `distribuicao.exemplo.yaml`, para os testes que não leem o arquivo. */
        val PADRAO = RegrasDeDistribuicao(
            versao = 1, pesoWip = 0.5, penalidadeWipCheio = 1.0, pesoCompetenciaFaltante = 0.6, bonusEpicoOuComponente = 0.15,
            produtividadeMin = 0.85, produtividadeMax = 1.15, capacidadeMinimaH = 0.5, gatilhoRebalanceamento = 1.10,
            maxMovimentos = 10, histereseRebalanceamentoPp = 20.0,
        )

        /** Estrito: chave desconhecida ou ausente e limite violado falham citando o arquivo (nunca o valor lido). */
        fun fromYaml(path: Path): RegrasDeDistribuicao = lerEstrito(path) {
            val b = Yaml.default.decodeFromString(RegrasDeDistribuicaoYaml.serializer(), Files.readString(path))
            RegrasDeDistribuicao(
                b.versao, b.pesoWip, b.penalidadeWipCheio, b.pesoCompetenciaFaltante, b.bonusEpicoOuComponente, b.produtividadeMin,
                b.produtividadeMax, b.capacidadeMinimaH, b.gatilhoRebalanceamento, b.maxMovimentos, b.histereseRebalanceamentoPp,
            )
        }
    }
}

@Serializable
private data class RegrasDeDistribuicaoYaml(
    val versao: Int,
    val pesoWip: Double,
    val penalidadeWipCheio: Double,
    val pesoCompetenciaFaltante: Double,
    val bonusEpicoOuComponente: Double,
    val produtividadeMin: Double,
    val produtividadeMax: Double,
    val capacidadeMinimaH: Double,
    val gatilhoRebalanceamento: Double,
    val maxMovimentos: Int,
    val histereseRebalanceamentoPp: Double,
)
