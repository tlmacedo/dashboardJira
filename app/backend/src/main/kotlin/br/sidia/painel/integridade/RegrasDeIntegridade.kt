package br.sidia.painel.integridade

import br.sidia.painel.pessoas.lerEstrito
import com.charleskorn.kaml.Yaml
import kotlinx.serialization.Serializable
import java.nio.file.Files
import java.nio.file.Path

/**
 * Regras da Integridade, lidas de `integridade.yaml` (versionado, sem dado pessoal; em produção o arquivo real é obrigatório).
 * São heurísticas do app anterior AINDA SEM calibração com dados reais: por isso ficam em YAML, e a [versao] sai em toda
 * resposta. [gravidades] tem todos os sinais (1 a 3); [ativos] liga ou desliga cada um. Violações são
 * [IllegalArgumentException] que citam a regra e nunca o valor lido.
 */
data class RegrasDeIntegridade(
    val versao: Int,
    val gravidades: Map<SinalIntegridade, Int>,
    val ativos: Set<SinalIntegridade>,
    val trocaAntesDeFecharHoras: Double,
    val criadaEFechadaMinutos: Int,
    val reabreEFechaHoras: Double,
    val loteQuantidade: Int,
    val loteMinutos: Int,
    val fracaoVidaTardia: Double,
    val estimativaAumento: Double,
    val labelsCriticidade: List<String>,
    val labelRemovidaDias: Int,
    val limiteInicioHora: Int,
    val pontosAlto: Int,
    val multiploAlto: Double,
    val gravesAlto: Int,
    val pontosMedio: Int,
    val multiploMedio: Double,
    val concluidasMinimas: Int,
    val pisoMediana: Double,
) {
    init {
        require(versao == 1) { "versao não suportada (esperado: 1)" }
        for (sinal in SinalIntegridade.entries) {
            val g = gravidades[sinal] ?: throw IllegalArgumentException("gravidades: falta a gravidade de $sinal")
            require(g in 1..3) { "gravidades: a gravidade de $sinal fora de 1..3" }
        }
        require(trocaAntesDeFecharHoras > 0.0 && trocaAntesDeFecharHoras <= LIMITE_DE_HORAS) { "trocaAntesDeFecharHoras fora de (0, $LIMITE_DE_HORAS]" }
        require(criadaEFechadaMinutos in 1..LIMITE_DE_MINUTOS) { "criadaEFechadaMinutos fora de 1..$LIMITE_DE_MINUTOS" }
        require(reabreEFechaHoras > 0.0 && reabreEFechaHoras <= LIMITE_DE_HORAS) { "reabreEFechaHoras fora de (0, $LIMITE_DE_HORAS]" }
        require(loteQuantidade in 2..LIMITE_DO_LOTE) { "loteQuantidade fora de 2..$LIMITE_DO_LOTE" }
        require(loteMinutos in 1..LIMITE_DE_MINUTOS) { "loteMinutos fora de 1..$LIMITE_DE_MINUTOS" }
        require(fracaoVidaTardia > 0.0 && fracaoVidaTardia < 1.0) { "fracaoVidaTardia fora de (0, 1)" }
        require(estimativaAumento > 0.0 && estimativaAumento <= LIMITE_DO_AUMENTO) { "estimativaAumento fora de (0, $LIMITE_DO_AUMENTO]" }
        require(labelsCriticidade.none { it.isBlank() || it.length > TAMANHO_MAXIMO_DA_LABEL }) { "labelsCriticidade com item vazio ou longo demais" }
        require(labelRemovidaDias in 0..LIMITE_DE_DIAS) { "labelRemovidaDias fora de 0..$LIMITE_DE_DIAS" }
        require(limiteInicioHora in 0..23) { "limiteInicioHora fora de 0..23" }
        require(pontosAlto in 1..LIMITE_DE_PONTOS) { "pontosAlto fora de 1..$LIMITE_DE_PONTOS" }
        require(multiploAlto >= 1.0 && multiploAlto <= LIMITE_DO_MULTIPLO) { "multiploAlto fora de [1, $LIMITE_DO_MULTIPLO]" }
        require(gravesAlto in 1..LIMITE_DE_PONTOS) { "gravesAlto fora de 1..$LIMITE_DE_PONTOS" }
        require(pontosMedio in 1..pontosAlto) { "pontosMedio fora de 1..pontosAlto" }
        require(multiploMedio >= 1.0 && multiploMedio <= multiploAlto) { "multiploMedio fora de [1, multiploAlto]" }
        require(concluidasMinimas in 1..LIMITE_DE_PONTOS) { "concluidasMinimas fora de 1..$LIMITE_DE_PONTOS" }
        require(pisoMediana > 0.0 && pisoMediana <= 1.0) { "pisoMediana fora de (0, 1]" }
    }

    /** O texto da versão que sai em toda resposta (`integridade v1`). */
    val rotulo: String get() = "integridade v$versao"

    fun ativo(sinal: SinalIntegridade): Boolean = sinal in ativos

    fun gravidade(sinal: SinalIntegridade): Int = gravidades.getValue(sinal)

    companion object {
        private const val LIMITE_DE_HORAS = 720.0
        private const val LIMITE_DE_MINUTOS = 1440
        private const val LIMITE_DO_LOTE = 1000
        private const val LIMITE_DO_AUMENTO = 10.0
        private const val TAMANHO_MAXIMO_DA_LABEL = 100
        private const val LIMITE_DE_DIAS = 365
        private const val LIMITE_DE_PONTOS = 10_000
        private const val LIMITE_DO_MULTIPLO = 1000.0

        /** Valores do modelo `integridade.exemplo.yaml`, para os testes que não leem o arquivo. */
        val PADRAO = RegrasDeIntegridade(
            versao = 1,
            gravidades = SinalIntegridade.entries.associateWith { sinal ->
                when (sinal.ordinal) {
                    in 0..4 -> 3
                    in 5..9 -> 2
                    else -> 1
                }
            },
            ativos = SinalIntegridade.entries.toSet() - SinalIntegridade.ESTIMATIVA_AUMENTADA,
            trocaAntesDeFecharHoras = 2.0, criadaEFechadaMinutos = 15, reabreEFechaHoras = 2.0, loteQuantidade = 6, loteMinutos = 10,
            fracaoVidaTardia = 0.7, estimativaAumento = 0.3, labelsCriticidade = listOf("CRITICAL", "BLOCKER"), labelRemovidaDias = 2,
            limiteInicioHora = 22, pontosAlto = 12, multiploAlto = 3.0, gravesAlto = 3, pontosMedio = 6, multiploMedio = 2.0,
            concluidasMinimas = 8, pisoMediana = 0.05,
        )

        /** Estrito: chave ou sinal desconhecido, sinal sem gravidade e limite violado falham citando o arquivo (nunca o valor lido). */
        fun fromYaml(path: Path): RegrasDeIntegridade = lerEstrito(path) {
            val b = Yaml.default.decodeFromString(RegrasDeIntegridadeYaml.serializer(), Files.readString(path))
            fun sinal(nome: String): SinalIntegridade =
                SinalIntegridade.entries.firstOrNull { it.name == nome } ?: throw IllegalArgumentException("sinal desconhecido")
            RegrasDeIntegridade(
                versao = b.versao, gravidades = b.gravidades.entries.associate { (nome, g) -> sinal(nome) to g },
                ativos = b.ativos.map(::sinal).toSet(), trocaAntesDeFecharHoras = b.trocaAntesDeFecharHoras,
                criadaEFechadaMinutos = b.criadaEFechadaMinutos, reabreEFechaHoras = b.reabreEFechaHoras, loteQuantidade = b.loteQuantidade,
                loteMinutos = b.loteMinutos, fracaoVidaTardia = b.fracaoVidaTardia, estimativaAumento = b.estimativaAumento,
                labelsCriticidade = b.labelsCriticidade, labelRemovidaDias = b.labelRemovidaDias, limiteInicioHora = b.limiteInicioHora,
                pontosAlto = b.pontosAlto, multiploAlto = b.multiploAlto, gravesAlto = b.gravesAlto, pontosMedio = b.pontosMedio,
                multiploMedio = b.multiploMedio, concluidasMinimas = b.concluidasMinimas, pisoMediana = b.pisoMediana,
            )
        }
    }
}

@Serializable
private data class RegrasDeIntegridadeYaml(
    val versao: Int,
    val gravidades: Map<String, Int>,
    val ativos: List<String>,
    val trocaAntesDeFecharHoras: Double,
    val criadaEFechadaMinutos: Int,
    val reabreEFechaHoras: Double,
    val loteQuantidade: Int,
    val loteMinutos: Int,
    val fracaoVidaTardia: Double,
    val estimativaAumento: Double,
    val labelsCriticidade: List<String>,
    val labelRemovidaDias: Int,
    val limiteInicioHora: Int,
    val pontosAlto: Int,
    val multiploAlto: Double,
    val gravesAlto: Int,
    val pontosMedio: Int,
    val multiploMedio: Double,
    val concluidasMinimas: Int,
    val pisoMediana: Double,
)
